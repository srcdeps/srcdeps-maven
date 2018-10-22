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
package org.srcdeps.mvn.itest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.runner.Description;

import io.takari.maven.testing.TestResources;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class SrcdepsTestResources extends TestResources {
    private static final Pattern replacementPattern = Pattern
            .compile(Pattern.quote("<version>") + "[^<]+" + Pattern.quote("</version><!-- @srcdeps.version@ -->"));

    private Path basedir;
    private String project;

    public SrcdepsTestResources() throws IOException {
        super(TestUtils.getSrcdepsQuickstartsPath().toString(), "target/test-projects");
    }

    @Override
    public File getBasedir(String project) throws IOException {
        if (this.project != null && !this.project.equals(project)) {
            throw new IllegalStateException(
                    "org.srcdeps.mvn.itest.SrcdepsTestResources.getBasedir(String) can be called once only");
        }
        this.project = project;
        final File result = super.getBasedir(project);

        final Path extensionsXmlPath = result.toPath().resolve(".mvn/extensions.xml");

        final String extensionsXmlContent = new String(Files.readAllBytes(extensionsXmlPath), TestUtils.getEncoding());

        final String newContent = replacementPattern.matcher(extensionsXmlContent)
                .replaceAll("<version>" + TestUtils.getProjectversion() + "</version>");

        Assert.assertNotEquals(newContent, extensionsXmlContent);

        Files.write(extensionsXmlPath, newContent.getBytes(TestUtils.getEncoding()));

        this.basedir = result.toPath();
        return result;
    }

    public Path getBasedirPath() {
        return basedir;
    }

    public Path getBasedirPath(String project) throws IOException {
        if (this.project == null) {
            return getBasedir(project).toPath();
        } else {
            return basedir;
        }
    }

    public void reset() {
        this.project = null;
        this.basedir = null;
    }

    @Override
    protected void starting(Description d) {
        super.starting(d);
        reset();
    }
}
