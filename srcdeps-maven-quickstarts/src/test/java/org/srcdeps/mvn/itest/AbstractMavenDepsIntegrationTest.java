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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.config.Maven;
import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

public abstract class AbstractMavenDepsIntegrationTest {
    private static final long DELETE_RETRY_MILLIS = 5000L;
    private static final Logger log = LoggerFactory.getLogger(AbstractMavenDepsIntegrationTest.class);
    protected static final String mrmSettingsXmlPath = System.getProperty("mrm.settings.xml");
    protected static final String QUICKSTART_GROUPID = "org.srcdeps.mvn.quickstarts";
    protected static final String QUICKSTART_VERSION = "1.0-SNAPSHOT";

    @BeforeClass
    public static void beforeClass() throws IOException {
        final Path mvnLocalRepoPath = TestUtils.getMvnLocalRepo().getRootDirectory();
        final Path takariLocalRepoDir = mvnLocalRepoPath.resolve("io/takari");
        if (Files.exists(mvnLocalRepoPath)) {
            try (DirectoryStream<Path> subPaths = Files.newDirectoryStream(mvnLocalRepoPath)) {
                for (Path subPath : subPaths) {
                    if (Files.isDirectory(subPath)) {
                        Files.walkFileTree(subPath, new SimpleFileVisitor<Path>() {

                            @Override
                            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                                if (exc == null) {
                                    final FileVisitResult result;
                                    if (!takariLocalRepoDir.startsWith(d)) {
                                        Files.delete(d);
                                        result = FileVisitResult.CONTINUE;
                                    } else if (takariLocalRepoDir.equals(d)) {
                                        result = FileVisitResult.SKIP_SUBTREE;
                                    } else {
                                        result = FileVisitResult.CONTINUE;
                                    }
                                    return result;
                                } else {
                                    // directory iteration failed; propagate exception
                                    throw exc;
                                }
                            }

                            @Override
                            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs)
                                    throws IOException {
                                final FileVisitResult result = d.equals(takariLocalRepoDir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                                return result ;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                if (TestUtils.isWindows()) {
                                    final long deadline = System.currentTimeMillis() + DELETE_RETRY_MILLIS;
                                    FileSystemException lastException = null;
                                    do {
                                        try {
                                            Files.delete(file);
                                            return FileVisitResult.CONTINUE;
                                        } catch (FileSystemException e) {
                                            lastException = e;
                                        }
                                    } while (System.currentTimeMillis() < deadline);
                                    throw new IOException(String.format("Could not delete file [%s] after retrying for %d ms", file,
                                            DELETE_RETRY_MILLIS), lastException);
                                } else {
                                    Files.delete(file);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                                // try to delete the file anyway, even if its attributes
                                // could not be read, since delete-only access is
                                // theoretically possible
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        Files.delete(subPath);
                    }
                }
            }
        } else {
            SrcdepsCoreUtils.ensureDirectoryExists(mvnLocalRepoPath);
        }

        SrcdepsCoreUtils.copyDirectory(TestUtils.getMvnLocalRepoBasePath(), mvnLocalRepoPath);

        System.setProperty(Maven.getSrcdepsMavenSettingsProperty(), mrmSettingsXmlPath);

        Assert.assertTrue("[" + mrmSettingsXmlPath + "] should exist", Files.exists(Paths.get(mrmSettingsXmlPath)));
        Assert.assertNotNull("project.build.sourceEncoding property must be set", TestUtils.getEncoding());
        Assert.assertNotNull("project.version property must be set", TestUtils.getProjectversion());
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
    public final SrcdepsTestResources resources = new SrcdepsTestResources();

    @Rule
    public TestName testName = new TestName() {

        @Override
        protected void starting(Description d) {
            super.starting(d);
            log.info("Running test {}.{}", AbstractMavenDepsIntegrationTest.this.getClass().getSimpleName(), getMethodName());
        }

    };

    public final MavenRuntime verifier;
    public AbstractMavenDepsIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws IOException, Exception {
        this.verifier = runtimeBuilder.withExtension(new File("target/classes").getCanonicalFile()).build();
    }

    protected WrappedMavenExecutionResult assertBuild(String project, String[] gavtcPatternsExpectedToExist,
            String[] gavtcPatternsExpectedNotToExist, String... goals) throws Exception {

        LocalMavenRepoVerifier repoVerifier = new LocalMavenRepoVerifier(project, gavtcPatternsExpectedToExist, gavtcPatternsExpectedNotToExist);
        repoVerifier.clean();

        WrappedMavenExecutionResult result = build(project, goals);
        result //
                .assertErrorFreeLog() //
                .assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                        + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
        ;

        repoVerifier.verify();

        return result;
    }

    protected WrappedMavenExecutionResult build(String project, String... goals) throws Exception {
        log.info("Building test project [{}]", project);

        final String quickstartRepoDir = "org/l2x6/srcdeps/quickstarts/" + project;
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepoPath().resolve(quickstartRepoDir));

        MavenExecution execution = verifier.forProject(resources.getBasedirPath(project).toFile()) //
                //.withCliOption("-X") //
                .withCliOption("-B") // batch
                .withCliOption("-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn") //
                .withCliOptions("-Dmaven.repo.local=" + TestUtils.getMvnLocalRepo().getRootDirectory().toAbsolutePath().toString()) //
                .withCliOption("-s").withCliOption(mrmSettingsXmlPath);
        return new WrappedMavenExecutionResult(execution.execute(goals));
    }
}
