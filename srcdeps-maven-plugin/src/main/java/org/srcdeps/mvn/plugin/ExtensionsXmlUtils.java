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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Static methods to perform modifications on {@code extensions.xml} DOM, such as adding srcdeps core extensions to it
 * or upgrading their versions. Called from {@link SrcdepsUpgradeMojo}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ExtensionsXmlUtils {

    public enum ExtensionsXmlAttribute {
        artifactId, groupId, version
    }

    public enum ExtensionsXmlElement {
        artifactId, extension, extensions, groupId, version
    }

    public enum SrcdepsExtension {
        localRepo("org.srcdeps.mvn", "srcdeps-maven-local-repository"), //
        enforcer("org.srcdeps.mvn", "srcdeps-maven-enforcer") //
        ;

        private final String artifactId;
        private final String groupId;

        SrcdepsExtension(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        public boolean matches(String groupId, String artifactId) {
            return this.groupId.equals(groupId) && this.artifactId.equals(artifactId);
        }
    }

    private static String getFirstChildTextValue(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element elem = (Element) child;
                if (childName.equals(elem.getNodeName())) {
                    return getOrCreateFirstTextChild(elem).getTextContent();
                }
            }
        }
        return null;
    }

    private static Text getOrCreateFirstTextChild(Element node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Text) {
                return (Text) child;
            }
        }
        Text result = node.getOwnerDocument().createTextNode("");
        node.appendChild(result);
        return result;
    }

    private static void setTextValue(Element parent, String childName, String childValue) {
        Element elem = parent.getOwnerDocument().createElement(childName);
        parent.appendChild(elem);
        Text textNode = parent.getOwnerDocument().createTextNode(childValue);
        elem.appendChild(textNode);
    }

    /**
     * First, if necessary, adds the srcdeps extensions listed in {@link SrcdepsExtension} enum to the given DOM
     * {@code document} and then, if necessary, upgrades the srcdeps extensions to the given {@code newVersion}. It is
     * up to the caller to eventually store the document.
     *
     * @param doc
     *            the DOM of {@code extensions.xml} to modify, may be an empty DOM
     * @param newVersion
     *            the srcdeps version to add or upgrade to
     */
    public static void upgradeOrAddSrcdeps(Document doc, String newVersion) {

        Element extensions = doc.getDocumentElement();
        if (extensions == null) {
            extensions = doc.createElement(ExtensionsXmlElement.extensions.name());
            doc.appendChild(extensions);
        }

        for (SrcdepsExtension srcdepsExtension : SrcdepsExtension.values()) {

            boolean extensionFound = false;
            NodeList extChildren = extensions.getChildNodes();
            for (int i = 0; i < extChildren.getLength(); i++) {
                Node extChild = extChildren.item(i);
                if (extChild instanceof Element) {
                    Element extension = (Element) extChild;
                    if (ExtensionsXmlElement.extension.name().equals(extChild.getNodeName())) {
                        final String groupId = getFirstChildTextValue(extension, ExtensionsXmlAttribute.groupId.name());
                        final String artifactId = getFirstChildTextValue(extension,
                                ExtensionsXmlAttribute.artifactId.name());
                        if (srcdepsExtension.matches(groupId, artifactId)) {

                            NodeList versions = extension.getElementsByTagName(ExtensionsXmlAttribute.version.name());
                            boolean versionReplaced = false;
                            for (int j = 0; j < versions.getLength(); j++) {
                                Node version = versions.item(j);
                                if (j == 0) {
                                    Text versionText = getOrCreateFirstTextChild((Element) version);
                                    if (!newVersion.equals(versionText.getTextContent())) {
                                        versionText.setTextContent(newVersion);
                                    }
                                    versionReplaced = true;
                                } else {
                                    extension.removeChild(version);
                                }
                            }
                            if (!versionReplaced) {
                                setTextValue(extension, ExtensionsXmlAttribute.version.name(), newVersion);
                            }

                            extensionFound = true;
                            break;
                        }
                    }
                }
            }

            if (!extensionFound) {
                Element extension = doc.createElement(ExtensionsXmlElement.extension.name());

                setTextValue(extension, ExtensionsXmlAttribute.groupId.name(), srcdepsExtension.groupId);
                setTextValue(extension, ExtensionsXmlAttribute.artifactId.name(), srcdepsExtension.artifactId);
                setTextValue(extension, ExtensionsXmlAttribute.version.name(), newVersion);

                extensions.appendChild(extension);
            }

        }

    }

    private ExtensionsXmlUtils() {
    }
}
