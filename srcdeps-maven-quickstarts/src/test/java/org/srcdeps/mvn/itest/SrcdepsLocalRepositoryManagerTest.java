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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.config.Maven;
import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.3.1" })
public class SrcdepsLocalRepositoryManagerTest {
    private static final String ORG_L2X6_MAVEN_SRCDEPS_ITEST = "org/l2x6/maven/srcdeps/itest";

    private static final Logger log = LoggerFactory.getLogger(SrcdepsLocalRepositoryManagerTest.class);

    private static final Path mvnLocalRepo;
    private static final String mrmSettingsXmlPath = System.getProperty("mrm.settings.xml");
    private static final String projectVersion = System.getProperty("project.version");
    private static final String encoding = System.getProperty("project.build.sourceEncoding");
    private static final Path basedir = Paths.get(System.getProperty("basedir", new File("").getAbsolutePath()));
    private static final Pattern replacementPattern = Pattern.compile(Pattern.quote("<version>") + "[^<]+" + Pattern.quote("</version><!-- @srcdeps.version@ -->"));
    private static final Path srcdepsQuickstartsPath;

    static {
        srcdepsQuickstartsPath = basedir.resolve("../srcdeps-maven-quickstarts").normalize();
        mvnLocalRepo = basedir.resolve("target/mvn-local-repo");
    }

    public final MavenRuntime verifier;

    @Rule
    public final TestResources resources = new TestResources(srcdepsQuickstartsPath.toString(), "target/test-projects") {

        @Override
        public File getBasedir(String project) throws IOException {

            File result = super.getBasedir(project);

            Path extensionsXmlPath = result.toPath().resolve(".mvn/extensions.xml");

            String extensionsXmlContent = new String(Files.readAllBytes(extensionsXmlPath), encoding);

            String newContent = replacementPattern.matcher(extensionsXmlContent).replaceAll("<version>" + projectVersion + "</version>");

            Assert.assertNotEquals(newContent, extensionsXmlContent);

            Files.write(extensionsXmlPath, newContent.getBytes(encoding));

            return result;
        }

    };

    @BeforeClass
    public static void beforeClass() throws IOException {

        SrcdepsCoreUtils.ensureDirectoryExistsAndEmpty(mvnLocalRepo);

        System.setProperty(Maven.getSrcdepsMavenSettingsProperty(), mrmSettingsXmlPath);

        Assert.assertTrue("[" + mrmSettingsXmlPath + "] should exist", Files.exists(Paths.get(mrmSettingsXmlPath)));
        Assert.assertNotNull("project.build.sourceEncoding property must be set", encoding);
        Assert.assertNotNull("project.version property must be set", projectVersion);
    }

    public SrcdepsLocalRepositoryManagerTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        this.verifier = runtimeBuilder.withExtension(new File("target/classes").getCanonicalFile()).build();
    }

    public MavenExecutionResult assertBuild(String project, String srcArtifactId, String srcVersion, String... goals) throws Exception {
        return assertBuild(project, srcArtifactId, srcVersion, new String[] {".jar", ".pom"}, goals);
    }

    public MavenExecutionResult build(String project, String srcArtifactId, String... goals) throws Exception {
        log.info("Building test project {}", project);

        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolve(ORG_L2X6_MAVEN_SRCDEPS_ITEST));

        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project;
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolve(quickstartRepoDir));

        MavenExecution execution = verifier.forProject(resources.getBasedir(project)) //
                // .withCliOption("-X") //
                .withCliOptions("-Dmaven.repo.local=" + mvnLocalRepo.toAbsolutePath().toString()).withCliOption("-s")
                .withCliOption(mrmSettingsXmlPath);
        return execution.execute(goals);
    }

    public MavenExecutionResult assertBuild(String project, String srcArtifactId, String srcVersion, String[] artifactSuffixes, String... goals) throws Exception {
        SrcdepsCoreUtils.deleteDirectory(mvnLocalRepo.resolve(ORG_L2X6_MAVEN_SRCDEPS_ITEST));
        final String testArtifactDir = ORG_L2X6_MAVEN_SRCDEPS_ITEST  + "/" + srcArtifactId;

        MavenExecutionResult result = build(project, srcArtifactId, goals);
        result //
                .assertErrorFreeLog() //
                .assertLogText(
                        "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        ;

        final String artifactPrefix = testArtifactDir + "/" + srcVersion + "/" + srcArtifactId + "-" + srcVersion;
        for (String suffix : artifactSuffixes) {
            assertExists(mvnLocalRepo.resolve(artifactPrefix + suffix));
        }

        return result;
    }

    public static void assertExists(Path path) {
        Assert.assertTrue(String.format("File or directory does not exist [%s]", path.toString()), Files.exists(path));
    }

    public static void assertNotExists(Path path) {
        Assert.assertTrue(String.format("File or directory exists [%s], but should not", path.toString()), !Files.exists(path));
    }

    @Test
    public void mvnGitBom() throws Exception {
        String project = "srcdeps-mvn-git-bom-quickstart";
        assertBuild(project, "srcdeps-test-artifact", "0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8", new String[] {".pom"}, "clean", "install");

        final String quickstartRepoDir = "org/srcdeps/mvn/quickstarts/" + project + "/" + project;
        String dependentVersion = "1.0-SNAPSHOT";
        final String artifactPrefix = quickstartRepoDir + "/" + dependentVersion + "/" + project + "-" + dependentVersion;
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".jar"));
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".pom"));
    }

    @Test
    public void mvnGitBranch() throws Exception {
        String project = "srcdeps-mvn-git-branch-quickstart";
        assertBuild(project, "srcdeps-test-artifact", "0.0.1-SRC-branch-morning-branch", "clean", "install");

        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project + "/" + project + "-jar";
        String dependentVersion = "1.0-SNAPSHOT";
        final String artifactPrefix = quickstartRepoDir + "/" + dependentVersion + "/" + project + "-jar-" + dependentVersion;
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".jar"));
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".pom"));
    }

    @Test
    public void mvnGitInterdepModules() throws Exception {
        assertBuild("srcdeps-mvn-git-interdep-modules-quickstart", "srcdeps-test-artifact-service", "0.0.1-SRC-revision-56576301d21c53439bcb5c48502c723282633cc7",
                "clean", "verify");
    }

    @Test
    public void mvnGitParent() throws Exception {
        String project = "srcdeps-mvn-git-parent-quickstart";
        assertBuild(project, "srcdeps-test-artifact", "0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8", new String[] {".pom"}, "clean", "install");

        final String quickstartRepoDir = "org/srcdeps/mvn/quickstarts/" + project + "/" + project;
        String dependentVersion = "1.0-SNAPSHOT";
        final String artifactPrefix = quickstartRepoDir + "/" + dependentVersion + "/" + project + "-" + dependentVersion;
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".jar"));
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".pom"));
    }

    @Test
    public void mvnGitProfileAndProperties() throws Exception {
        MavenExecutionResult result = assertBuild("srcdeps-mvn-git-profile-and-properties-quickstart", "srcdeps-test-artifact-api",
                "0.0.1-SRC-revision-834947e286f1f59bd6c5c3ca3823f4656bc9345b", "clean", "test");
    }

    @Test
    public void mvnGitRevision() throws Exception {
        String project = "srcdeps-mvn-git-revision-quickstart";
        assertBuild(project, "srcdeps-test-artifact", "0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b", "clean", "install");

        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project + "/" + project + "-jar";
        String dependentVersion = "1.0-SNAPSHOT";
        final String artifactPrefix = quickstartRepoDir + "/" + dependentVersion + "/" + project + "-jar-" + dependentVersion;
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".jar"));
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".pom"));
    }

    @Test
    public void mvnFailWithArgumentsDependency() throws Exception {
        String project = "srcdeps-mvn-git-revision-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "release:prepare");
        result
        .assertLogText(
                "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        .assertLogText("This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b] and goal [release:prepare]")
        .assertLogText("BUILD FAILURE");

    }

    @Test
    public void mvnFailWithArgumentsBom() throws Exception {
        String project = "srcdeps-mvn-git-bom-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "release:prepare");
        result
        .assertLogText(
                "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        .assertLogText("This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and goal [release:prepare]")
        .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnFailWithArgumentsParent() throws Exception {
        String project = "srcdeps-mvn-git-parent-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "release:prepare");
        result
        .assertLogText(
                "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        .assertLogText("This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and goal [release:prepare]")
        .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsProfile() throws Exception {
        String project = "srcdeps-mvn-git-profile-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "install", "-Psrcdeps-profile");
        result
        .assertLogText(
                "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        .assertLogText("This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and profile [srcdeps-profile]")
        .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsPropertyCli() throws Exception {
        String project = "srcdeps-mvn-git-bom-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "install", "-Dsrcdeps-fail-property");
        result
        .assertLogText(
                "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        .assertLogText("This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and property [srcdeps-fail-property]")
        .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsPropertyCliOverride() throws Exception {
        String project = "srcdeps-mvn-git-revision-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "install",
                "-Dsrcdeps-fail-property-cli", "-Dsrcdeps.maven.failWith.properties=srcdeps-fail-property-cli");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate " + TakariLocalRepositoryManagerFactory.class.getName()) //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b] and property [srcdeps-fail-property-cli]")
                .assertLogText("BUILD FAILURE");
    }

    @Test
    public void mvnFailWithArgumentsPropertyPom() throws Exception {
        String project = "srcdeps-mvn-git-profile-quickstart";
        MavenExecutionResult result = build(project, "srcdeps-test-artifact", "clean", "install", "-Psrcdeps-property-profile");
        result
        .assertLogText(
                "SrcdepsLocalRepositoryManager will decorate "+ TakariLocalRepositoryManagerFactory.class.getName()) //
        .assertLogText("This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and property [srcdeps-fail-property]")
        .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnGitRevisionNonMaster() throws Exception {
        assertBuild("srcdeps-mvn-git-revision-non-master-quickstart", "srcdeps-test-artifact", "0.0.1-SRC-revision-dbad2cdc30b5bb3ff62fc89f57987689a5f3c220", "clean", "compile");
    }

    @Test
    public void mvnGitTag() throws Exception {
        String project = "srcdeps-mvn-git-tag-quickstart";
        assertBuild(project, "srcdeps-test-artifact", "0.0.1-SRC-tag-0.0.1", "clean", "install");
        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project + "/" + project + "-jar";
        String dependentVersion = "1.0-SNAPSHOT";
        final String artifactPrefix = quickstartRepoDir + "/" + dependentVersion + "/" + project + "-jar-" + dependentVersion;
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".jar"));
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".pom"));
    }

    @Test
    public void mvnwGit() throws Exception {
        String project = "srcdeps-mvnw-git-quickstart";
        assertBuild(project, "srcdeps-test-artifact", "0.0.2-SRC-revision-dc21a1375bd5388b5489621e71dbe6e0e70db200", new String[] {".pom"}, "clean", "install");

        final String quickstartRepoDir = "org/srcdeps/mvn/quickstarts/" + project + "/" + project;
        String dependentVersion = "1.0-SNAPSHOT";
        final String artifactPrefix = quickstartRepoDir + "/" + dependentVersion + "/" + project + "-" + dependentVersion;
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".jar"));
        assertExists(mvnLocalRepo.resolve(artifactPrefix + ".pom"));
    }
}
