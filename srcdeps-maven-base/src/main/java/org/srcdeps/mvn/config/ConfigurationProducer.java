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
package org.srcdeps.mvn.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.config.yaml.YamlConfigurationIo;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ConfigurationException;
import org.srcdeps.core.config.ScmRepositoryFinder;
import org.srcdeps.core.config.tree.walk.DefaultsAndInheritanceVisitor;
import org.srcdeps.core.config.tree.walk.OverrideVisitor;
import org.srcdeps.mvn.Constants;

@Named
public class ConfigurationProducer {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationProducer.class);

    /** Since 3.1.0 this is the default location of srcdeps.yaml file */
    private static final Path SRCDEPS_YAML_PATH = Paths.get("srcdeps.yaml");

    /** Before 3.1.0 this used to be the default location of srcdeps.yaml file */
    private static final Path MVN_SRCDEPS_YAML_PATH = Paths.get(".mvn", "srcdeps.yaml");

    private final Configuration configuration;
    private final Path configurationLocation;
    private final ScmRepositoryFinder repositoryFinder;

    public ConfigurationProducer() {
        super();

        String basePathString = System.getProperty(Constants.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY);
        if (basePathString == null || basePathString.isEmpty()) {
            throw new RuntimeException(String.format("The system property %s must not be null or empty",
                    Constants.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY));
        }
        final Path basePath = Paths.get(basePathString).toAbsolutePath();
        final Path defaultSrcdepsYamlPath = basePath.resolve(SRCDEPS_YAML_PATH);
        final Path legacySrcdepsYamlPath = basePath.resolve(MVN_SRCDEPS_YAML_PATH);
        Path srcdepsYamlPath = defaultSrcdepsYamlPath;
        if (!Files.exists(srcdepsYamlPath)) {
            srcdepsYamlPath = legacySrcdepsYamlPath;
        }

        this.configurationLocation = srcdepsYamlPath;

        final Configuration.Builder configBuilder;
        if (Files.exists(srcdepsYamlPath)) {
            log.debug("SrcdepsLocalRepositoryManager using configuration {}", configurationLocation);
            final String encoding = System.getProperty(Configuration.getSrcdepsEncodingProperty(), "utf-8");
            final Charset cs = Charset.forName(encoding);
            try (Reader r = Files.newBufferedReader(configurationLocation, cs)) {
                configBuilder = new YamlConfigurationIo().read(r);
            } catch (IOException | ConfigurationException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.warn("Could not locate srcdeps configuration at neither {} nor {}, defaulting to an empty configuration",
                    defaultSrcdepsYamlPath, legacySrcdepsYamlPath);
            configBuilder = Configuration.builder();
        }

        this.configuration = configBuilder //
                .accept(new OverrideVisitor(System.getProperties())) //
                .accept(new DefaultsAndInheritanceVisitor()) //
                .build();

        this.repositoryFinder = new ScmRepositoryFinder(this.configuration);

    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Path getConfigurationLocation() {
        return configurationLocation;
    }

    public ScmRepositoryFinder getRepositoryFinder() {
        return repositoryFinder;
    }
}
