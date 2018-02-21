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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(MavenDepsMavenIntegrationTest.class);

    protected static final String ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID = "org.l2x6.maven.srcdeps.itest";

    private static Map<String, String> loadBuildRefs(Path basedir) throws IOException {
        final Map<String, String> result = new TreeMap<>();
        Path buildRefsPath = basedir.resolve("target/srcdeps/build-refs");
        if (Files.exists(buildRefsPath)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(buildRefsPath)) {
                for (Path path : directoryStream) {
                    result.put(path.getFileName().toString(), new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }

    public MavenDepsMavenIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void mvnFailWithArgumentsBom() throws Exception {
        final String project = "srcdeps-mvn-git-bom-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "release:prepare");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and goal [release:prepare]")
                .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsDependency() throws Exception {
        final String project = "srcdeps-mvn-git-revision-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));

        WrappedMavenExecutionResult result = build(project, "clean", "release:prepare");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b] and goal [release:prepare]")
                .assertLogText("BUILD FAILURE");

    }

    @Test
    public void mvnFailWithArgumentsParent() throws Exception {
        final String project = "srcdeps-mvn-git-parent-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "release:prepare");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and goal [release:prepare]")
                .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsProfile() throws Exception {
        final String project = "srcdeps-mvn-git-profile-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Psrcdeps-profile");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and profile [srcdeps-profile]")
                .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsPropertyCli() throws Exception {
        final String project = "srcdeps-mvn-git-bom-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Dsrcdeps-fail-property");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and property [srcdeps-fail-property]")
                .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsPropertyCliOverride() throws Exception {
        final String project = "srcdeps-mvn-git-revision-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Dsrcdeps-fail-property-cli",
                "-Dsrcdeps.maven.failWith.properties=srcdeps-fail-property-cli");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b] and property [srcdeps-fail-property-cli]")
                .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsPropertyPom() throws Exception {
        final String project = "srcdeps-mvn-git-profile-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Psrcdeps-property-profile");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and property [srcdeps-fail-property]")
                .assertLogText("BUILD FAILURE");
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
        SrcdepsCoreUtils.deleteDirectory(basedir.resolve("target/srcdeps"));

        {
            final Map<String, String> buildRefs = loadBuildRefs(basedir);
            Assert.assertEquals("found " + buildRefs, 0, buildRefs.size());
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
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepoPath.resolve(quickstartRepoDir));

        /* this copies the test project to target/test-projects */
        final File workDir = resources.getBasedir(project);

        /* Create a local clone */
        final Path localGitRepos = basedir.resolve("target/local-git-repos");
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

        String hash_a84403b = null;
        {
            final MavenExecution execution = verifier.forProject(workDir) //
                    // .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOptions("-Dmaven.repo.local=" + mvnLocalRepo.getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption("-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildRefStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));

            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate "
                            + TakariLocalRepositoryManagerFactory.class.getName()) //
                    .assertLogText("srcdeps: attempting to clone version 0.0.1-SRC-branch-morning-branch from SCM URL "
                            + localGitRepoUri + "") //
                    .assertLogText(
                            "Srcdeps mapped artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch to revision a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogText("Srcdeps requires a rebuild of org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "], triggered by org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch lookup") //
            ;

            final Map<String, String> buildRefs = loadBuildRefs(basedir);
            Assert.assertEquals("found " + buildRefs, 1, buildRefs.size());
            hash_a84403b = buildRefs.keySet().iterator().next();
            result.assertLogText("BuildRequestId path " + basedir + "/target/srcdeps/build-refs/" + hash_a84403b
                    + " will point at commitId a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogText("Srcdeps SCM repository org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "] has been maked as built and up-to-date in this JVM. The artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:jar:0.0.1-SRC-branch-morning-branch must be there in the local maven repository") //
            ;
        }

        repoVerifier.verify();
        Files.move(workDir.toPath().resolve("log.txt"), workDir.toPath().resolve("log-1.txt"));

        /* The second build: no branch rebuild should be needed */
        {
            final MavenExecution execution = verifier.forProject(workDir) //
                    // .withCliOption("-X") //
                    .withCliOption("-B") // batch
                    .withCliOptions("-Dmaven.repo.local=" + mvnLocalRepo.getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption("-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildRefStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate "
                            + TakariLocalRepositoryManagerFactory.class.getName()) //
                    .assertLogText("srcdeps: attempting to fetch version 0.0.1-SRC-branch-morning-branch from SCM URL "
                            + localGitRepoUri + "") //
                    .assertLogText(
                            "Srcdeps version 0.0.1-SRC-branch-morning-branch of org.l2x6.maven.srcdeps.itest currently at commit a84403b6fb44c5a588a9fe39d939c977e1e5c6a4 was built in the past; no need to build again") //
                    .assertLogText("BuildRequestId path " + basedir + "/target/srcdeps/build-refs/" + hash_a84403b
                            + " points at commitId a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogText(
                            "Srcdeps mapped artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch to revision a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogText("Srcdeps SCM repository org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "] has been maked as built and up-to-date in this JVM. The artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:jar:0.0.1-SRC-branch-morning-branch must be there in the local maven repository") //

                    /* there should be no dependency rebuild in the second build */
                    .assertNoLogText("Srcdeps requires a rebuild") //
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
                    .withCliOptions("-Dmaven.repo.local=" + mvnLocalRepo.getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption("-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildRefStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate "
                            + TakariLocalRepositoryManagerFactory.class.getName()) //
                    .assertLogText("srcdeps: attempting to fetch version 0.0.1-SRC-branch-morning-branch from SCM URL "
                            + localGitRepoUri + "") //
                    .assertLogText(
                            "Srcdeps mapped artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch to revision "
                                    + expectedCommitId) //
                    .assertLogText("BuildRequestId path " + basedir + "/target/srcdeps/build-refs/" + hash_a84403b
                            + " points at commitId a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogText("Srcdeps requires a rebuild of org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "], triggered by org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch lookup")
                    .assertLogText("BuildRequestId path " + basedir + "/target/srcdeps/build-refs/" + hash_a84403b
                            + " will point at commitId " + expectedCommitId)
                    .assertLogText("Srcdeps SCM repository org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "] has been maked as built and up-to-date in this JVM. The artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:jar:0.0.1-SRC-branch-morning-branch must be there in the local maven repository")

            ;
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
                    .withCliOptions("-Dmaven.repo.local=" + mvnLocalRepo.getRootDirectory().toAbsolutePath().toString()) //
                    .withCliOption("-s").withCliOption(mrmSettingsXmlPath)
                    .withCliOption(
                            "-Dorg.slf4j.simpleLogger.log.org.srcdeps.mvn.localrepo.SrcdepsLocalRepositoryManager=debug")
                    .withCliOption("-Dorg.slf4j.simpleLogger.log.org.srcdeps.core.fs.PersistentBuildRefStore=debug");
            final WrappedMavenExecutionResult result = new WrappedMavenExecutionResult(
                    execution.execute("clean", "install"));
            result //
                    .assertErrorFreeLog() //
                    .assertLogText("SrcdepsLocalRepositoryManager will decorate "
                            + TakariLocalRepositoryManagerFactory.class.getName()) //
                    .assertLogText("srcdeps: attempting to fetch version 0.0.1-SRC-branch-morning-branch from SCM URL "
                            + localGitRepoUri + "") //
                    .assertLogText(
                            "Srcdeps mapped artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch to revision a84403b6fb44c5a588a9fe39d939c977e1e5c6a4") //
                    .assertLogText("BuildRequestId path " + basedir + "/target/srcdeps/build-refs/" + hash_a84403b
                            + " points at commitId " + expectedCommitId) //
                    .assertLogText("Srcdeps requires a rebuild of org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "], triggered by org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:pom:0.0.1-SRC-branch-morning-branch lookup")
                    .assertLogText("BuildRequestId path " + basedir + "/target/srcdeps/build-refs/" + hash_a84403b
                            + " will point at commitId a84403b6fb44c5a588a9fe39d939c977e1e5c6a4")
                    .assertLogText("Srcdeps SCM repository org.l2x6.maven.srcdeps.itest:[git:" + localGitRepoUri
                            + "] has been maked as built and up-to-date in this JVM. The artifact org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:jar:0.0.1-SRC-branch-morning-branch must be there in the local maven repository");
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
