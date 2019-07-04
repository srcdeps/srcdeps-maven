/**
 * Copyright 2015-2019 Maven Source Dependencies
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.srcdeps.core.Gavtc;
import org.srcdeps.core.MavenLocalRepository;
import org.srcdeps.core.fs.CannotAcquireLockException;
import org.srcdeps.core.fs.PathLocker;
import org.srcdeps.core.util.SrcdepsCoreUtils;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class TestUtils {
    private static final Path basedir = Paths.get(System.getProperty("basedir", new File("").getAbsolutePath()));
    private static final Path dependencySourceTreesPath;
    private static final String encoding = System.getProperty("project.build.sourceEncoding");
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    private static final MavenLocalRepository mvnLocalRepo;
    private static final Path mvnLocalRepoBasePath;
    private static final Path mvnLocalRepoPath;
    private static final String projectVersion = System.getProperty("project.version");
    private static final Path srcdepsBuildMetadataPath;
    private static final Path srcdepsQuickstartsPath;
    static {
        srcdepsQuickstartsPath = basedir.resolve("../srcdeps-maven-quickstarts").normalize();
        mvnLocalRepoBasePath = basedir.resolve("target/mvn-local-repo-base");
        mvnLocalRepoPath = basedir.resolve("target/mvn-local-repo");
        dependencySourceTreesPath = basedir.resolve("target/dependency-source-trees");
        srcdepsBuildMetadataPath = basedir.resolve("target/srcdeps/build-metadata");
        mvnLocalRepo = new MavenLocalRepository(mvnLocalRepoPath);
    }

    public static void assertExists(Path path) {
        Assert.assertTrue(String.format("File or directory does not exist [%s]", path.toString()), Files.exists(path));
    }

    public static void assertNotExists(Path path) {
        Assert.assertTrue(String.format("File or directory exists [%s], but should not", path.toString()),
                !Files.exists(path));
    }

    public static Path createSourceTreesTestRunPath() throws IOException {
        final String subdir = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-') + "-"
                + ((int) (Math.random() * 1000));
        final Path result = dependencySourceTreesPath.resolve(subdir);
        Files.createDirectories(result);
        return result;
    }

    /**
     * Deletes {@code target/srcdeps}. This tends to fail in some cases on Windows for which we do a dirty workaround of
     * FS locking the git repo that cannot be deleted, so that it is not re-used by the subsequent test.
     *
     * @throws IOException
     * @throws CannotAcquireLockException
     */
    public static void deleteSrcdepsDirectory() throws IOException, CannotAcquireLockException {
        final Path srcdepsPath = basedir.resolve("target/srcdeps");
        MavenDepsMavenIntegrationTest.log.warn("Deleting [{}]", srcdepsPath);
        try {
            SrcdepsCoreUtils.deleteDirectory(srcdepsPath);
        } catch (IOException e) {
            if (isWindows()) {
                final String msg = e.getMessage();
                String slash = File.separator;
                if ("\\".equals(slash)) {
                    slash = "\\\\";
                }
                final Matcher m = Pattern.compile("[^\\[]+\\[(.*" + slash + "\\d+)" + slash + "\\.git.*$").matcher(msg);
                if (m.matches()) {
                    final String path = m.group(1);
                    MavenDepsMavenIntegrationTest.log
                            .warn("Locking [" + path + "] as a workaround of not being able to remove it", e);
                    new PathLocker<Object>().lockDirectory("lock", Paths.get(path), new Object());
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
        }
    }

    public static Path getBasedir() {
        return basedir;
    }

    public static Path getDependencySourceTreesPath() {
        return dependencySourceTreesPath;
    }

    public static String getEncoding() {
        return encoding;
    }

    public static MavenLocalRepository getMvnLocalRepo() {
        return mvnLocalRepo;
    }

    public static Path getMvnLocalRepoBasePath() {
        return mvnLocalRepoBasePath;
    }

    public static Path getMvnLocalRepoPath() {
        return mvnLocalRepoPath;
    }

    public static String getProjectversion() {
        return projectVersion;
    }

    public static Path getSrcdepsBuildMetadataPath() {
        return srcdepsBuildMetadataPath;
    }

    public static Path getSrcdepsQuickstartsPath() {
        return srcdepsQuickstartsPath;
    }

    public static boolean isWindows() {
        return isWindows;
    }

    public static Manifest loadManifest(Gavtc gavtc) throws IOException {
        final Path path = getMvnLocalRepo().resolve(gavtc);
        try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(path))) {
            return jarStream.getManifest();
        }
    }

}
