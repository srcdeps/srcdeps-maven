/**
 * Copyright 2015-2016 Maven Source Dependencies
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
package org.srcdeps.mvn.localrepo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.config.yaml.YamlConfigurationIo;
import org.srcdeps.core.BuildException;
import org.srcdeps.core.BuildRequest;
import org.srcdeps.core.BuildService;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.config.BuilderIo;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ConfigurationException;
import org.srcdeps.core.config.ScmRepository;
import org.srcdeps.core.fs.BuildDirectoriesManager;
import org.srcdeps.core.fs.PathLock;
import org.srcdeps.core.fs.PathLocker;
import org.srcdeps.core.shell.IoRedirects;

/**
 * A {@link LocalRepositoryManager} able to build the requested artifacts from their sources.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class SrcdepsLocalRepositoryManager implements LocalRepositoryManager {
    private static final Logger log = LoggerFactory.getLogger(SrcdepsLocalRepositoryManager.class);

    /** See the bin/mvn or bin/mvn.cmd script of your maven distro, where maven.multiModuleProjectDirectory is set */
    private static final String MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY = "maven.multiModuleProjectDirectory";

    public static final Path relativeMvnSrcdepsYaml = Paths.get(".mvn", "srcdeps.yaml");

    /** A system property for setting an encoding other than the default {@code utf-8} for reading the
     * {@code .mvn/srcdeps.yaml} file. */
    private static final String SRCDEPS_ENCODING_PROPERTY = "srcdeps.encoding";

    private static List<String> enhanceBuildArguments(List<String> buildArguments, Path configurationLocation,
            String localRepo) {
        List<String> result = new ArrayList<>();
        for (String arg : buildArguments) {
            if (arg.startsWith("-Dmaven.repo.local=")) {
                /* We won't touch maven.repo.local set in the user's config */
                log.debug("Srcdeps forwards {} to the nested build as set in {}", arg, configurationLocation);
                return buildArguments;
            }
            result.add(arg);
        }

        String arg = "-Dmaven.repo.local=" + localRepo;
        log.debug("Srcdeps forwards {} from the outer Maven build to the nested build", arg);
        result.add(arg);

        return Collections.unmodifiableList(result);
    }

    private final BuildDirectoriesManager buildDirectoriesManager;

    private final BuildService buildService;

    private final Configuration configuration;
    private final Path configurationLocation;

    private final LocalRepositoryManager delegate;

    private final Path scrdepsDir;

    public SrcdepsLocalRepositoryManager(LocalRepositoryManager delegate, BuildService buildService, PathLocker<SrcVersion> pathLocker) {
        super();
        this.delegate = delegate;
        this.buildService = buildService;
        this.scrdepsDir = delegate.getRepository().getBasedir().toPath().getParent().resolve("srcdeps");
        this.buildDirectoriesManager = new BuildDirectoriesManager(scrdepsDir, pathLocker);

        String basePathString = System.getProperty(MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY);
        if (basePathString == null || basePathString.isEmpty()) {
            throw new RuntimeException(String.format("The system property %s must not be null or empty",
                    MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY));
        }
        final Path basePath = Paths.get(basePathString).toAbsolutePath();
        final Path srcdepsYamlPath = basePath.resolve(relativeMvnSrcdepsYaml);
        if (Files.exists(srcdepsYamlPath)) {
            this.configurationLocation = srcdepsYamlPath;
            log.debug("SrcdepsLocalRepositoryManager using configuration {}", configurationLocation);
        } else {
            throw new RuntimeException(
                    String.format("Could not locate srcdeps configuration at [%s]", srcdepsYamlPath));
        }

        final String encoding = System.getProperty(SRCDEPS_ENCODING_PROPERTY, "utf-8");
        final Charset cs = Charset.forName(encoding);
        try (Reader r = Files.newBufferedReader(configurationLocation, cs)) {
            this.configuration = new YamlConfigurationIo().read(r);
        } catch (IOException | ConfigurationException e) {
            throw new RuntimeException(e);
        }

    }
    /**
     * Delegated to {@link #delegate}
     *
     * @see org.eclipse.aether.repository.LocalRepositoryManager#add(org.eclipse.aether.RepositorySystemSession,
     *      org.eclipse.aether.repository.LocalArtifactRegistration)
     */
    @Override
    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        delegate.add(session, request);
    }
    /**
     * Delegated to {@link #delegate}
     *
     * @see org.eclipse.aether.repository.LocalRepositoryManager#add(org.eclipse.aether.RepositorySystemSession,
     *      org.eclipse.aether.repository.LocalMetadataRegistration)
     */
    @Override
    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        delegate.add(session, request);
    }

    /**
     * In case the {@link #delegate} does not find the given artifact and the given artifact's version string is a
     * srcdeps version string, then the version is built from source and returned.
     *
     * @see org.eclipse.aether.repository.LocalRepositoryManager#find(org.eclipse.aether.RepositorySystemSession,
     *      org.eclipse.aether.repository.LocalArtifactRequest)
     */
    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        log.debug("Srcdeps looking up locally {}", request.getArtifact());
        final LocalArtifactResult result = delegate.find(session, request);

        Artifact artifact = request.getArtifact();
        String version = artifact.getVersion();
        if (!result.isAvailable() && SrcVersion.isSrcVersion(version)) {

            if (!configuration.isSkip()) {
                ScmRepository scmRepo = findScmRepo(artifact);
                SrcVersion srcVersion = SrcVersion.parse(version);
                try (PathLock projectBuildDir = buildDirectoriesManager.openBuildDirectory(scmRepo.getIdAsPath(),
                        srcVersion)) {

                    /* query the delegate again, because things may have changed since we requested the lock */
                    final LocalArtifactResult result2 = delegate.find(session, request);
                    if (result2.isAvailable()) {
                        return result2;
                    } else {
                        /* no change in the local repo, let's build */
                        BuilderIo builderIo = configuration.getBuilderIo();
                        IoRedirects ioRedirects = IoRedirects.builder() //
                                .stdin(IoRedirects.parseUri(builderIo.getStdin())) //
                                .stdout(IoRedirects.parseUri(builderIo.getStdout())) //
                                .stderr(IoRedirects.parseUri(builderIo.getStderr())) //
                                .build();

                        List<String> buildArgs = enhanceBuildArguments(scmRepo.getBuildArguments(),
                                configurationLocation,
                                delegate.getRepository().getBasedir().getAbsolutePath());

                        BuildRequest buildRequest = BuildRequest.builder() //
                                .projectRootDirectory(projectBuildDir.getPath()) //
                                .scmUrls(scmRepo.getUrls()) //
                                .srcVersion(srcVersion) //
                                .buildArguments(buildArgs) //
                                .skipTests(scmRepo.isSkipTests()) //
                                .forwardProperties(configuration.getForwardProperties()) //
                                .addDefaultBuildArguments(scmRepo.isAddDefaultBuildArguments()) //
                                .verbosity(configuration.getVerbosity()) //
                                .ioRedirects(ioRedirects) //
                                .build();
                        buildService.build(buildRequest);

                        /* check once again if the delegate sees the newly built artifact */
                        final LocalArtifactResult newResult = delegate.find(session, request);
                        if (!newResult.isAvailable()) {
                            log.error(
                                    "Srcdeps build succeeded but the artifact {} is still not available in the local repository",
                                    artifact);
                        }
                        return newResult;
                    }

                } catch (BuildException | IOException e) {
                    log.error("Srcdeps could not build " + request, e);
                }

            }

        }

        return result;
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        return delegate.find(session, request);
    }

    /**
     * Finds the first {@link ScmRepository} associated with the given {@code artifact}. The association is given by
     * the exact string match between the groupId of the {@code artifact} and one of the
     * {@link ScmRepository#getSelectors() selectors} of {@link ScmRepository}
     *
     * @param artifact
     * @return
     */
    public ScmRepository findScmRepo(Artifact artifact) {
        final String groupId = artifact.getGroupId();
        for (ScmRepository scmRepository : configuration.getRepositories()) {
            if (scmRepository.getSelectors().contains(groupId)) {
                return scmRepository;
            }
        }
        throw new IllegalStateException(String
                .format("No srcdeps SCM repository configured in .mvn/srcdeps.yaml for groupId [%s]", groupId));
    }

    /**
     * @return the {@link Configuration} loaded from {@link #configurationLocation}
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Returns {@code ".mvn/srcdeps.yaml"} resolved against the top level project the current Maven request.
     *
     * @return
     */
    public Path getConfigurationLocation() {
        return configurationLocation;
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        return delegate.getPathForLocalArtifact(artifact);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        return delegate.getPathForLocalMetadata(metadata);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        return delegate.getPathForRemoteArtifact(artifact, repository, context);
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        return delegate.getPathForRemoteMetadata(metadata, repository, context);
    }

    @Override
    public LocalRepository getRepository() {
        return delegate.getRepository();
    }

}
