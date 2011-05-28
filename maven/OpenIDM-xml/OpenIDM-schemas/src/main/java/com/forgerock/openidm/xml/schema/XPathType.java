/*
 * 
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 * 
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 * 
 * $Id$
 */
package com.forgerock.openidm.xml.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *
 * TODO: documentation
 *
 * TODO: This should be refactored to XPathHolder to avoid confusing with
 * generated XPathType
 *
 * Assumes relative XPath, but somhow can also work with absolute XPaths.
 *
 * @author semancik
 */
public class XPathType {

    public static final String REPLACE_PREFIX_FOR_DEFAULT_NAMESPACE = "idmdn";
    private boolean absolute;
    private List<XPathSegment> segments;
    Map<String, String> explicitNamespaceDeclarations;
    static Random rnd = new Random();

    /**
     * Sets "current node" Xpath.
     */
    public XPathType() {
        absolute = false;
        segments = new ArrayList<XPathSegment>();
    }

    // This should not really be used. There should always be a namespace
    public XPathType(String xpath) {
        parse(xpath, null, null);
    }

    public XPathType(String xpath, Map namespaceMap) {
        parse(xpath, null, namespaceMap);
    }

    public XPathType(List<XPathSegment> segments) {
        this(segments, false);
    }

    public XPathType(List<XPathSegment> segments, boolean absolute) {
        this.segments = new ArrayList<XPathSegment>();
        for (XPathSegment segment : segments) {
            if (StringUtils.isEmpty(segment.getQName().getPrefix())) {
                QName qname = segment.getQName();
                this.segments.add(new XPathSegment(new QName(qname.getNamespaceURI(), qname.getLocalPart(), REPLACE_PREFIX_FOR_DEFAULT_NAMESPACE)));
            } else {
                this.segments.add(segment);
            }
        }

        //this.segments = segments;
        this.absolute = absolute;
    }

    public XPathType(Element domElement) {

        String xpath = ".";
        if (null != domElement) {
            xpath = domElement.getTextContent();
        }

        parse(xpath, domElement, null);

        // We are stupid now.
        // We don't understand XPath and therefore we don't know
        // what namespace prefixes are there. To be on the safe side
        // just remember all applicable prefixes

//        namespaceMap = new HashMap<String, String>();
//        NamedNodeMap attributes = domElement.getAttributes();
//        for (int i = 0; i < attributes.getLength(); i++) {
//            Node n = attributes.item(i);
//            if ("xmlns".equals(n.getPrefix())) {
//                namespaceMap.put(n.getLocalName(), n.getNodeValue());
//            }
//            if (n.getPrefix() == null && "xmlns".equals(n.getLocalName())) {
//                // Default namespace
//                namespaceMap.put("",n.getNodeValue());
//            }
//        }
    }

    public XPathType(String xpath, Node domNode) {

        parse(xpath, domNode, null);
    }

    private void parse(String xpath, Node domNode, Map<String, String> namespaceMap) {

        segments = new ArrayList<XPathSegment>();
        absolute = false;

        if (".".equals(xpath)) {
            return;
        }

        // Check for explicit namespace declarations.
        TrivialXPathParser parser = TrivialXPathParser.parse(xpath);
        explicitNamespaceDeclarations = parser.getNamespaceMap();

        // Continue parsing with Xpath without the "preamble"
        xpath = parser.getPureXPathString();

        String[] segArray = xpath.split("/");
        for (int i = 0; i < segArray.length; i++) {
            if (segArray[i] == null || segArray[i].isEmpty()) {
                if (i == 0) {
                    absolute = true;
                    // ignore the fist empty segment of absolute path
                    continue;
                } else {
                    throw new IllegalStateException("XPath " + xpath + " has an empty segment (number " + i + ")");
                }
            }

            // TODO: add support for [@attr="value"]

            String segmentStr = segArray[i];
            boolean variable = false;
            if (segmentStr.startsWith("$")) {
                // We have variable here
                variable = true;
                segmentStr = segmentStr.substring(1);
            }

            String[] qnameArray = segmentStr.split(":");
            if (qnameArray.length > 2) {
                throw new IllegalStateException("Unsupported format: more than one colon in XPath segment: " + segArray[i]);
            }
            QName qname;
            if (qnameArray.length == 1 || qnameArray[1] == null || qnameArray[1].isEmpty()) {
                // default namespace <= empty prefix
                String namespace = findNamespace(null, domNode, namespaceMap);
                qname = new QName(namespace, qnameArray[0], REPLACE_PREFIX_FOR_DEFAULT_NAMESPACE);
            } else {
                String namespace = findNamespace(qnameArray[0], domNode, namespaceMap);
                qname = new QName(namespace, qnameArray[1], qnameArray[0]);
            }

            XPathSegment segment = new XPathSegment(qname, variable);

            segments.add(segment);
        }
    }

