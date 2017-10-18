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
package org.srcdeps.mvn.itest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.Gavtc;
import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenExecutionResult;
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

    public MavenDepsMavenIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    public MavenExecutionResult assertBuild(String project, String[] gavtcPatternsExpectedToExist,
            String[] gavtcPatternsExpectedNotToExist, String... goals) throws Exception {

        /* delete all expected and unexpected groups */
        List<Path> expectedToExist = new ArrayList<>();
        for (String gavtcPattern : gavtcPatternsExpectedToExist) {
            for (Gavtc gavtc : Gavtc.ofPattern(gavtcPattern)) {
                SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(gavtc.getGroupId()));
                expectedToExist.add(mvnLocalRepo.resolve(gavtc));
            }
        }
        List<Path> expectedNotToExist = new ArrayList<>();
        for (String gavtcPattern : gavtcPatternsExpectedNotToExist) {
            for (Gavtc gavtc : Gavtc.ofPattern(gavtcPattern)) {
                SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(gavtc.getGroupId()));
                expectedNotToExist.add(mvnLocalRepo.resolve(gavtc));
            }
        }

        MavenExecutionResult result = build(project, goals);
        result //
                .assertErrorFreeLog() //
                .assertLogText("SrcdepsLocalRepositoryManager will decorate "
                        + TakariLocalRepositoryManagerFactory.class.getName()) //
        ;

        for (Path p : expectedToExist) {
            assertExists(p);
        }
        for (Path p : expectedNotToExist) {
            assertNotExists(p);
        }

        return result;
    }

    protected MavenExecutionResult build(String project, String... goals) throws Exception {
        log.error("Building test project {}", project);

        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project;
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepoPath.resolve(quickstartRepoDir));

        MavenExecution execution = verifier.forProject(resources.getBasedir(project)) //
                // .withCliOption("-X") //
                .withCliOptions("-Dmaven.repo.local=" + mvnLocalRepo.getRootDirectory().toAbsolutePath().toString()) //
                .withCliOption("-s").withCliOption(mrmSettingsXmlPath);
        return execution.execute(goals);
    }

    @Test
    public void mvnFailWithArgumentsBom() throws Exception {
        final String project = "srcdeps-mvn-git-bom-quickstart";
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolveGroup(groupId(project)));
        MavenExecutionResult result = build(project, "clean", "release:prepare");
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

        MavenExecutionResult result = build(project, "clean", "release:prepare");
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
        MavenExecutionResult result = build(project, "clean", "release:prepare");
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
        MavenExecutionResult result = build(project, "clean", "install", "-Psrcdeps-profile");
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
        MavenExecutionResult result = build(project, "clean", "install", "-Dsrcdeps-fail-property");
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
        MavenExecutionResult result = build(project, "clean", "install", "-Dsrcdeps-fail-property-cli",
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
        MavenExecutionResult result = build(project, "clean", "install", "-Psrcdeps-property-profile");
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
        final String project = "srcdeps-mvn-git-branch-quickstart";
        final String srcVersion = "0.0.1-SRC-branch-morning-branch";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(ORG_L2X6_MAVEN_SRCDEPS_ITEST_GROUPID, "srcdeps-test-artifact", srcVersion) //
        };

        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
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
