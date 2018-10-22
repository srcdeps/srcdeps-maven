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
package org.srcdeps.mvn.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;

import org.srcdeps.config.yaml.YamlConfigurationReader;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ConfigurationException;
import org.srcdeps.core.config.ConfigurationLocator;
import org.srcdeps.core.config.Maven;
import org.srcdeps.core.config.tree.walk.DefaultsAndInheritanceVisitor;
import org.srcdeps.core.config.tree.walk.OverrideVisitor;
import org.srcdeps.mvn.Constants;

@Named
public class ConfigurationProducer {

    private final Configuration configuration;
    private final Path multimoduleProjectRootDirectory;

    public ConfigurationProducer() throws ConfigurationException {
        super();

        String basePathString = System.getProperty(Constants.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY);
        if (basePathString == null || basePathString.isEmpty()) {
            throw new RuntimeException(String.format("The system property %s must not be null or empty",
                    Constants.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY));
        }
        multimoduleProjectRootDirectory = Paths.get(basePathString).toAbsolutePath();

        this.configuration = new ConfigurationLocator(System.getProperties(), true) //
                .locate(multimoduleProjectRootDirectory, new YamlConfigurationReader()) //
                .accept(new OverrideVisitor(System.getProperties())) //
                .accept(new DefaultsAndInheritanceVisitor()) //
                .forwardPropertyValue(Maven.getSrcdepsMavenVersionProperty(), Constants.SRCDEPS_MAVEN_VERSION)
                .build();

    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * @return the root directory of the Maven multimodule project.
     */
    public Path getMultimoduleProjectRootDirectory() {
        return multimoduleProjectRootDirectory;
    }

}