    private String findNamespace(String prefix, Node domNode, Map<String, String> namespaceMap) {

        String ns = null;

        if (explicitNamespaceDeclarations != null) {
            if (prefix == null) {
                ns = explicitNamespaceDeclarations.get("");
            } else {
                ns = explicitNamespaceDeclarations.get(prefix);
            }
            if (ns != null) {
                return ns;
            }
        }

        if (namespaceMap != null) {
            if (prefix == null) {
                ns = namespaceMap.get("");
            } else {
                ns = namespaceMap.get(prefix);
            }
            if (ns != null) {
                return ns;
            }
        }

        if (domNode != null) {
            if (prefix == null || prefix.isEmpty()) {
                ns = domNode.lookupNamespaceURI(null);
            } else {
                ns = domNode.lookupNamespaceURI(prefix);
            }
            if (ns != null) {
                return ns;
            }
        }

        //TODO: workaround is described in method: addPureXpath()
        //Note: this check is needed if some calls XPathType constructor with parameter xpath as a String.
        if (ns == null) {
            if (REPLACE_PREFIX_FOR_DEFAULT_NAMESPACE.equals(prefix) || (null == prefix)) {
                return SchemaConstants.NS_C;
            }
        }
        //TODO: workaround end

        return ns;
    }

    public String getXPath() {
        StringBuilder sb = new StringBuilder();

        addPureXpath(sb);

        return sb.toString();
    }

    public String getXPathWithDeclarations() {
        StringBuilder sb = new StringBuilder();

        addExplicitNsDeclarations(sb);
        addPureXpath(sb);

        return sb.toString();
    }

    private void addPureXpath(StringBuilder sb) {
        if (!absolute && segments.isEmpty()) {
            // Emtpty segment list gives a "local node" XPath
            sb.append(".");
            return;
        }

        if (absolute) {
            sb.append("/");
        }

        Iterator<XPathSegment> iter = segments.iterator();
        while (iter.hasNext()) {
            XPathSegment seg = iter.next();
            if (seg.isVariable()) {
                sb.append("$");
            }
            QName qname = seg.getQName();
            if (qname.getPrefix() != null && !qname.getPrefix().isEmpty()) {
                sb.append(qname.getPrefix() + ":" + qname.getLocalPart());
            } else {
                // Default namespace
                //TODO: HACK - because of broken implementations of JAXP and JVM, we will transform default namespace to some namespace
                //problem with default namespace resolution - implementation is broken:
                // http://stackoverflow.com/questions/1730710/xpath-is-there-a-way-to-set-a-default-namespace-for-queries
                //Jaxen and Saxon are treating xpath with "./:" differently
                sb.append(REPLACE_PREFIX_FOR_DEFAULT_NAMESPACE + ":" + qname.getLocalPart());
                //sb.append(qname.getLocalPart());
            }

            if (iter.hasNext()) {
                sb.append("/");
            }
        }
    }

    public Map<String, String> getNamespaceMap() {

        Map<String, String> namespaceMap = new HashMap<String, String>();
        Iterator<XPathSegment> iter = segments.iterator();
        while (iter.hasNext()) {
            XPathSegment seg = iter.next();
            QName qname = seg.getQName();
            if (qname.getPrefix() != null) {
                namespaceMap.put(qname.getPrefix(), qname.getNamespaceURI());
            } else {
                // Default namespace
                namespaceMap.put("", qname.getNamespaceURI());
            }

        }

        return namespaceMap;
    }

    public Element toElement(String elementNamespace, String localElementName) {
        // TODO: is this efficient?
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder loader = factory.newDocumentBuilder();
            return toElement(elementNamespace, localElementName, loader.newDocument());
        } catch (ParserConfigurationException ex) {
            throw new AssertionError("Error on createing XML document " + ex.getMessage());
        }
    }

    public Element toElement(QName elementQName, Document document) {
        return toElement(elementQName.getNamespaceURI(), elementQName.getLocalPart(), document);
    }

    public Element toElement(String elementNamespace, String localElementName, Document document) {
        Element e = document.createElementNS(elementNamespace, localElementName);
        // Random prefix to avoid collision with nsXX provided by JAXB
        e.setPrefix("xp" + rnd.nextInt(10000));
        e.setTextContent(getXPath());
        Map<String, String> namespaceMap = getNamespaceMap();
        if (namespaceMap != null) {
            NamedNodeMap attributes = e.getAttributes();
            for (Entry<String, String> entry : namespaceMap.entrySet()) {
                Attr attr;
                if (entry.getKey() == null || entry.getKey().isEmpty()) {
                    // default namespace
                    attr = document.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns");
                } else {
                    attr = document.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + entry.getKey());
                }
                attr.setValue(entry.getValue());
                attributes.setNamedItem(attr);
            }
        }
        return e;
    }

    public List<XPathSegment> toSegments() {
        // FIXME !!!
        return Collections.unmodifiableList(segments);
    }

    @Override
    public String toString() {
        // TODO: more verbose toString later
        return getXPath();
    }

    private void addExplicitNsDeclarations(StringBuilder sb) {
        if (explicitNamespaceDeclarations == null || explicitNamespaceDeclarations.isEmpty()) {
            return;
        }

        for (String prefix : explicitNamespaceDeclarations.keySet()) {
            sb.append("declare ");
            if (prefix.equals("")) {
                sb.append("default namespace '");
                sb.append(explicitNamespaceDeclarations.get(prefix));
                sb.append("'; ");
                //TODO: workaround is described in method: addPureXpath()
                sb.append("declare namespace ");
                sb.append(REPLACE_PREFIX_FOR_DEFAULT_NAMESPACE);
                sb.append("='");
                sb.append(explicitNamespaceDeclarations.get(prefix));
                sb.append("'; ");
                //TODO: workaround end
            } else {
                sb.append("namespace ");
                sb.append(prefix);
                sb.append("='");
                sb.append(explicitNamespaceDeclarations.get(prefix));
                sb.append("'; ");
            }
        }
    }
}
