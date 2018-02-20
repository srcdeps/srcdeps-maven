/**
 * Copyright 2015-2018 Maven Source Dependencies
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
import java.nio.file.Path;
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
import org.srcdeps.core.BuildException;
import org.srcdeps.core.BuildRefStore;
import org.srcdeps.core.BuildRequest;
import org.srcdeps.core.BuildRequestId;
import org.srcdeps.core.BuildService;
import org.srcdeps.core.FetchId;
import org.srcdeps.core.FetchLog;
import org.srcdeps.core.ScmService;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.config.BuilderIo;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ScmRepository;
import org.srcdeps.core.fs.BuildDirectoriesManager;
import org.srcdeps.core.fs.PathLock;
import org.srcdeps.core.fs.PathLocker;
import org.srcdeps.core.fs.PersistentBuildRefStore;
import org.srcdeps.core.shell.IoRedirects;
import org.srcdeps.mvn.config.ConfigurationProducer;

/**
 * A {@link LocalRepositoryManager} able to build the requested artifacts from their sources.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class SrcdepsLocalRepositoryManager implements LocalRepositoryManager {
    private static final Logger log = LoggerFactory.getLogger(SrcdepsLocalRepositoryManager.class);

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

    /**
     * Finds the first {@link ScmRepository} associated with the given {@code groupId:artifactId:version} triple.
     *
     * @param repositories
     * @param groupId
     * @param artifactId
     * @param version
     * @return the matching {@link ScmRepository}
     */
    private static ScmRepository findScmRepo(List<ScmRepository> repositories, String groupId, String artifactId,
            String version) {
        for (ScmRepository scmRepository : repositories) {
            if (scmRepository.getGavSet().contains(groupId, artifactId, version)) {
                return scmRepository;
            }
        }
        throw new IllegalStateException(
                String.format("No srcdeps SCM repository configured in srcdeps.yaml for artifact [%s:%s:%s]", groupId,
                        artifactId, version));
    }

    private final BuildDirectoriesManager buildDirectoriesManager;

    private final BuildRefStore buildRefStore;
    private final BuildService buildService;

    private final ConfigurationProducer configurationProducer;

    private final LocalRepositoryManager delegate;

    private final FetchLog fetchLog;

    private final ScmService scmService;

    private final Path scrdepsDir;

    public SrcdepsLocalRepositoryManager(LocalRepositoryManager delegate, BuildService buildService,
            ScmService scmService, PathLocker<SrcVersion> pathLocker, ConfigurationProducer configurationProducer) {
        super();
        this.delegate = delegate;
        this.buildService = buildService;
        this.scmService = scmService;
        this.scrdepsDir = delegate.getRepository().getBasedir().toPath().getParent().resolve("srcdeps");
        this.buildRefStore = new PersistentBuildRefStore(scrdepsDir.resolve("build-refs"));
        this.buildDirectoriesManager = new BuildDirectoriesManager(scrdepsDir, pathLocker);
        this.configurationProducer = configurationProducer;
        this.fetchLog = new FetchLog();
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
        Artifact artifact = request.getArtifact();
        log.trace("Srcdeps looking up locally {}", artifact);
        final LocalArtifactResult result = delegate.find(session, request);

        String version = artifact.getVersion();
        if (SrcVersion.isSrcVersion(version)) {

            final SrcVersion srcVersion = SrcVersion.parse(version);
            if (srcVersion.isImmutable() && result.isAvailable()) {
                /* Only tags and revisions do not need to get rebuilt once there in the local repo */
                log.debug("Srcdeps found {} in the local maven repository; no need to rebuild", request.getArtifact());
                return result;
            }

            final Configuration configuration = configurationProducer.getConfiguration();

            if (configuration.isSkip()) {
                log.debug("Srcdeps is configured to be skipped");
            } else {
                final ScmRepository scmRepo = findScmRepo(configuration.getRepositories(), artifact.getGroupId(),
                        artifact.getArtifactId(), version);

                /* Ensure that we fetch and build a branch just once per outer build */
                final FetchId fetchId = new FetchId(scmRepo.getId(), scmRepo.getUrls());
                if (fetchLog.contains(fetchId)) {
                    log.debug(
                            "Srcdeps SCM repository {} has been maked as built and up-to-date in this JVM. The artifact {} must be there in the local maven repository",
                            fetchId, artifact);
                    return result;
                }

                try (PathLock projectBuildDir = buildDirectoriesManager.openBuildDirectory(scmRepo.getIdAsPath(),
                        srcVersion)) {

                    /* query the delegate again, because things may have changed since we requested the lock */
                    final LocalArtifactResult result2 = delegate.find(session, request);
                    if (srcVersion.isImmutable() && result2.isAvailable()) {
                        log.debug("Srcdeps found {} in the local maven repository; no need to rebuild", artifact);
                        return result2;
                    } else if (fetchLog.contains(fetchId)) {
                        log.debug(
                                "Srcdeps SCM repository {} has been maked as built and up-to-date in this JVM. The artifact {} must be there in the local maven repository",
                                fetchId, artifact);
                        return result2;
                    } else {
                        /* The artifact is not available in the local repo, we probably need to build */
                        BuilderIo builderIo = scmRepo.getBuilderIo();
                        IoRedirects ioRedirects = IoRedirects.builder() //
                                .stdin(IoRedirects.parseUri(builderIo.getStdin())) //
                                .stdout(IoRedirects.parseUri(builderIo.getStdout())) //
                                .stderr(IoRedirects.parseUri(builderIo.getStderr())) //
                                .build();

                        List<String> buildArgs = enhanceBuildArguments(scmRepo.getBuildArguments(),
                                configurationProducer.getConfigurationLocation(),
                                delegate.getRepository().getBasedir().getAbsolutePath());

                        BuildRequest buildRequest = BuildRequest.builder() //
                                .dependentProjectRootDirectory(
                                        configurationProducer.getMultimoduleProjectRootDirectory()) //
                                .projectRootDirectory(projectBuildDir.getPath()) //
                                .scmUrls(scmRepo.getUrls()) //
                                .srcVersion(srcVersion) //
                                .buildArguments(buildArgs) //
                                .timeoutMs(scmRepo.getBuildTimeout().toMilliseconds()) //
                                .skipTests(scmRepo.isSkipTests()) //
                                .forwardProperties(configuration.getForwardProperties()) //
                                .addDefaultBuildArguments(scmRepo.isAddDefaultBuildArguments()) //
                                .verbosity(scmRepo.getVerbosity()) //
                                .ioRedirects(ioRedirects) //
                                .versionsMavenPluginVersion(scmRepo.getMaven().getVersionsMavenPluginVersion())
                                .gradleModelTransformer(scmRepo.getGradle().getModelTransformer()).build();

                        final BuildRequestId buildRequestId = buildRequest.getId();
                        final String newCommitId = scmService.checkout(buildRequest);

                        if (!srcVersion.isImmutable()) {
                            /* A branch */
                            log.info("Srcdeps mapped artifact {} to revision {}", artifact, newCommitId);
                            if (result2.isAvailable() && newCommitId.equals(buildRefStore.retrieve(buildRequestId))) {
                                /*
                                 * we have already built this branch in the past and the state of the branch has not
                                 * changed
                                 */
                                log.debug(
                                        "Srcdeps version {} of {} currently at commit {} was built in the past; no need to build again",
                                        artifact.getVersion(), scmRepo.getId(), newCommitId);
                                fetchLog.add(fetchId);
                                return result2;
                            }
                        }

                        log.debug("Srcdeps requires a rebuild of {}, triggered by {} lookup", fetchId, artifact);
                        buildService.build(buildRequest);

                        fetchLog.add(fetchId);

                        buildRefStore.store(buildRequestId, newCommitId);

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
