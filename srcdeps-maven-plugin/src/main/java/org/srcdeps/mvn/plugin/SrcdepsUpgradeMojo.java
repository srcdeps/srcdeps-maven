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
package org.srcdeps.mvn.plugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.srcdeps.mvn.Constants;
import org.w3c.dom.Document;

/**
 * Adds and/or upgrades the srcdeps core extensions in {@code .mvn/extensions.xml} file. If the
 * {@code .mvn/extensions.xml} does not exist, it creates it from scratch. If the {@code .mvn/extensions.xml} exists, it
 * adds missing srcdeps core extensions and ensures they have the version specified by {@link #newVersion} parameter.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@SuppressWarnings("deprecation")
@Mojo(name = "up", defaultPhase = LifecyclePhase.NONE, threadSafe = false, requiresProject = true, requiresDependencyResolution = ResolutionScope.NONE)
public class SrcdepsUpgradeMojo extends AbstractMojo {
    protected final File multiModuleRootDir = new File(System.getProperty("maven.multiModuleProjectDirectory"));
    protected final Path mvnDir;
    protected final Path extensionsXmlPath;

    /** The encoding to use when reading and writing {@code srcdeps.yaml} and {@code extensions.xml} files */
    @Parameter(defaultValue = "utf-8", property = "srcdeps.encoding")
    protected String encoding;

    public SrcdepsUpgradeMojo() {
        super();
        this.mvnDir = multiModuleRootDir.toPath().resolve(".mvn");
        this.extensionsXmlPath = mvnDir.resolve("extensions.xml");
    }

    /**
     * The version of srcdeps core extensions to add or upgarde to in {@code extensions.xml} file. Default is the
     * version if the {@code srcdeps-maven-plugin} that runs this mojo.
     */
    @Parameter(property = "srcdeps.newVersion")
    protected String newVersion;

    /** The limit for the word wrap in {@code extensions.xml} file */
    @Parameter(defaultValue = "128", property = "srcdeps.xmlLineLength")
    protected int xmlLineLength;

    /** The number of spaces to use for one level of indentation in {@code extensions.xml} file */
    @Parameter(defaultValue = "2", property = "srcdeps.xmlIndentSize")
    protected int xmlIndentSize;

    /**
     * If {@code true} the {@code extensions.xml} file will be indented; otherwise the {@code extensions.xml} file will
     * not be indented.
     */
    @Parameter(defaultValue = "true", property = "srcdeps.xmlIndent")
    protected boolean xmlIndent;

    /** If {@code true} the execution of this mojo will be skipped altogether; otherwise this mojo will be executed. */
    @Parameter(defaultValue = "false", property = "srcdeps.skip")
    protected boolean skip;

    @Parameter(defaultValue = "${session}")
    protected MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        if (skip || !multiModuleRootDir.equals(session.getCurrentProject().getBasedir())) {
            log.info("srcdeps: ["+ getClass().getSimpleName() + "] skipped");
        } else {

            if (newVersion == null || newVersion.isEmpty()) {
                newVersion = Constants.SRCDEPS_MAVEN_VERSION;
            }

            try {
                final Document extensionsXmlDoc;
                if (Files.exists(extensionsXmlPath)) {

                    try (Reader in = Files.newBufferedReader(extensionsXmlPath, Charset.forName(encoding))) {
                        DOMResult domResult = new DOMResult();
                        TransformerFactory.newInstance().newTransformer().transform(new StreamSource(in), domResult);
                        extensionsXmlDoc = (Document) domResult.getNode();
                    } catch (TransformerException | TransformerFactoryConfigurationError e) {
                        throw new MojoExecutionException(
                                String.format("Could parse [%s] into a DOM", extensionsXmlPath), e);
                    }
                } else {
                    try {
                        extensionsXmlDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                    } catch (ParserConfigurationException e) {
                        throw new MojoExecutionException(
                                String.format("Could create an empty DOM for [%s]", extensionsXmlPath), e);
                    }
                }

                ExtensionsXmlUtils.upgradeOrAddSrcdeps(extensionsXmlDoc, newVersion);

                Files.createDirectories(mvnDir);
                try (Writer out = Files.newBufferedWriter(extensionsXmlPath, Charset.forName(encoding))) {
                    @SuppressWarnings("deprecation")
                    OutputFormat format = new OutputFormat(extensionsXmlDoc);
                    format.setLineWidth(xmlLineLength);
                    format.setIndenting(xmlIndent);
                    format.setIndent(xmlIndentSize);

                    XMLSerializer serializer = new XMLSerializer(out, format);
                    serializer.serialize(extensionsXmlDoc);
                }
            } catch (IOException e) {
                throw new MojoExecutionException(String.format("Could not create [%s]", mvnDir), e);
            }
        }
    }

}
