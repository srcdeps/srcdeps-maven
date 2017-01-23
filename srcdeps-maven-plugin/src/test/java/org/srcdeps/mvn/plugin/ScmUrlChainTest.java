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
package org.srcdeps.mvn.plugin;

import org.junit.Assert;
import org.junit.Test;
import org.srcdeps.mvn.plugin.SrcdepsInitMojo.ScmRepositoryIndex.ScmUrlAncestry;

public class ScmUrlChainTest {

    @Test
    public void chain() {
        ScmUrlAncestry chain = ScmUrlAncestry.builder() //
                .element(
                        "scm:git:git://git@github.com:arquillian/arquillian-extension-rest.git/arquillian-rest-warp-parent/arquillian-rest-warp-api",
                        new Ga("org.jboss.arquillian.extension", "arquillian-rest-warp-api"))
                .element(
                        "scm:git:git://git@github.com:arquillian/arquillian-extension-rest.git/arquillian-rest-warp-parent",
                        new Ga("org.jboss.arquillian.extension", "arquillian-rest-warp-parent"))
                .build();

        Assert.assertTrue(chain.hasUrl());
        Assert.assertEquals(2, chain.getLength());
        Assert.assertEquals("git:git://git@github.com:arquillian/arquillian-extension-rest.git", chain.getUrl());
    }
}
