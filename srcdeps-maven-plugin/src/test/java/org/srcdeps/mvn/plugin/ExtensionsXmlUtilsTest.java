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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

@SuppressWarnings("deprecation")
public class ExtensionsXmlUtilsTest {
    private static final String EXTENSIONS_XML_TEMPLATE_REPO = //
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
                    "<extensions>\n" + //
                    "  <extension>\n" + //
                    "    <groupId>org.srcdeps.mvn</groupId>\n" + //
                    "    <artifactId>srcdeps-maven-local-repository</artifactId>\n" + //
                    "    <version>%s</version>\n" + //
                    "  </extension>\n" + //
                    "</extensions>\n";

    private static final String EXTENSIONS_XML_TEMPLATE_REPO_AND_ENFORCER = //
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
                    "<extensions>\n" + //
                    "  <extension>\n" + //
                    "    <groupId>org.srcdeps.mvn</groupId>\n" + //
                    "    <artifactId>srcdeps-maven-local-repository</artifactId>\n" + //
                    "    <version>%s</version>\n" + //
                    "  </extension>\n" + //
                    "  <extension>\n" + //
                    "    <groupId>org.srcdeps.mvn</groupId>\n" + //
                    "    <artifactId>srcdeps-maven-enforcer</artifactId>\n" + //
                    "    <version>%s</version>\n" + //
                    "  </extension>\n" + //
                    "</extensions>\n";

    private static final String EXTENSIONS_XML_TEMPLATE_REPO_ENFORCER_AND_THIRD_PARTY = //
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
                    "<extensions>\n" + //
                    "  <extension>\n" + //
                    "    <groupId>org.whatever</groupId>\n" + //
                    "    <artifactId>whatever</artifactId>\n" + //
                    "    <version>0.0.1</version>\n" + //
                    "  </extension>\n" + //
                    "  <extension>\n" + //
                    "    <groupId>org.srcdeps.mvn</groupId>\n" + //
                    "    <artifactId>srcdeps-maven-local-repository</artifactId>\n" + //
                    "    <version>%s</version>\n" + //
                    "  </extension>\n" + //
                    "  <extension>\n" + //
                    "    <groupId>org.srcdeps.mvn</groupId>\n" + //
                    "    <artifactId>srcdeps-maven-enforcer</artifactId>\n" + //
                    "    <version>%s</version>\n" + //
                    "  </extension>\n" + //
                    "</extensions>\n";

    private static void assertUpgradeOrAdd(Document doc, String upgradeVersion, String expected) throws IOException {
        ExtensionsXmlUtils.upgradeOrAddSrcdeps(doc, upgradeVersion);

        StringWriter out = new StringWriter();

        OutputFormat format = new OutputFormat(doc);
        format.setLineWidth(128);
        format.setIndenting(true);
        format.setIndent(2);

        XMLSerializer serializer = new XMLSerializer(out, format);
        serializer.serialize(doc);

        // System.out.println("expected:\n" + expected);
        // System.out.println("found:\n" + out.toString());

        Assert.assertEquals(expected, out.toString());

    }

    @Test
    public void upgradeOrAddSrcdepsEmpty()
            throws TransformerConfigurationException, TransformerException, IOException, ParserConfigurationException {

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        final String upgradeVersion = "1.2.3";
        String expected = String.format(EXTENSIONS_XML_TEMPLATE_REPO_AND_ENFORCER, upgradeVersion, upgradeVersion);
        assertUpgradeOrAdd(doc, upgradeVersion, expected);

    }

    @Test
    public void upgradeRepoAddEnforcer() throws TransformerConfigurationException, TransformerException, IOException {

        final String origVersion = "3.1.0";
        String source = String.format(EXTENSIONS_XML_TEMPLATE_REPO, origVersion, origVersion);

        DOMResult domResult = new DOMResult();
        TransformerFactory.newInstance().newTransformer().transform(new StreamSource(new StringReader(source)),
                domResult);

        Document doc = (Document) domResult.getNode();

        final String upgradeVersion = "3.2.0";
        String expected = String.format(EXTENSIONS_XML_TEMPLATE_REPO_AND_ENFORCER, upgradeVersion, upgradeVersion);

        assertUpgradeOrAdd(doc, upgradeVersion, expected);

    }

    @Test
    public void upgradeRepoAddEnforcerWithThirdParty()
            throws TransformerConfigurationException, TransformerException, IOException {

        final String origVersion = "4.0.0";
        String source = String.format(EXTENSIONS_XML_TEMPLATE_REPO_ENFORCER_AND_THIRD_PARTY, origVersion, origVersion);

        DOMResult domResult = new DOMResult();
        TransformerFactory.newInstance().newTransformer().transform(new StreamSource(new StringReader(source)),
                domResult);

        Document doc = (Document) domResult.getNode();

        final String upgradeVersion = "4.1.0";
        String expected = String.format(EXTENSIONS_XML_TEMPLATE_REPO_ENFORCER_AND_THIRD_PARTY, upgradeVersion,
                upgradeVersion);

        assertUpgradeOrAdd(doc, upgradeVersion, expected);

    }

    @Test
    public void upgradeRepoAndEnforcer() throws TransformerConfigurationException, TransformerException, IOException {

        final String origVersion = "1.0.0";
        String source = String.format(EXTENSIONS_XML_TEMPLATE_REPO_AND_ENFORCER, origVersion, origVersion);

        DOMResult domResult = new DOMResult();
        TransformerFactory.newInstance().newTransformer().transform(new StreamSource(new StringReader(source)),
                domResult);

        Document doc = (Document) domResult.getNode();

        final String upgradeVersion = "2.0.0";
        String expected = String.format(EXTENSIONS_XML_TEMPLATE_REPO_AND_ENFORCER, upgradeVersion, upgradeVersion);

        assertUpgradeOrAdd(doc, upgradeVersion, expected);

    }

}
