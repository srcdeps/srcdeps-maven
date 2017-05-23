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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.config.Maven;
import org.srcdeps.core.config.MavenAssertions;
import org.srcdeps.mvn.config.ConfigurationProducer;

/**
 * A {@link ProjectExecutionListener} that implements the {@code failWith} and {@code failWithout} srcdeps configuration
 * options. {@link SrcdepsEnforcer} checks the setup of the given Maven project and a given Maven execution request for
 * arguments present in {@code failWith} and {@code failWithout} configuration options.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@Named
@Singleton
public class SrcdepsEnforcer implements ProjectExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SrcdepsEnforcer.class);

    /**
     * Checks for failure triggering conditions in the given {@code goals}, {@code profiles} and {@code properties}.
     *
     * @param failWith
     *            the srcdeps configuration node
     * @param goals
     *            the goals of the current Maven invocation
     * @param profiles
     *            the profiles active in the current Maven invocation
     * @param properties
     *            the properties present the current Maven invocation
     * @return the failure triggering item
     */
    static String[] assertFailWith(MavenAssertions failWith, List<String> goals, List<String> profiles,
            Properties properties) {

        log.debug("Srcdeps Enforcer using failWith {}", failWith);
        final Set<String> failWithGoals = failWith.getGoals();
        for (String goal : goals) {
            if (failWithGoals.contains(goal)) {
                return new String[] { "goal", goal };
            }
        }

        final Set<String> failWithProfiles = failWith.getProfiles();
        for (String profile : profiles) {
            if (failWithProfiles.contains(profile)) {
                return new String[] { "profile", profile };
            }
        }

        final Set<String> failWithProperties = failWith.getProperties();
        for (String keyVal : failWithProperties) {
            final int eqPos = keyVal.indexOf('=');
            final String key;
            final String val;
            if (eqPos >= 0) {
                key = keyVal.substring(0, eqPos);
                val = keyVal.substring(eqPos + 1);
                if (val.equals(properties.get(key))) {
                    return new String[] { "property", keyVal };
                }
            } else {
                key = keyVal;
                if (properties.containsKey(key)) {
                    return new String[] { "property", keyVal };
                }
            }
        }

        return null;
    }

    /**
     * Checks for failure triggering conditions in the given {@code goals}, {@code profiles} and {@code properties}.
     *
     * @param failWithout
     *            the srcdeps configuration node
     * @param goals
     *            the goals of the current Maven invocation
     * @param profiles
     *            the profiles active in the current Maven invocation
     * @param properties
     *            the properties present the current Maven invocation
     * @return the failure triggering item
     */
    static String[] assertFailWithout(MavenAssertions failWithout, List<String> goals, List<String> profiles,
            Properties properties) {

        log.debug("Srcdeps Enforcer using failWithout {}", failWithout);
        final Set<String> failWithoutGoals = new LinkedHashSet<>(failWithout.getGoals());
        failWithoutGoals.removeAll(goals);
        if (!failWithoutGoals.isEmpty()) {
            return new String[] { "goals missing", failWithoutGoals.toString() };
        }

        final Set<String> failWithoutProfiles = new LinkedHashSet<>(failWithout.getProfiles());
        failWithoutProfiles.removeAll(profiles);
        if (!failWithoutProfiles.isEmpty()) {
            return new String[] { "profiles missing", failWithoutProfiles.toString() };
        }

        final Set<String> failWithoutProperties = new LinkedHashSet<>(failWithout.getProperties());
        for (Entry<Object, Object> prop : properties.entrySet()) {
            final String key = prop.getKey().toString();
            failWithoutProperties.remove(key);
            final Object val = prop.getValue();
            if (val != null) {
                failWithoutProperties.remove(key + "=" + val.toString());
            }
        }
        if (!failWithoutProperties.isEmpty()) {
            return new String[] { "properties missing", failWithoutProperties.toString() };
        }

        return null;
    }

    private static void assertNotSrcdeps(List<Dependency> deps, String[] violation) throws LifecycleExecutionException {
        for (Dependency dep : deps) {
            assertNotSrcdeps(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), violation);
        }
    }

    private static void assertNotSrcdeps(String group, String artifact, String version, String[] violation)
            throws LifecycleExecutionException {
        if (SrcVersion.isSrcVersion(version)) {
            throw new LifecycleExecutionException(String.format(
                    "This build was configured to fail if there is a source dependency [%s:%s:%s] and %s [%s]", group,
                    artifact, version, violation[0], violation[1]));
        }
    }

    private final ConfigurationProducer configurationProducer;

    @Inject
    public SrcdepsEnforcer(ConfigurationProducer configurationProducer) {
        super();
        this.configurationProducer = configurationProducer;
    }

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent event) {
    }

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent event) throws LifecycleExecutionException {
    }

    @Override
    public void beforeProjectExecution(ProjectExecutionEvent event) throws LifecycleExecutionException {
    }

    @Override
    public void beforeProjectLifecycleExecution(ProjectExecutionEvent event) throws LifecycleExecutionException {
        final MavenProject project = event.getProject();
        log.info("srcdeps enforcer checks for violations in {}:{}", project.getGroupId(), project.getArtifactId());

        final Maven maven = configurationProducer.getConfiguration().getMaven();

        final List<MojoExecution> mojoExecutions = event.getExecutionPlan();
        final List<String> goals = new ArrayList<>(mojoExecutions.size());
        for (MojoExecution mojoExecution : mojoExecutions) {
            MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
            goals.add(mojoDescriptor.getFullGoalName());
            goals.add(mojoDescriptor.getGoal());
        }

        final List<String> profiles = new ArrayList<>();
        final List<Profile> activeProfiles = project.getActiveProfiles();
        for (Profile profile : activeProfiles) {
            final String id = profile.getId();
            profiles.add(id);
        }

        final Properties props = new Properties();
        props.putAll(project.getProperties());
        props.putAll(System.getProperties());

        String[] firstViolation = assertFailWithout(maven.getFailWithout(), goals, profiles, props);
        if (firstViolation == null) {
            firstViolation = assertFailWith(maven.getFailWith(), goals, profiles, props);
        }
        if (firstViolation != null) {
            /* check if there are srcdeps */
            Artifact parent = project.getParentArtifact();
            if (parent != null) {
                assertNotSrcdeps(parent.getGroupId(), parent.getArtifactId(), parent.getVersion(), firstViolation);
            }
            DependencyManagement dm;
            List<Dependency> deps;
            if ((dm = project.getDependencyManagement()) != null && (deps = dm.getDependencies()) != null) {
                assertNotSrcdeps(deps, firstViolation);
            }
            if ((deps = project.getDependencies()) != null) {
                assertNotSrcdeps(deps, firstViolation);
            }
        }
    }

}
