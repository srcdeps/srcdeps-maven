/**
 * Copyright 2015-2016 Maven Source Dependencies
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
package org.srcdeps.mvn;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public interface Constants {

    /** See the bin/mvn or bin/mvn.cmd script of your maven distro, where maven.multiModuleProjectDirectory is set */
    String MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY = "maven.multiModuleProjectDirectory";


    /** A system property for setting an encoding other than the default {@code utf-8} for reading the
     * {@code .mvn/srcdeps.yaml} file. */
    String SRCDEPS_ENCODING_PROPERTY = "srcdeps.encoding";

    Set<String> DEFAULT_FAIL_WITH_ANY_OF_ARGUMENTS = Collections
            .unmodifiableSet(new LinkedHashSet<>(Arrays.asList("release:prepare", "release:perform")));


}
