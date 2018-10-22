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
import java.util.ArrayList;
import java.util.List;

import org.srcdeps.core.Gavtc;
import org.srcdeps.core.util.SrcdepsCoreUtils;

public class LocalMavenRepoVerifier {
    private final List<Gavtc> expectedNotToExist;
    private final List<Gavtc> expectedToExist;
    private final String project;

    protected LocalMavenRepoVerifier(String project, String[] gavtcPatternsExpectedToExist) {
        this(project, gavtcPatternsExpectedToExist, new String[0]);
    }
    protected LocalMavenRepoVerifier(String project, String[] gavtcPatternsExpectedToExist,
            String[] gavtcPatternsExpectedNotToExist) {
        this.project = project;
        /* delete all expected and unexpected groups */
        this.expectedToExist = new ArrayList<>();
        for (String gavtcPattern : gavtcPatternsExpectedToExist) {
            for (Gavtc gavtc : Gavtc.ofPattern(gavtcPattern)) {
                expectedToExist.add(gavtc);
            }
        }
        this.expectedNotToExist = new ArrayList<>();
        for (String gavtcPattern : gavtcPatternsExpectedNotToExist) {
            for (Gavtc gavtc : Gavtc.ofPattern(gavtcPattern)) {
                expectedNotToExist.add(gavtc);
            }
        }
    }
    public void clean() throws IOException {
        for (Gavtc gavtc : expectedToExist) {
            SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(gavtc.getGroupId()));
        }
        for (Gavtc gavtc : expectedNotToExist) {
            SrcdepsCoreUtils.deleteDirectory(TestUtils.getMvnLocalRepo().resolveGroup(gavtc.getGroupId()));
        }
    }

    public void verify() {
        for (Gavtc gavtc : expectedToExist) {
            TestUtils.assertExists(TestUtils.getMvnLocalRepo().resolve(gavtc));
        }
        for (Gavtc gavtc : expectedNotToExist) {
            TestUtils.assertNotExists(TestUtils.getMvnLocalRepo().resolve(gavtc));
        }
    }

}