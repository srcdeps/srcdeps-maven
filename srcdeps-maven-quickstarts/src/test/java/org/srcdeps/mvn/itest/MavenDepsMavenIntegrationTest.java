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
package org.srcdeps.mvn.itest;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Manifest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.Gavtc;
import org.srcdeps.core.fs.PersistentBuildMetadataStore;
import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.3.1" })
public class MavenDepsMavenIntegrationTest extends AbstractMavenDepsIntegrationTest {

    static final Logger log = LoggerFactory.getLogger(MavenDepsMavenIntegrationTest.class);

    protected static final String ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID = "org.l2x6.maven.srcdeps.itest";

    public MavenDepsMavenIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void mvnGitBom() throws Exception {

        final String project = "srcdeps-mvn-git-bom-quickstart";
        final String srcVersion = "0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8";

        String[] expectedGavtcs = new String[] { //
                pomJar(groupId(project), project, QUICKSTART_VERSION), //
                pom(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");

    }

    @Test
    public void mvnGitBranch() throws Exception {
        TestUtils.deleteSrcdepsDirectory();

        PersistentBuildMetadataStore buildMetadataStore = new PersistentBuildMetadataStore(TestUtils.getSrcdepsBuildMetadataPath());
        {
            PersistentBuildMetadataStore.BuildRequestIdCollector consumer = new PersistentBuildMetadataStore.BuildRequestIdCollector();
            buildMetadataStore.walkBuildRequestHashes(consumer);
            Assert.assertEquals("found " + consumer.getHashes(), 0, consumer.getHashes().size());
        }

        final String project = "srcdeps-mvn-git-branch-quickstart";
        final String srcVersion = "0.0.1-SRC-branch-morning-branch";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-api", QUICKSTART_VERSION), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion) //
        };

        final LocalMavenRepoVerifier repoVerifier = new LocalMavenRepoVerifier(project, expectedGavtcs);
        repoVerifier.clean();

        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project;
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepoPath().resolve(quickstartRepoDir));

        /* this copies the test project to target/test-projects */
        final File workDir = resources.getBasedir(project);

        /* Create a local clone */
        final Path localGitRepos = TestUtils.getBasedir().resolve("target/local-git-repos");
        final Path srcdepsTestArtifactDirectory = localGitRepos.resolve("srcdeps-test-artifact");
        SrcdepsCoreUtils.deleteDirectory(srcdepsTestArtifactDirectory);

        final String remoteGitUri = "https://github.com/srcdeps/srcdeps-test-artifact.git";
        final String mornigBranch = "morning-branch";
        try (Git git = Git.cloneRepository().setURI(remoteGitUri).setDirectory(srcdepsTestArtifactDirectory.toFile())
                .setBranch(mornigBranch).call()) {
            git.checkout().setName(mornigBranch).call();
        }

        /* Set the local clone in srcdeps.yaml */
        final Path srcdepsYamlPath = workDir.toPath().resolve("srcdeps.yaml");
        String srcdepsYaml = new String(Files.readAllBytes(srcdepsYamlPath), StandardCharsets.UTF_8);
        final Path localGitRepoPath = srcdepsTestArtifactDirectory.resolve(".git");
        final String localGitRepoUri = localGitRepoPath.toUri().toString();
        srcdepsYaml = srcdepsYaml.replace(remoteGitUri, localGitRepoUri);
        Files.write(srcdepsYamlPath, srcdepsYaml.getBytes(StandardCharsets.UTF_8));

        final String hashA84403b;
        final Path hashA84403bPath;
        {
            final MavenExecution execution = verifier.forProject(workDir) //
                    .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildMetadataStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));

