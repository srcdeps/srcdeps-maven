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
package org.srcdeps.quickstarts.mvn.git.revision.gradle;

public class GreetingApp {
    private final org.srcdeps.test.gradle.api.Greeter greeter = new org.srcdeps.test.gradle.impl.HelloGreeter();

    public org.srcdeps.test.gradle.api.Greeter getGreeter() {
        return greeter;
    }
}
