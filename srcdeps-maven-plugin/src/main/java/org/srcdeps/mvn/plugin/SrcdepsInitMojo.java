/**
 * Copyright 2015-2017 Maven Source Dependencies
 * Plugin contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.srcdeps.mvn.plugin;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Scm;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.config.yaml.writer.YamlWriterConfiguration;
import org.srcdeps.config.yaml.writer.YamlWriterVisitor;
import org.srcdeps.core.Gav;
import org.srcdeps.core.GavSet;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ScmRepository;
import org.srcdeps.core.config.ScmRepository.Builder;
import org.srcdeps.core.config.tree.walk.DefaultsAndInheritanceVisitor;
import org.srcdeps.core.config.tree.walk.OverrideVisitor;

/**
 * First calls {@link SrcdepsUpgradeMojo} and then generates the {@code srcdeps.yaml} file. Any existing
 * {@code srcdeps.yaml} file is overwritten without warning.
 * <p>
 * The main responsibility of {@link SrcdepsInitMojo} is to produce a {@code srcdeps.yaml} file that is as complete as
 * possible. To accomplish this, the mojo crawls through the dependencies of the current project tree and collects the
 * following info:
 * <ul>
 * <li>SCM URLs (via {@code <scm>} tags in {@code pom.xml} files)
 * <li>Associations between GAVs and SCM URLs - i.e. which GAV should be built from which SCM URL
 * </ul>
 * That is basically enough on the level of raw data, but to produce a nice {@code srcdeps.yaml} file, we need a bit
 * more: It is often not optimal to list per-artifactId selectors [1] like
 *
 * <pre>
 * selectors:
 * - org.mygroup:my-artifact-1
 * - org.mygroup:my-artifact-2
 * - org.mygroup:my-artifact-3
 * - org.mygroup:my-artifact-4
 * </pre>
 *
 * That would be correct, but not nice and reliable because org.mygroup project may decide to add my-artifact-5 at some
 * point in the future. If all artifacts for the given URL, have the same groupId, then we could theoretically
 * generalize the selectors to just
 *
 * <pre>
 * selectors:
 * - org.mygroup
 * </pre>
 *
 * But to do that, we need to make sure that the same group does not occur under other URLs. If it does, we fall back to
 * per-artifactId selectors.
 * <p>
 * We also need a unique id for each SCM repository element in srcdeps.yaml file. Similarly as with selectors, we prefer
 * short groupId based IDs, as long as we can prove them to be unique over all SCM repositories.
 * <p>
 * To handle this two kinds of problems, we use a couple of tracking maps in {@link ScmRepositoryIndex}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@SuppressWarnings("deprecation")
@Mojo(name = "init", defaultPhase = LifecyclePhase.NONE, threadSafe = false, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST)
public class SrcdepsInitMojo extends SrcdepsUpgradeMojo {

    /**
     * A container for several collections that will allow us to build a list of {@link ScmRepository}s at the end.
     */
    static class ScmRepositoryIndex {

        /**
         * This helps to climb up the parent hierarchy of a GAV to determine its SCM URL. Note that for the given GAV we
         * not only need the SCM URL itself, we need also the GAV that URL is defined on, so that we can indentify some
         * naming clashes later in {@link ScmRepositoryIndex#createRepository(String)}.
         */
        static class ScmUrlAncestry {
            private static final String GIT_SCM_PREFIX = "git:";

            private static final String SCM_PREFIX = "scm:";

            static class Builder {
                private List<Element> path = new ArrayList<>();

                public ScmUrlAncestry build() {
                    List<Element> useElements = Collections.unmodifiableList(path);
                    this.path = null;
                    return new ScmUrlAncestry(useElements);
                }

                public Builder element(String url, Ga ga) {
                    this.path.add(new Element(url, ga));
                    return this;
                }
            }

            /**
             * The element in the parent hierarchy of a GAV.
             */
            private static class Element {
                private final Ga ga;
                /** The SCM URL */
                private final String url;

                private Element(String url, Ga ga) {
                    super();
                    this.url = url;
                    this.ga = ga;
                }
            }

            /**
             * Appends the given {@code project} to the given {@code ancestryPath} and returns the same instance of
             * {@code ancestryPath}.
             *
             * @param project
             *            the project to append
             * @param ancestryPath
             *            the path to append to
             * @return the given {@code ancestryPath}
             */
            private static List<Element> append(MavenProject project, List<Element> ancestryPath) {
                Scm scm = project.getScm();
                if (scm == null) {
                    log.debug("No SCM in project [{}:{}:{}]", project.getGroupId(), project.getArtifactId(),
                            project.getVersion());
                    return ancestryPath;
                } else {
                    String url = scm.getConnection();
                    if (url == null) {
                        url = scm.getDeveloperConnection();
                        if (url == null) {
                            log.debug("No SCM connection in project [{}:{}:{}]", project.getGroupId(),
                                    project.getArtifactId(), project.getVersion());
                            return ancestryPath;
                        } else {
                            log.debug("No SCM connection in project [{}:{}:{}] - falling back to developerConnection",
                                    project.getGroupId(), project.getArtifactId(), project.getVersion());
                        }
                    }
                    /*
                     * url != null look if can climb up to the parent and shothen the URL
                     */
                    ancestryPath.add(new Element(url, new Ga(project.getGroupId(), project.getArtifactId())));
                    MavenProject parent = project.getParent();
                    if (parent != null) {
                        return append(parent, ancestryPath);
                    } else {
                        /* parent == null */
                        return ancestryPath;
                    }
                }
            }

            public static Builder builder() {
                return new Builder();
            }

            public static ScmUrlAncestry of(MavenProject project) {
                return new ScmUrlAncestry(append(project, new ArrayList<Element>()));
            }

            /** The list of ancestors */
            private final List<Element> elements;

            private final int length;

            ScmUrlAncestry(List<Element> elements) {
                super();
                this.elements = elements;
                this.length = guessDepth();
            }

            /**
             * Returns GAs from {@link #elements} in the inverted order - the "oldest" ancestor being at position
             * {@code 0} and the joungest being at the terminal position.
             *
             * @param i
             *            the index
             * @return the GA at index {@code i}
             */
            public Ga getGaAt(int i) {
                if (length == Integer.MAX_VALUE) {
                    throw new IllegalStateException(
                            String.format("No root GA found in %s : [%s]", ScmUrlAncestry.class.getName(), elements));
                }
                if (i < 0 || i >= length) {
                    throw new IndexOutOfBoundsException(String.format("Expected 0..%d, found %d", length, i));
                }
                return elements.get(length - i - 1).ga;
            }

            /**
             * @return the length of the path from the first element to the element that defines its SCM URL. Note that
             *         {@link #length} can be shorter than {@code elements.length()}
             */
            public int getLength() {
                return length;
            }

            /**
             * @return the Ga that defines the SCM URL returned by {@link #getUrl()}. A shorthand for {@code getGaAt(0)}
             */
            public Ga getRootGa() {
                return getGaAt(0);
            }

            /**
             * @return the SCM URL that is valid for path elements from index {@code 0} to index {@code length - 1}.
             */
            public String getUrl() {
                if (length == Integer.MAX_VALUE) {
                    return null;
                } else {
                    final Element terminal = elements.get(length - 1);
                    String result = terminal.url;
                    if (result.startsWith(SCM_PREFIX)) {
                        result = result.substring(SCM_PREFIX.length());
                    }
                    String suffix = "/" + terminal.ga.getArtifactId();
                    if (result.endsWith(suffix)) {
                        result = result.substring(0, result.length() - suffix.length());
                    }

                    if (!result.startsWith(GIT_SCM_PREFIX) && result.indexOf("github.com") >= 0) {
                        /* fix a malformed github URL */
                        log.warn("Fixing the SCM URL [{}] that is apparently missing the git: prefix", result);
                        result = GIT_SCM_PREFIX + result;
                    }
                    return result;
                }
            }

            private int guessDepth() {
                for (int i = 0; i < elements.size(); i++) {
                    Element e = elements.get(i);
                    String suffix = "/" + e.ga.getArtifactId();
                    String url = e.url;
                    if (suffix.length() >= url.length()) {
                        return i + 1;
                    } else {
                        String expectedParentUrl = url.substring(0, url.length() - suffix.length());
                        if (url.endsWith(suffix)
                                && (i + 1 < elements.size() && elements.get(i + 1).url.equals(expectedParentUrl))) {
                            continue;
                        } else {
                            return i + 1;
                        }
                    }
                }
                return Integer.MAX_VALUE;
            }

            /**
             * @return {@code true} if a valid URL could be found for this {@link ScmUrlAncestry} and {@code false}
             *         otherwise
             */
            public boolean hasUrl() {
                return this.length != Integer.MAX_VALUE;
            }

        }

        /** Tracks under which URLs (values) a given groupId (key) occurs */
        private final Map<String, Set<String>> groupIdUrlMap = new HashMap<>();
        private final ProjectBuilder projectBuilder;
        private final List<RemoteRepository> remoteRepos;
        private final RepositorySystemSession repoSession;
        private final ArtifactFactory repositorySystem;
        private final Set<org.srcdeps.core.Scm> scms;
        /** We do not want to process the same {@link Gav} twice */
        private final Set<Gav> seenGavs = new HashSet<>();
        private final MavenSession session;
        /**
         * A map from SCM URLs to their respective maps from GAV to the number of path segments removed from the URL to
         * reach the resulting URL
         */
        private final Map<String, Map<String, Set<String>>> urlGaMap = new HashMap<>();

        /** Tracks root GAs (value) found for the given URL (key). */
        private final Map<String, Set<Ga>> urlRootGasMap = new HashMap<>();

        /** Tracks URLs that we already reported once as unsupported, so that we do not warn twice about the same URL */
        private final Set<String> unsupportedUrls = new HashSet<>();

        private ScmRepositoryIndex(MavenSession session, RepositorySystemSession repoSession,
                ArtifactFactory repositorySystem, ProjectBuilder projectBuilder, Set<org.srcdeps.core.Scm> scms) {
            super();
            this.session = session;
            this.repoSession = repoSession;
            this.repositorySystem = repositorySystem;
            this.projectBuilder = projectBuilder;
            this.remoteRepos = RepositoryUtils.toRepos(session.getProjectBuildingRequest().getRemoteRepositories());
            this.scms = scms;
        }

        /**
         * Associate the given {@code url} with the given {@code ga}
         *
         * @param url
         *            a SCM URL cleaned from any {@code artifactId} suffixes
         * @param ga
         *            the {@link Ga} to associate with the given {@code url}
         */
        private void add(String url, Ga ga) {
            final String groupId = ga.getGroupId();
            final String artifactId = ga.getArtifactId();
            Map<String, Set<String>> gaMap = urlGaMap.get(url);
            if (gaMap == null) {
                gaMap = new HashMap<>();
                urlGaMap.put(url, gaMap);
            }
            Set<String> artifactIds = gaMap.get(groupId);
            if (artifactIds == null) {
                artifactIds = new HashSet<>();
                gaMap.put(groupId, artifactIds);
            }
            artifactIds.add(artifactId);

            Set<String> urls = groupIdUrlMap.get(groupId);
            if (urls == null) {
                urls = new TreeSet<>();
                groupIdUrlMap.put(groupId, urls);
            }
            urls.add(url);
        }

        /**
         * Find the SCM URL for the given {@code g, a, v} triple and store the association for the later retrieval via
         * {@link #createSortedScmRepositoryMap()}.
         *
         * @param g
         *            {@code groupId}
         * @param a
         *            {@code artifactId}
         * @param v
         *            {@code version}
         * @param failOnUnresolvable
         *            see {@link SrcdepsInitMojo#failOnUnresolvable}
         * @throws MojoExecutionException
         */
        public void addGav(String g, String a, String v, boolean failOnUnresolvable) throws MojoExecutionException {
            final Gav gav = new Gav(g, a, v);
            if (!seenGavs.contains(gav)) {
                seenGavs.add(gav);
                final Ga ga = new Ga(g, a);
                log.debug("Adding GA: {}", ga);

                ProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
                projectBuildingRequest.setLocalRepository(session.getLocalRepository());
                projectBuildingRequest
                        .setRemoteRepositories(session.getProjectBuildingRequest().getRemoteRepositories());
                projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
                projectBuildingRequest.setProcessPlugins(false);
                projectBuildingRequest.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
                projectBuildingRequest.setSystemProperties(session.getSystemProperties());
                projectBuildingRequest.setRepositorySession(repoSession);

                Artifact pomArtifact = repositorySystem.createProjectArtifact(g, a, v, "compile");
                try {
                    ProjectBuildingResult result = projectBuilder.build(pomArtifact, projectBuildingRequest);
                    MavenProject dependencyProject = result.getProject();
                    ScmUrlAncestry ancestry = ScmUrlAncestry.of(dependencyProject);
                    if (!ancestry.hasUrl()) {
                        log.warn("No SCM connection for artifact [{}]", ga);
                    } else {
                        final String url = ancestry.getUrl();
                        if (unsupportedUrls.contains(url)) {
                            /* was reported once already */
                        } else if (isScmUrlSupported(url)) {
                            log.debug("Found SCM URL [{}] for GA [{}]", url, ga);
                            int len = ancestry.getLength();
                            for (int i = 0; i < len; i++) {
                                this.add(url, ancestry.getGaAt(i));
                            }
                            Set<Ga> rootGas = urlRootGasMap.get(url);
                            if (rootGas == null) {
                                rootGas = new TreeSet<>();
                                urlRootGasMap.put(url, rootGas);
                            }
                            rootGas.add(ancestry.getRootGa());
                        } else {
                            log.warn("Unsupported SCM URL [{}] for GAV [{}]", url, ga);
                            unsupportedUrls.add(url);
                        }
                    }
                } catch (ProjectBuildingException e) {
                    final String msg = String.format("Could not resolve [%s] using remote repositories [%s]",
                            pomArtifact, remoteRepos);
                    if (failOnUnresolvable) {
                        throw new MojoExecutionException(msg, e);
                    } else {
                        log.warn(msg);
                    }
                }
            }
        }

        /**
         * Creates a new {@link ScmRepository.Builder} and sets its selectors, SCM URL and ID based on the information
         * available in this {@link ScmRepositoryIndex}.
         *
         * @param url
         *            the SCM URL to create a new {@link ScmRepository.Builder} for
         * @return a new {@link ScmRepository.Builder}
         */
        public ScmRepository.Builder createRepository(String url) {

            log.debug(" == Creating SCM repository for URL [{}]", url);

            ScmRepository.Builder repoBuilder = ScmRepository.builder();

            /* (1) Set the ID of the SCM repo */

            final Set<Ga> rootGas = urlRootGasMap.get(url);
            /*
             * Having more than one root GAs should not be very common. It may occur e.g. when the groupId or artifactId
             * is changed over time and the nesting project somehow depends on both GAs before and after the renaming.
             * We are not going to think out anything smart for this case. We will just get the the first root GA and
             * handle it as if it was the only one.
             */
            final Ga rootGa = rootGas.iterator().next();
            final String rootGroupId = rootGa.getGroupId();
            final Set<String> rootUrls = groupIdUrlMap.get(rootGroupId);
            if (rootUrls.size() == 1) {
                /*
                 * good luck: this rootGa's group ID does not occur under any other URL, hence the groupId is unique
                 * enough to serve as the SCM repo ID
                 */
                repoBuilder.id(rootGroupId);
            } else {
                /*
                 * this rootGa's group ID occurs under some other URL Let's check if at least the root groupId -
                 * artifactId combination does not occur under another URL
                 */
                final String rootArtifactId = rootGa.getArtifactId();
                boolean rootGaUnique = true;
                for (String otherUrl : rootUrls) {
                    if (!otherUrl.equals(url)) {
                        Set<String> otherRootArtifactIds = urlGaMap.get(otherUrl).get(rootGroupId);
                        if (otherRootArtifactIds != null && otherRootArtifactIds.contains(rootArtifactId)) {
                            rootGaUnique = false;
                        }
                    }
                }

                if (rootGaUnique) {
                    /*
                     * the root groupId - artifactId combination does not occur under another URL We can safely use the
                     * g-a combo as an ID
                     */
                    repoBuilder.id(rootGroupId + "." + rootArtifactId.replace('.', '-'));
                } else {
                    /*
                     * the root groupId - artifactId combo not unique over URLs We have to make it unique by appending
                     * the URL hash code
                     */
                    repoBuilder.id(
                            rootGroupId + "." + rootArtifactId.replace('.', '-') + ".id" + Math.abs(url.hashCode()));
                }

            }

            /* (2) add the selectors to the SCM repo */
            Set<String> selectors = new TreeSet<>();
            Map<String, Set<String>> gaMap = urlGaMap.get(url);

            for (Entry<String, Set<String>> gaEntry : gaMap.entrySet()) {
                final String groupId = gaEntry.getKey();
                Set<String> urls = groupIdUrlMap.get(groupId);
                if (urls.size() == 1) {
                    /*
                     * good luck: this group ID does not occur under any other URL, hence the groupId is unique enough
                     * to serve as a generalized selector
                     */
                    selectors.add(groupId);
                } else {
                    /*
                     * this group ID occurs under some other URL. Therefore, we have to add per-artifactId selectors
                     */
                    final Set<String> artifactIds = gaEntry.getValue();
                    for (String artifactId : artifactIds) {
                        selectors.add(groupId + ":" + artifactId);
                    }
                }
            }

            for (String selector : selectors) {
                repoBuilder.selector(selector);
            }

            return repoBuilder.url(url);
        }

        /**
         * @return new sorted map of {@link ScmRepository}s by their names
         */
        public Map<String, ScmRepository.Builder> createSortedScmRepositoryMap() {
            Map<String, ScmRepository.Builder> repos = new TreeMap<>();
            for (String url : this.urlGaMap.keySet()) {
                final ScmRepository.Builder newBuilder = createRepository(url);
                final String id = newBuilder.getName();
                final Builder oldBuilder = repos.get(id);
                if (oldBuilder != null) {
                    log.warn(
                            "SCM repository ID not unique, will force the uniqueness of the ID: [{}], old URLs: [{}] old selectors; new URLs: [{}], new selectors: [{}]",
                            id, //
                            oldBuilder.getChildren().get("urls"), oldBuilder.getChildren().get("selectors"),
                            newBuilder.getChildren().get("urls"), newBuilder.getChildren().get("selectors"));
                    newBuilder.id(id + ".id" + Math.abs(url.hashCode()));
                }
                repos.put(newBuilder.getName(), newBuilder);
            }
            return repos;
        }

        /**
         * Ignore the given GAV when it is submitted via {@link #addGav(String, String, String, boolean)}
         *
         * @param groupId
         * @param artifactId
         * @param version
         */
        public void ignoreGav(String groupId, String artifactId, String version) {
            final Gav gav = new Gav(groupId, artifactId, version);
            seenGavs.add(gav);
        }

        /**
         * @param url
         *            the URL to decide about
         * @return {@code true} if the present version of srcdeps is able to handle the source management system given
         *         by the {@code url} parameter
         */
        private boolean isScmUrlSupported(String url) {
            for (org.srcdeps.core.Scm scm : scms) {
                if (scm.supports(url)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SrcdepsInitMojo.class);

    /**
     * Exclude the matching GAVs from processing when generating the {@code srcdeps.yaml} file. The default list of
     * excludes is empty.
     */
    @Parameter(property = "srcdeps.excludes")
    private String[] excludes;

    /**
     * If {@code true} all artifacts having versions ending with {@code -SNAPSHOT} will be ignored. Otherwise, the
     * {@code -SNAPSHOT} artifacts will be included and it will be attempted to find SCM repositories for them.
     */
    @Parameter(defaultValue = "true", property = "srcdeps.excludeSnapshots")
    private boolean excludeSnapshots;

    /**
     * If {@code true} the execution with fail with error in case an artifact is found that cannot be downloaded from
     * any remote repository. If {@code false}, just a warning is produced.
     */
    @Parameter(defaultValue = "true", property = "srcdeps.failOnUnresolvable")
    private boolean failOnUnresolvable;

    /** The set defined by {@link #includes} and {@link #excludes} */
    private GavSet gavSet;

    /**
     * Include the matching GAVs in the processing when generating the {@code srcdeps.yaml} file. The default list of
     * includes constains just the match all pattern {@code *:*:*}.
     */
    @Parameter(property = "srcdeps.includes")
    private String[] includes;

    @Component
    private ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    @Component
    private ArtifactFactory repositorySystem;

    @Component
    private RepositorySystem repoSystem;

    private final Set<org.srcdeps.core.Scm> scms;

    @Inject
    public SrcdepsInitMojo(Set<org.srcdeps.core.Scm> scms) {
        super();
        this.scms = scms;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        super.execute();

        org.srcdeps.core.GavSet.Builder gavSetBuilder = GavSet.builder() //
                .includes(includes) //
                .excludes(excludes);
        if (excludeSnapshots) {
            gavSetBuilder.excludeSnapshots();
        }
        this.gavSet = gavSetBuilder.build();

        log.info("Using includes and excludes [{}]", gavSet);

        log.info("Supported SCMs: {}", scms);

        if (skip || !multiModuleRootDir.equals(session.getCurrentProject().getBasedir())) {
            log.info(getClass().getSimpleName() + " skipped");
        } else {

            Configuration.Builder config = Configuration.builder() //
                    .configModelVersion(Configuration.getLatestConfigModelVersion()).commentBefore("") //
                    .commentBefore("srcdeps.yaml - the srcdeps configuration file") //
                    .commentBefore("") //
                    .commentBefore(
                            "The full srcdeps.yaml reference can be found under https://github.com/srcdeps/srcdeps-core/tree/master/doc/srcdeps.yaml") //
                    .commentBefore("") //
                    .commentBefore("This file was generated by the following command:") //
                    .commentBefore("") //
                    .commentBefore("    mvn org.srcdeps.mvn:srcdeps-maven-plugin:init") //
                    .commentBefore("") //
            ;

            ScmRepositoryIndex index = new ScmRepositoryIndex(session, repoSession, repositorySystem, projectBuilder,
                    scms);
            log.debug("Going over [{}] reactor projects", reactorProjects.size());
            /* first add the reactor projects to seenGas so that they get ignored */
            for (MavenProject project : reactorProjects) {
                index.ignoreGav(project.getGroupId(), project.getArtifactId(), project.getVersion());
            }

            for (MavenProject project : reactorProjects) {

                final List<Dependency> dependencies = project.getDependencies();

                log.info("Project [{}] has [{}] dependencies", project.getArtifactId(),
                        dependencies == null ? 0 : dependencies.size());

                if (dependencies != null) {
                    for (Dependency dependency : dependencies) {

                        final String g = dependency.getGroupId();
                        final String a = dependency.getArtifactId();
                        final String v = dependency.getVersion();
                        if (!"system".equals(dependency.getScope()) && gavSet.contains(g, a, v)) {
                            /* Ignore system scope */
                            index.addGav(g, a, v, failOnUnresolvable);
                        }
                    }
                }

                final DependencyManagement dependencyManagement = project.getDependencyManagement();
                if (dependencyManagement != null) {
                    final List<Dependency> managedDeps = dependencyManagement.getDependencies();
                    if (managedDeps != null) {
                        for (Dependency dependency : managedDeps) {
                            final String g = dependency.getGroupId();
                            final String a = dependency.getArtifactId();
                            final String v = dependency.getVersion();
                            if (!"system".equals(dependency.getScope()) && gavSet.contains(g, a, v)) {
                                /* Ignore system scope */
                                index.addGav(g, a, v, false);
                            }
                        }
                    }
                }

                MavenProject parent = project.getParent();
                if (parent != null) {
                    final String g = parent.getGroupId();
                    final String a = parent.getArtifactId();
                    final String v = parent.getVersion();
                    if (gavSet.contains(g, a, v)) {
                        index.addGav(g, a, v, failOnUnresolvable);
                    }
                }
            }

            Map<String, Builder> repos = index.createSortedScmRepositoryMap();
            if (repos.size() == 0) {
                /* add some dummy repo so that we do not write an empty srcdeps.yaml file */
                ScmRepository.Builder dummyRepo = ScmRepository.builder() //
                        .commentBefore(
                                "FIXME: srcdeps-maven-plugin could not authomatically identify any SCM URLs for dependencies in this project") //
                        .commentBefore(
                                "       and has added this dummy repository only as a starting point for you to proceed manually") //
                        .id("org.my-group") //
                        .selector("org.my-group") //
                        .url("git:https://github.com/my-org/my-project.git") //
                ;
                repos.put(dummyRepo.getName(), dummyRepo);
            }

            config //
                    .repositories(repos) //
                    .accept(new OverrideVisitor(System.getProperties())) //
                    .accept(new DefaultsAndInheritanceVisitor()) //
            ;

            final Path srcdepsYamlPath = multiModuleRootDir.toPath().resolve("srcdeps.yaml");
            try {
                YamlWriterConfiguration yamlWriterConfiguration = YamlWriterConfiguration.builder().build();
                try (Writer out = Files.newBufferedWriter(srcdepsYamlPath, Charset.forName(encoding))) {
                    config.accept(new YamlWriterVisitor(out, yamlWriterConfiguration));
                }
            } catch (IOException e) {
                throw new MojoExecutionException(String.format("Could not write [%s]", srcdepsYamlPath), e);
            }
        }

    }

}
