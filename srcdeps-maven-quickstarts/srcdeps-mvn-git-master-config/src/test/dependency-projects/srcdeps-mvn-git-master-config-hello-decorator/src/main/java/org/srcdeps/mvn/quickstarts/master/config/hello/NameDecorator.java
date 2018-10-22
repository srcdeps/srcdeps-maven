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
package org.srcdeps.mvn.quickstarts.master.config.hello;

import org.srcdeps.mvn.quickstarts.master.config.hello.decorator.Greeter;

public class NameDecorator implements Greeter {

    private final Greeter delegate;
    private final String name;

    public NameDecorator(Greeter delegate, String name) {
        super();
        this.delegate = delegate;
        this.name = name;
    }

    public String greet() {
        return delegate.greet() + " " + name;
    }
}
