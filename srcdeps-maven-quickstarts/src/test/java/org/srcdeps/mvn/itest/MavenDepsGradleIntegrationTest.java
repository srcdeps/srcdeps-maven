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

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.3.1" })
public class MavenDepsGradleIntegrationTest extends AbstractMavenDepsIntegrationTest {

    private static final String ORG_SRCDEPS_ITEST_GRADLE_GROUPID = "org.srcdeps.test.gradle";

    public MavenDepsGradleIntegrationTest(MavenRuntimeBuilder runtimeBuilder) throws IOException, Exception {
        super(runtimeBuilder);
    }

    @Test
    public void mvnGitGradleSourceDependency() throws Exception {
        final String project = "srcdeps-mvn-git-gradle-source-dependency-quickstart";
        final String srcVersion = "1.0-SRC-revision-e63539236a94e8f6c2d720f8bda0323d1ce4db0f";

        String[] expectedGavtcs = new String[] { //
                pom(groupId(project), project, QUICKSTART_VERSION), //
                pomJar(groupId(project), project + "-jar", QUICKSTART_VERSION), //
                pomJar(ORG_SRCDEPS_ITEST_GRADLE_GROUPID, "srcdeps-test-artifact-gradle-api", srcVersion), //
                pomJar(ORG_SRCDEPS_ITEST_GRADLE_GROUPID, "srcdeps-test-artifact-gradle-impl", srcVersion) //
        };
        assertBuild(project, expectedGavtcs, new String[] {}, "clean", "install");
    }

}