            {
                PersistentBuildMetadataStore.BuildRequestIdCollector consumer = new PersistentBuildMetadataStore.BuildRequestIdCollector();
                buildMetadataStore.walkBuildRequestHashes(consumer);
                Assert.assertEquals("found " + consumer.getHashes(), 1, consumer.getHashes().size());
                hashA84403b = consumer.getHashes().iterator().next();
                hashA84403bPath = buildMetadataStore.createBuildRequestIdPath(hashA84403b);
            }

            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                            + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                    .assertLogText("srcdeps: Fetching version [0.0.1-SRC-branch-morning-branch] from SCM URL 1/1 ["
                            + localGitRepoUri + "]") //
                    .assertLogText(
                            "srcdeps: Mapped artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] to revision [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogTextPath(
                            "srcdeps: commitId path [" + hashA84403bPath + File.separator + "commitId] does not exist") //
                    .assertLogText("srcdeps: A rebuild of [org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "]] was triggered by [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] lookup") //
                    .assertLogText("srcdeps: Uninstalling [0] GAVs before rebuilding them") //

                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "commitId] will point at commitId [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4]") //
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_pom] will point at sha1") //
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_jar] will point at sha1") //
                    .assertLogText("srcdeps: Installed [2] artifacts") //
                    .assertLogText("srcdeps: SCM repository [org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "]] has been marked as built and up-to-date in this JVM. The artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:jar:0.0.1-SRC-branch-morning-branch] must be there in the local maven repository") //

            ;

        }

        repoVerifier.verify();
        Files.move(workDir.toPath().resolve("log.txt"), workDir.toPath().resolve("log-1.txt"));

        /* The second build: no branch rebuild should be needed */
        {
            final MavenExecution execution = verifier.forProject(workDir) //
                    .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildMetadataStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                            + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                    .assertLogText("srcdeps: Fetching version [0.0.1-SRC-branch-morning-branch] from SCM URL 1/1 ["
                            + localGitRepoUri + "]") //
                    .assertLogText(
                            "srcdeps: Mapped artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] to revision [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4] via [0.0.1-SRC-branch-morning-branch") //
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "commitId] points at commitId [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4]") //
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_pom] points at sha1 [bb7e07cbf984f98d12abaa4fee58577b032d537c]") //
                    .assertLogText(
                            "srcdeps: The artifact in the local Maven repo has not changed since we built it in the past: [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch")

                    /* there should be no dependency rebuild in the second build */
                    .assertNoLogText("srcdeps: A rebuild of") //
            ;
        }
        repoVerifier.verify();
        Files.move(workDir.toPath().resolve("log.txt"), workDir.toPath().resolve("log-2.txt"));

        /* The third build: a dependency rebuild should happen when we add a new commit to the branch */
        /* Add a file */
        final Path readmePath = srcdepsTestArtifactDirectory.resolve("README.adoc");
        Files.write(readmePath, "Test".getBytes(StandardCharsets.UTF_8));

        final String expectedCommitId;
        /* Commit */
        try (Git git = Git.init().setDirectory(srcdepsTestArtifactDirectory.toFile()).call()) {
            git.add().addFilepattern("README.adoc").call();
            expectedCommitId = git.commit().setMessage("Added README").call().getId().getName();
        }

        {
            final MavenExecution execution = verifier.forProject(workDir) //
                    // .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildMetadataStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));
            Path commitIdPath = buildMetadataStore.createBuildRequestIdPath(hashA84403b).resolve("commitId");
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                            + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                    .assertLogText("srcdeps: Fetching version [0.0.1-SRC-branch-morning-branch] from SCM URL 1/1 ["
                            + localGitRepoUri + "]") //
                    .assertLogText(
                            "srcdeps: Mapped artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] to revision ["
                                    + expectedCommitId) //
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "commitId] points at commitId [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4]")
                    .assertLogText("srcdeps: A rebuild of [org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "]] was triggered by [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] lookup")
                    .assertLogText("srcdeps: Uninstalling [1] GAVs before rebuilding them")
                    .assertLogTextPath("srcdeps: Uninstalling [" + TestUtils.getMvnLocalRepoPath()
                            + "/org/l2x6/maven/srcdeps/itest/srcdeps-test-artifact/0.0.1-SRC-branch-morning-branch"
                                    .replace('/', File.separatorChar))
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "commitId] will point at commitId [" + expectedCommitId)
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_pom] will point at sha1")
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_jar] will point at sha1")
                    .assertLogText("srcdeps: Installed [2] artifacts")
                    .assertNoLogText(
                            "artifact in the local Maven repo has not changed since we built it in the past");
        }
        repoVerifier.verify();
        Files.move(workDir.toPath().resolve("log.txt"), workDir.toPath().resolve("log-3.txt"));

        /* The fourth build: a dependency rebuild should happen when we reset back the branch */
        try (Git git = Git.init().setDirectory(srcdepsTestArtifactDirectory.toFile()).call()) {
            git.reset().setMode(ResetType.HARD).setRef("a84403b6fb44c5a588a9fe39d939c977e1e5c6a4").call();
        }

        {
            final MavenExecution execution = verifier.forProject(workDir) //
                    // .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildMetadataStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                            + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                    .assertLogText("srcdeps: Fetching version [0.0.1-SRC-branch-morning-branch] from SCM URL 1/1 ["
                            + localGitRepoUri + "]") //
                    .assertLogText(
                            "srcdeps: Mapped artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] to revision [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //

                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "commitId] points at commitId [" + expectedCommitId)
                    .assertLogText("srcdeps: A rebuild of [org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "]] was triggered by [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch] lookup")
                    .assertLogText("srcdeps: Uninstalling [1] GAVs before rebuilding them")
                    .assertLogTextPath("srcdeps: Uninstalling [" + TestUtils.getMvnLocalRepoPath()
                            + "/org/l2x6/maven/srcdeps/itest/srcdeps-test-artifact/0.0.1-SRC-branch-morning-branch"
                                    .replace('/', File.separatorChar))
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "commitId] will point at commitId [a84403b6fb44c5a588a9fe39d939c977e1e5c6a4]")
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_pom] will point at sha1")
                    .assertLogTextPath("srcdeps: Path [" + hashA84403bPath + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.1-SRC-branch-morning-branch_jar] will point at sha1")
                    .assertLogText("srcdeps: Installed [2] artifacts")
                    .assertLogText("srcdeps: SCM repository [org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "]] has been marked as built and up-to-date in this JVM. The artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:jar:0.0.1-SRC-branch-morning-branch] must be there in the local maven repository")
                    .assertNoLogText(
                            "srcdeps artifact in the local Maven repo has not changed since we built it in the past") //
            ;
        }
        repoVerifier.verify();
    }

    @Test
    public void mvnGitInterdepModules() throws Exception {

        final String project = "srcdeps-mvn-git-interdep-modules-quickstart";
        final String srcVersion = "0.0.1-SRC-revision-56576301d21c53439bcb5c48502c723282633cc7";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pom(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

    @Test
    public void mvnGitParent() throws Exception {

        final String project = "srcdeps-mvn-git-parent-quickstart";
        final String srcVersion = "0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8";

        String[] expectedGavtcs = new String[] { //
                pomJar(groupId(project), project, QUICKSTART_VERSION), //
                pom(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

    @Test
    public void mvnGitProfileAndProperties() throws Exception {
        final String project = "srcdeps-mvn-git-profile-and-properties-quickstart";
        final String srcVersion = "0.0.1-SRC-revision-834947e286f1f59bd6c5c3ca3823f4656bc9345b";

        String[] expectedGavtcs = new String[] { //
                pom(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", srcVersion) //
        };

        String[] unexpectedGavtcs = new String[] { //
                pomJar(groupId(project), project, QUICKSTART_VERSION) //
        };

        assertBuild(project, expectedGavtcs, unexpectedGavtcs, "clean", "test");
    }

    @Test
    public void mvnGitRevision() throws Exception {
        final String project = "srcdeps-mvn-git-revision-quickstart";
        final String srcVersion = "0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion) //
        };
        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

    @Test
    public void mvnGitRevisionNonMaster() throws Exception {
        final String project = "srcdeps-mvn-git-revision-non-master-quickstart";
        final String srcVersion = "0.0.1-SRC-revision-dbad2cdc30b5bb3ff62fc89f57987689a5f3c220";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

    @Test
    public void mvnGitSnapshotRevision() throws Exception {
        TestUtils.deleteSrcdepsDirectory();

        PersistentBuildMetadataStore buildMetadataStore = new PersistentBuildMetadataStore(TestUtils.getSrcdepsBuildMetadataPath());
        {
            PersistentBuildMetadataStore.BuildRequestIdCollector consumer = new PersistentBuildMetadataStore.BuildRequestIdCollector();
            buildMetadataStore.walkBuildRequestHashes(consumer);
            Assert.assertEquals("found " + consumer.getHashes(), 0, consumer.getHashes().size());
        }

        final String project = "srcdeps-mvn-git-snapshot-quickstart";
        final String depVersion = "0.0.2-SNAPSHOT";
        final String srcVersion = "0.0.2-SRC-revision-67e9a1480f6de434e513c3ced2b4e952dce5ddc0";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", depVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", depVersion) //
        };
        String[] unexpectedGavtcs = new String[] { //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", srcVersion) //
        };
        /* this copies the test project to target/test-projects */
        final File workDir = resources.getBasedir(project);
        final Gavtc serviceGavts = new Gavtc(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service",
                depVersion, "jar");

        final String hash67e9a14;
        final Path hash67e9a14Path;
        /* The first build: the snapshots will be removed before building */
        {
            LocalMavenRepoVerifier repoVerifier = new LocalMavenRepoVerifier(project, expectedGavtcs, unexpectedGavtcs);
            repoVerifier.clean();

            final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project;
            SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepoPath().resolve(quickstartRepoDir));

            MavenExecution execution = verifier.forProject(workDir) //
                    .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                    .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath);
            WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(execution.execute("clean", "install"));

            {
                PersistentBuildMetadataStore.BuildRequestIdCollector consumer = new PersistentBuildMetadataStore.BuildRequestIdCollector();
                buildMetadataStore.walkBuildRequestHashes(consumer);
                Assert.assertEquals("found " + consumer.getHashes(), 1, consumer.getHashes().size());
                hash67e9a14 = consumer.getHashes().iterator().next();
                hash67e9a14Path = buildMetadataStore.createBuildRequestIdPath(hash67e9a14);
            }

            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                            + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                    .assertLogText(
                            "srcdeps: Fetching version [revision-67e9a1480f6de434e513c3ced2b4e952dce5ddc0] from SCM URL 1/1 [https://github.com/srcdeps/srcdeps-test-artifact.git]")
                    .assertLogText(
                            "srcdeps: Mapped artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-service:pom:0.0.2-SNAPSHOT] to revision [67e9a1480f6de434e513c3ced2b4e952dce5ddc0] via [revision-67e9a1480f6de434e513c3ced2b4e952dce5ddc0")
                    .assertLogText(
                            "srcdeps: Adding SCM repo to FetchLog: [org.l2x6.maven.srcdeps.itest:[git:https://github.com/srcdeps/srcdeps-test-artifact.git]]")
                    .assertLogText(
                            "srcdeps: commitId path [" + hash67e9a14Path + File.separator + "commitId] does not exist")
                    .assertLogText(
                            "srcdeps: A rebuild of [org.l2x6.maven.srcdeps.itest:[git:https://github.com/srcdeps/srcdeps-test-artifact.git]] was triggered by [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-service:pom:0.0.2-SNAPSHOT] lookup")
                    .assertLogText("srcdeps: Uninstalling [0] GAVs before rebuilding them")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "commitId] will point at commitId [67e9a1480f6de434e513c3ced2b4e952dce5ddc0]")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.2-SNAPSHOT_pom] will point at sha1 ")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-api_0.0.2-SNAPSHOT_pom] will point at sha1 ")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-api_0.0.2-SNAPSHOT_jar] will point at sha1 ")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-service_0.0.2-SNAPSHOT_pom] will point at sha1 ")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-service_0.0.2-SNAPSHOT_jar] will point at sha1 ")
                    .assertLogText("srcdeps: Installed [5] artifacts");

            repoVerifier.verify();
            final Manifest manifest = TestUtils.loadManifest(serviceGavts);
            Assert.assertNotNull(manifest);
            Assert.assertEquals("67e9a1480f6de434e513c3ced2b4e952dce5ddc0",
                    manifest.getMainAttributes().getValue("Built-From-Git-SHA1"));
        }

        Files.move(workDir.toPath().resolve("log.txt"), workDir.toPath().resolve("log-1.txt"));

        /*
         * The second build: malform one of the dependency artifacts and try to rebuid. A success will prove that the
         * dependency artifact got rebuilt
         */
        Path serviceJarPath = TestUtils.getMvnLocalRepo().resolve(serviceGavts);
        try (OutputStream in = Files.newOutputStream(serviceJarPath)) {
            in.write(42);
        }
        {
            LocalMavenRepoVerifier repoVerifier = new LocalMavenRepoVerifier(project, expectedGavtcs, unexpectedGavtcs);

            MavenExecution execution = verifier.forProject(workDir) //
                    .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                    .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath);
            WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(execution.execute("clean", "install"));
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                            + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                    .assertLogText(
                            "srcdeps: Fetching version [revision-67e9a1480f6de434e513c3ced2b4e952dce5ddc0] from SCM URL 1/1 [https://github.com/srcdeps/srcdeps-test-artifact.git]")
                    .assertLogText(
                            "srcdeps: Mapped artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-service:pom:0.0.2-SNAPSHOT] to revision [67e9a1480f6de434e513c3ced2b4e952dce5ddc0] via [revision-67e9a1480f6de434e513c3ced2b4e952dce5ddc0")
                    .assertLogText(
                            "srcdeps: Adding SCM repo to FetchLog: [org.l2x6.maven.srcdeps.itest:[git:https://github.com/srcdeps/srcdeps-test-artifact.git]]")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "commitId] points at commitId [67e9a1480f6de434e513c3ced2b4e952dce5ddc0]")
                    .assertLogText(
                            "srcdeps: Rebuilding: sha1 of artifact [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-service:0.0.2-SNAPSHOT:jar] in local Maven repository differs from last known sha1 built by srcdeps")
                    .assertLogText(
                            "srcdeps: A rebuild of [org.l2x6.maven.srcdeps.itest:[git:https://github.com/srcdeps/srcdeps-test-artifact.git]] was triggered by [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-service:pom:0.0.2-SNAPSHOT] lookup")
                    .assertLogText("srcdeps: Uninstalling [3] GAVs before rebuilding them")
                    .assertLogTextPath("srcdeps: Uninstalling [" + TestUtils.getMvnLocalRepoPath()
                            + "/org/l2x6/maven/srcdeps/itest/srcdeps-test-artifact-api/0.0.2-SNAPSHOT".replace('/',
                                    File.separatorChar))
                    .assertLogTextPath("srcdeps: Uninstalling [" + TestUtils.getMvnLocalRepoPath()
                            + "/org/l2x6/maven/srcdeps/itest/srcdeps-test-artifact-service/0.0.2-SNAPSHOT".replace('/',
                                    File.separatorChar))
                    .assertLogTextPath("srcdeps: Uninstalling [" + TestUtils.getMvnLocalRepoPath()
                            + "/org/l2x6/maven/srcdeps/itest/srcdeps-test-artifact/0.0.2-SNAPSHOT".replace('/',
                                    File.separatorChar))
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "commitId] will point at commitId [67e9a1480f6de434e513c3ced2b4e952dce5ddc0]")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact_0.0.2-SNAPSHOT_pom] will point at sha1")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-api_0.0.2-SNAPSHOT_pom] will point at sha1")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-api_0.0.2-SNAPSHOT_jar] will point at sha1")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-service_0.0.2-SNAPSHOT_pom] will point at sha1")
                    .assertLogTextPath("srcdeps: Path [" + hash67e9a14Path + File.separator
                            + "org.l2x6.maven.srcdeps.itest_srcdeps-test-artifact-service_0.0.2-SNAPSHOT_jar] will point at sha1")
                    .assertLogText("srcdeps: Installed [5] artifacts");

            repoVerifier.verify();
            final Manifest manifest = TestUtils.loadManifest(serviceGavts);
            Assert.assertNotNull(manifest);
            Assert.assertEquals("67e9a1480f6de434e513c3ced2b4e952dce5ddc0",
                    manifest.getMainAttributes().getValue("Built-From-Git-SHA1"));
        }
        Files.move(workDir.toPath().resolve("log.txt"), workDir.toPath().resolve("log-2.txt"));

    }

    @Test
    public void mvnGitTag() throws Exception {
        final String project = "srcdeps-mvn-git-tag-quickstart";
        final String srcVersion = "0.0.1-SRC-tag-0.0.1";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

    @Test
    public void mvnwGit() throws Exception {
        final String project = "srcdeps-mvnw-git-quickstart";
        final String srcVersion = "0.0.2-SRC-revision-dc21a1375bd5388b5489621e71dbe6e0e70db200";

        String[] expectedGavtcs = new String[] { //
                pomJar(groupId(project), project, QUICKSTART_VERSION), //
                pom(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-api", srcVersion), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact-service", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

}
