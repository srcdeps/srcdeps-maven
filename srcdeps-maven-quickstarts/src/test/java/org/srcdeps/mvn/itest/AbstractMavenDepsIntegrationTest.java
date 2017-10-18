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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.Gavtc;
import org.srcdeps.core.MavenLocalRepository;
import org.srcdeps.core.config.Maven;
import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

public abstract class AbstractMavenDepsIntegrationTest {
    protected static final Path basedir = Paths.get(System.getProperty("basedir", new File("").getAbsolutePath()));
    protected static final String encoding = System.getProperty("project.build.sourceEncoding");
    private static final Logger log = LoggerFactory.getLogger(AbstractMavenDepsIntegrationTest.class);
    protected static final String mrmSettingsXmlPath = System.getProperty("mrm.settings.xml");
    protected static final MavenLocalRepository mvnLocalRepo;
    protected static final Path mvnLocalRepoPath;
    protected static final String projectVersion = System.getProperty("project.version");
    protected static final String QUICKSTART_GROUPID = "org.srcdeps.mvn.quickstarts";
    protected static final String QUICKSTART_VERSION = "1.0-SNAPSHOT";
    protected static final Pattern replacementPattern = Pattern
            .compile(Pattern.quote("<version>") + "[^<]+" + Pattern.quote("</version><!-- @srcdeps.version@ -->"));
    protected static final Path srcdepsQuickstartsPath;

    static {
        srcdepsQuickstartsPath = basedir.resolve("../srcdeps-maven-quickstarts").normalize();
        mvnLocalRepoPath = basedir.resolve("target/mvn-local-repo");
        mvnLocalRepo = new MavenLocalRepository(mvnLocalRepoPath);
    }

    public static void assertExists(Path path) {
        Assert.assertTrue(String.format("File or directory does not exist [%s]", path.toString()), Files.exists(path));
    }

    public static void assertNotExists(Path path) {
        Assert.assertTrue(String.format("File or directory exists [%s], but should not", path.toString()),
                !Files.exists(path));
    }

    @BeforeClass
    public static void beforeClass() throws IOException {

        SrcdepsCoreUtils.ensureDirectoryExistsAndEmpty(mvnLocalRepo.getRootDirectory());

        System.setProperty(Maven.getSrcdepsMavenSettingsProperty(), mrmSettingsXmlPath);

        Assert.assertTrue("[" + mrmSettingsXmlPath + "] should exist", Files.exists(Paths.get(mrmSettingsXmlPath)));
        Assert.assertNotNull("project.build.sourceEncoding property must be set", encoding);
        Assert.assertNotNull("project.version property must be set", projectVersion);
    }

    protected static String groupId(String project) {
        return QUICKSTART_GROUPID + "." + project;
    }

    protected static String pom(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version + ":pom";
    }

    protected static String pomJar(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version + ":[pom,jar]";
    }

    @Rule
    public final TestResources resources = new TestResources(srcdepsQuickstartsPath.toString(),
            "target/test-projects") {

        @Override
        public File getBasedir(String project) throws IOException {

            File result = super.getBasedir(project);

            Path extensionsXmlPath = result.toPath().resolve(".mvn/extensions.xml");

            String extensionsXmlContent = new String(Files.readAllBytes(extensionsXmlPath), encoding);

            String newContent = replacementPattern.matcher(extensionsXmlContent)
                    .replaceAll("<version>" + projectVersion + "</version>");

            Assert.assertNotEquals(newContent, extensionsXmlContent);

            Files.write(extensionsXmlPath, newContent.getBytes(encoding));

            return result;
        }

    };

    public final MavenRuntime verifier;

    public AbstractMavenDepsIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws IOException, Exception {
        this.verifier = runtimeBuilder.withExtension(new File("target/classes").getCanonicalFile()).build();
    }

    protected MavenExecutionResult assertBuild(String project, String[] gavtcPatternsExpectedToExist,
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
}
