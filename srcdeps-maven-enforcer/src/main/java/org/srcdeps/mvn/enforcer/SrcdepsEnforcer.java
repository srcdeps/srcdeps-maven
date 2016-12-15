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
package org.srcdeps.mvn.enforcer;

import java.util.List;
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
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.core.SrcVersion;
import org.srcdeps.core.config.MavenFailWith;
import org.srcdeps.mvn.config.ConfigurationProducer;

/**
 * A {@link ProjectExecutionListener} that implements the {@code failWithAnyOfArguments} srcdeps configuration option.
 * {@link SrcdepsEnforcer} checks the setup of the given Maven project and a given Maven execution request for arguments
 * present in {@code failWithAnyOfArguments} configuration option. Here, the interpretation of "arguments" is rather
 * loose. Not only arguments specified on via CLI (such as plugin mojos, profiles and properties) are checked but also
 * their counterparts defined in pom.xml files.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@Named
@Singleton
public class SrcdepsEnforcer implements ProjectExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SrcdepsEnforcer.class);

    private static void assertNotSrcdeps(List<Dependency> deps, String[] violation)
            throws LifecycleExecutionException {
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
        String[] firstViolation = findFirstViolation(event.getExecutionPlan(), project.getActiveProfiles(),
                project.getProperties());
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

    /**
     * Goes through the given {@code mojoExecutions}, {@code profiles} and {@code properties} to find some failure
     * triggering argument in them.
     *
     * @param mojoExecutions
     * @param profiles
     * @param properties
     * @return the failure triggering argument as a {@link String}
     */
    private String[] findFirstViolation(List<MojoExecution> mojoExecutions, List<Profile> profiles,
            Properties properties) {
        final MavenFailWith failWith = configurationProducer.getConfiguration().getMaven().getFailWith();
        log.debug("Srcdeps Enforcer using failWith {}", failWith);
        final Set<String> failWithGoals = failWith.getGoals();
        String[] firstViolation = null;
        for (MojoExecution mojoExecution : mojoExecutions) {
            MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
            final String fullGoalName = mojoDescriptor.getFullGoalName();
            if (failWithGoals.contains(fullGoalName)) {
                firstViolation = new String[] {"goal", fullGoalName};
                break;
            } else {
                final String goal = mojoDescriptor.getGoal();
                PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();
                final String fqMojo = pluginDescriptor.getGroupId() + ":" + pluginDescriptor.getArtifactId() + ":"
                        + goal;
                if (failWithGoals.contains(fqMojo)) {
                    firstViolation = new String[] {"goal", fqMojo};
                    break;
                }
            }
        }

        if (firstViolation == null) {
            final Set<String> failWithProfiles = failWith.getProfiles();
            for (Profile profile : profiles) {
                final String id = profile.getId();
                if (failWithProfiles.contains(id)) {
                    firstViolation = new String[] {"profile", id};
                }
            }
        }

        if (firstViolation == null) {
            final Set<String> failWithProperties = failWith.getProperties();
            Properties sysProps = System.getProperties();
            for (String keyVal : failWithProperties) {
                final int eqPos = keyVal.indexOf('=');
                final String key;
                final String val;
                if (eqPos >= 0) {
                    key = keyVal.substring(0, eqPos);
                    val = keyVal.substring(eqPos + 1);
                    if (val.equals(properties.get(key)) || val.equals(sysProps.get(key))) {
                        firstViolation = new String[] {"property", keyVal};
                        break;
                    }
                } else {
                    key = keyVal;
                    if (properties.containsKey(key) || sysProps.containsKey(key)) {
                        firstViolation = new String[] {"property", keyVal};
                        break;
                    }
                }
            }
        }

        return firstViolation;

    }

}
