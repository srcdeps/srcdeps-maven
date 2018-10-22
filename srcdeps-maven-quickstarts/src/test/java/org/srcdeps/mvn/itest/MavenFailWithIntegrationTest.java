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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
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
public class MavenFailWithIntegrationTest extends AbstractMavenDepsIntegrationTest {
    @Rule
    public TestName testName = new TestName();


    public MavenFailWithIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws IOException, Exception {
        super(runtimeBuilder);
    }


    @Test
    public void mvnFailWithArgumentsBom() throws Exception {
        final String project = "srcdeps-mvn-git-bom-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "release:prepare");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and goal [release:prepare]")
                .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnFailWithArgumentsDependency() throws Exception {
        final String project = "srcdeps-mvn-git-revision-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));

        WrappedMavenExecutionResult result = build(project, "clean", "release:prepare");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b] and goal [release:prepare]")
                .assertLogText("BUILD FAILURE");

    }


    @Test
    public void mvnFailWithArgumentsParent() throws Exception {
        final String project = "srcdeps-mvn-git-parent-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "release:prepare");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and goal [release:prepare]")
                .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnFailWithArgumentsProfile() throws Exception {
        final String project = "srcdeps-mvn-git-profile-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Psrcdeps-profile");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and profile [srcdeps-profile]")
                .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnFailWithArgumentsPropertyCli() throws Exception {
        final String project = "srcdeps-mvn-git-bom-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Dsrcdeps-fail-property");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and property [srcdeps-fail-property]")
                .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnFailWithArgumentsPropertyCliOverride() throws Exception {
        final String project = "srcdeps-mvn-git-revision-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Dsrcdeps-fail-property-cli",
                "-Dsrcdeps.maven.failWith.properties=srcdeps-fail-property-cli");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact:0.0.1-SRC-revision-66ea95d890531f4eaaa5aa04a9b1c69b409dcd0b] and property [srcdeps-fail-property-cli]")
                .assertLogText("BUILD FAILURE");
    }


    @Test
    public void mvnFailWithArgumentsPropertyPom() throws Exception {
        final String project = "srcdeps-mvn-git-profile-quickstart";
        SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(groupId(project)));
        WrappedMavenExecutionResult result = build(project, "clean", "install", "-Psrcdeps-property-profile");
        result.assertLogText(
                "SrcdepsLocalRepositoryManager will decorate [" + TakariLocalRepositoryManagerFactory.class.getName() + "]") //
                .assertLogText(
                        "This build was configured to fail if there is a source dependency [org.l2x6.maven.srcdeps.itest:srcdeps-test-artifact-api:0.0.2-SRC-revision-3d00c2a91af593c01c9439cb16cb5f52d2ddbcf8] and property [srcdeps-fail-property]")
                .assertLogText("BUILD FAILURE");
    }

}
