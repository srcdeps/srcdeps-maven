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
package org.srcdeps.mvn.enforcer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.srcdeps.core.config.MavenAssertions;

public class SrcdepsEnforcerTest {

    /**
     * Checks that {@link SrcdepsEnforcer#assertFailWith(MavenAssertions, java.util.List, java.util.List, Properties)}
     * works properly.
     */
    @Test
    public void assertFailWith() {
        Assert.assertArrayEquals( //
                new String[] { "goal", "g1" }, //
                SrcdepsEnforcer.assertFailWith( //
                        MavenAssertions.failWithBuilder() //
                                .addDefaults(false) //
                                .goal("g1").goal("g2") //
                                .build(), //
                        Arrays.asList("g1"), //
                        Collections.<String>emptyList(), //
                        new Properties() //
                ) //
        );

        Assert.assertArrayEquals( //
                new String[] { "profile", "p2" }, //
                SrcdepsEnforcer.assertFailWith( //
                        MavenAssertions.failWithBuilder() //
                                .addDefaults(false) //
                                .profile("p2") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertArrayEquals( //
                new String[] { "property", "key1" }, //
                SrcdepsEnforcer.assertFailWith( //
                        MavenAssertions.failWithBuilder() //
                                .addDefaults(false) //
                                .property("key1") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertArrayEquals( //
                new String[] { "property", "key1=val1" }, //
                SrcdepsEnforcer.assertFailWith( //
                        MavenAssertions.failWithBuilder() //
                                .addDefaults(false) //
                                .property("key1=val1") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertNull( //
                SrcdepsEnforcer.assertFailWith( //
                        MavenAssertions.failWithBuilder() //
                                .addDefaults(false) //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertNull( //
                SrcdepsEnforcer.assertFailWith( //
                        MavenAssertions.failWithBuilder() //
                                .addDefaults(false) //
                                .goal("g3") //
                                .profile("p3") //
                                .property("key3") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

    }

    /**
     * Checks that {@link SrcdepsEnforcer#assertFailWithout(MavenAssertions, java.util.List, java.util.List, Properties)
     * works properly.
     */
    @Test
    public void assertFailWithout() {
        Assert.assertArrayEquals( //
                new String[] { "goals missing", "[g2]" }, //
                SrcdepsEnforcer.assertFailWithout( //
                        MavenAssertions.failWithoutBuilder() //
                                .goal("g1").goal("g2") //
                                .build(), //
                        Arrays.asList("g1"), //
                        Collections.<String>emptyList(), //
                        new Properties() //
                ) //
        );

        Assert.assertArrayEquals( //
                new String[] { "profiles missing", "[p3, p4]" }, //
                SrcdepsEnforcer.assertFailWithout( //
                        MavenAssertions.failWithoutBuilder() //
                                .profile("p3").profile("p4") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertArrayEquals( //
                new String[] { "properties missing", "[key3]" }, //
                SrcdepsEnforcer.assertFailWithout( //
                        MavenAssertions.failWithoutBuilder() //
                                .property("key3") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertArrayEquals( //
                new String[] { "properties missing", "[key3=val3]" }, //
                SrcdepsEnforcer.assertFailWithout( //
                        MavenAssertions.failWithoutBuilder() //
                                .property("key3=val3") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertNull( //
                SrcdepsEnforcer.assertFailWithout( //
                        MavenAssertions.failWithoutBuilder() //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );

        Assert.assertNull( //
                SrcdepsEnforcer.assertFailWithout( //
                        MavenAssertions.failWithoutBuilder() //
                                .goal("g1") //
                                .profile("p1") //
                                .property("key1") //
                                .build(), //
                        Arrays.asList("g1", "g2"), //
                        Arrays.asList("p1", "p2"), //
                        new Properties() {
                            {
                                put("key1", "val1");
                                put("key2", "val2");
                            }
                        } //
                ) //
        );
    }
}
