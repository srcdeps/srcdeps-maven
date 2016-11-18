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
package org.srcdeps.mvn.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.srcdeps.config.yaml.YamlConfigurationIo;
import org.srcdeps.core.config.Configuration;
import org.srcdeps.core.config.ConfigurationException;
import org.srcdeps.mvn.Constants;

@Named
@Singleton
public class ConfigurationProducer {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationProducer.class);

    public static final Path relativeMvnSrcdepsYaml = Paths.get(".mvn", "srcdeps.yaml");
    private final Configuration configuration;
    private final Path configurationLocation;

    public ConfigurationProducer() {
        super();


        String basePathString = System.getProperty(Constants.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY);
        if (basePathString == null || basePathString.isEmpty()) {
            throw new RuntimeException(String.format("The system property %s must not be null or empty",
                    Constants.MAVEN_MULTI_MODULE_PROJECT_DIRECTORY_PROPERTY));
        }
        final Path basePath = Paths.get(basePathString).toAbsolutePath();
        final Path srcdepsYamlPath = basePath.resolve(relativeMvnSrcdepsYaml);
        if (Files.exists(srcdepsYamlPath)) {
            this.configurationLocation = srcdepsYamlPath;
            log.debug("SrcdepsLocalRepositoryManager using configuration {}", configurationLocation);
        } else {
            throw new RuntimeException(
                    String.format("Could not locate srcdeps configuration at [%s]", srcdepsYamlPath));
        }

        final String encoding = System.getProperty(Constants.SRCDEPS_ENCODING_PROPERTY, "utf-8");
        final Charset cs = Charset.forName(encoding);
        try (Reader r = Files.newBufferedReader(configurationLocation, cs)) {
            this.configuration = new YamlConfigurationIo().read(r);
        } catch (IOException | ConfigurationException e) {
            throw new RuntimeException(e);
        }

    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Path getConfigurationLocation() {
        return configurationLocation;
    }
}
