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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.3.1" })
public class MavenMasterConfigIntegrationTest extends AbstractMavenDepsIntegrationTest {
    static final Logger log = LoggerFactory.getLogger(MavenDepsMavenIntegrationTest.class);

    @Rule
    public TestName testName = new TestName();

    public MavenMasterConfigIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws IOException, Exception {
        super(runtimeBuilder);
    }

    private static Map.Entry<String, String> prepareGitRepo(String artifactId, Path masterProjectDir,
            Path dependencySourceTrees) throws IOException, IllegalStateException, GitAPIException {
        final Path path = dependencySourceTrees.resolve(artifactId);
        Files.move(masterProjectDir.resolve("src/test/dependency-projects/" + artifactId), path);

        Map.Entry<String, String> result;
        try (Git git = Git.init().setDirectory(path.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            final RevCommit commit = git.commit().setMessage("Test commit to " + artifactId + " repo").call();
            final String gitUri = path.resolve(".git").toUri().toString();
            result = new AbstractMap.SimpleImmutableEntry<>(gitUri, commit.getId().name());
        }

        try (Git git = Git.open(path.toFile())) {
            ObjectId id = git.getRepository().resolve(Constants.HEAD);
        }

        return result;
    }

    @Test
    public void masterConfig() throws Exception {

        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo()
                .resolveGroup("org.srcdeps.mvn.quickstarts.srcdeps-mvn-git-master-config.hello"));
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo()
                .resolveGroup("org.srcdeps.mvn.quickstarts.srcdeps-mvn-git-master-config.hello-decorator"));

        final String project = "srcdeps-mvn-git-master-config";
        final Path masterProjectDir = resources.getBasedirPath(project);

        final Path dependencySourceTrees = TestUtils.createSourceTreesTestRunPath();
        final Entry<String, String> helloSourceTree = prepareGitRepo("srcdeps-mvn-git-master-config-hello",
                masterProjectDir, dependencySourceTrees);
        final Entry<String, String> helloDecoratorSourceTree = prepareGitRepo(
                "srcdeps-mvn-git-master-config-hello-decorator", masterProjectDir, dependencySourceTrees);

        final Path srcdepsYamlPath = masterProjectDir.resolve("srcdeps.yaml");
        String srcdepsYaml = new String(Files.readAllBytes(srcdepsYamlPath), StandardCharsets.UTF_8);
        srcdepsYaml = srcdepsYaml.replace("${hello.git.url}",
                "git:" + helloSourceTree.getKey());
        srcdepsYaml = srcdepsYaml.replace("${hello.git.revision}", helloSourceTree.getValue());
        srcdepsYaml = srcdepsYaml.replace("${hello-decorator.git.url}",
                "git:" + helloDecoratorSourceTree.getKey());
        srcdepsYaml = srcdepsYaml.replace("${hello-decorator.git.revision}", helloDecoratorSourceTree.getValue());
        Files.write(srcdepsYamlPath, srcdepsYaml.getBytes(StandardCharsets.UTF_8));

        WrappedMavenExecutionResult result = build(project, "clean", "test");
        result.assertLogText("SrcdepsLocalRepositoryManager will decorate ["
                + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText("BUILD SUCCESS");
    }
}
