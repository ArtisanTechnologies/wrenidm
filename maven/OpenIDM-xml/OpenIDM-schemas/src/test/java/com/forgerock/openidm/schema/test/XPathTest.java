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

package com.forgerock.openidm.schema.test;

import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectFactory;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyModificationType;
import com.forgerock.openidm.xml.schema.TrivialXPathParser;
import com.forgerock.openidm.xml.schema.XPathSegment;
import com.forgerock.openidm.xml.schema.XPathType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import static org.junit.Assert.*;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author semancik
 */
public class XPathTest {

    private static final String FILENAME_CHANGETYPE = "src/test/resources/examples/changetype-1.xml";
    private static final String FILENAME_STRANGECHARS = "src/test/resources/xpath/strange.txt";

    public XPathTest() {
    }

    /**
     * This is now a proper test yet.
     * It does some operations with XPath. If it does not die, then the
     * code some somehow consistent.
     *
     * It should be improved later.
     */
    @Test
    public void xpathTest() throws JAXBException, FileNotFoundException, IOException, ParserConfigurationException {

        File file = new File(FILENAME_CHANGETYPE);
        FileInputStream fis = new FileInputStream(file);

        Unmarshaller u = null;

        JAXBContext jc = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
        u = jc.createUnmarshaller();

        Object object = u.unmarshal(fis);

        ObjectModificationType objectModification = (ObjectModificationType) ((JAXBElement) object).getValue();

        for (PropertyModificationType change : objectModification.getPropertyModification()) {
            Element path = change.getPath();
            System.out.println("  path=" + path + " (" + path.getClass().getName() + ") " + path.getLocalName() + " = " + path.getTextContent());
            NamedNodeMap attributes = path.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node n = attributes.item(i);
//                System.out.println("   A: " + n.getClass().getName() + " " + n.getNodeName() + "(" + n.getPrefix() + " : " + n.getLocalName() + ") = " + n.getNodeValue());
            }
            List<Element> any = change.getValue().getAny();
            for (Element e : any) {
//                System.out.println("  E: " + e.getLocalName());
            }

            XPathType xpath = new XPathType(path);

            assertEquals("/c:extension/piracy:ship", xpath.getXPath());

            System.out.println("XPATH: " + xpath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder loader = factory.newDocumentBuilder();
            Document doc = loader.newDocument();

            Element xpathElement = xpath.toElement("http://elelel/", "path", doc);

            Attr nsC = xpathElement.getAttributeNodeNS("http://www.w3.org/2000/xmlns/", "c");
            Attr nsPiracy = xpathElement.getAttributeNodeNS("http://www.w3.org/2000/xmlns/", "piracy");

            System.out.println("c: "+nsC);
            System.out.println("piracy: "+nsPiracy);

            assertEquals("http://openidm.forgerock.com/xml/ns/public/common/common-1.xsd",nsC.getValue());
            assertEquals("http://openidm.forgerock.com/xml/ns/samples/piracy",nsPiracy.getValue());

            System.out.println("XPATH Element: " + xpathElement);

//            attributes = xpathElement.getAttributes();
//            for (int i = 0; i < attributes.getLength(); i++) {
//                Node n = attributes.item(i);
//                System.out.println(" A: " + n.getNodeName() + "(" + n.getPrefix() + " : " + n.getLocalName() + ") = " + n.getNodeValue());
//            }

            List<XPathSegment> segments = xpath.toSegments();

            System.out.println("XPATH segments: " + segments);

            XPathType xpathFromSegments = new XPathType(segments);

            System.out.println("XPath from segments: " + xpathFromSegments);

            assertEquals("c:extension/piracy:ship", xpathFromSegments.getXPath());

        }

    }

    @Test
    public void xPathFromDomNode1() throws ParserConfigurationException, SAXException, IOException {

        // Given

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        File file = new File("src/test/resources/xpath/data.xml");
        Document doc = builder.parse(file);

        NodeList childNodes = doc.getChildNodes();

        NodeList rootNodes = doc.getElementsByTagName("root");
        Node rootNode = rootNodes.item(0);

        NodeList nodes = ((Element) rootNode).getElementsByTagNameNS("http://xx.com/", "el1");

        Node el1 = nodes.item(0);

        String xpathString = "/root/x:el1";

        // When

        XPathType xpath = new XPathType(xpathString, el1);

        // Then

        Map<String, String> namespaceMap = xpath.getNamespaceMap();

        assertEquals("http://default.com/", namespaceMap.get("idmdn"));

        List<XPathSegment> segments = xpath.toSegments();

        System.out.println("XXXX: " + xpath);

        // TODO
    }

    @Test
    public void xPathFromDomNode2() throws ParserConfigurationException, SAXException, IOException {

        // Given

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        File file = new File("src/test/resources/xpath/data.xml");
        Document doc = builder.parse(file);

        NodeList childNodes = doc.getChildNodes();

        NodeList rootNodes = doc.getElementsByTagName("root");
        Node rootNode = rootNodes.item(0);

        NodeList nodes = ((Element) rootNode).getElementsByTagNameNS("http://xx.com/", "el1");

        Node el1 = nodes.item(0);

        String xpathString = "/:root/x:el1";

        // When

        XPathType xpath = new XPathType(xpathString, el1);

        // Then

        Map<String, String> namespaceMap = xpath.getNamespaceMap();

        assertEquals("http://default.com/", namespaceMap.get(""));
    }

        @Test
    public void variableTest() {

            String xpathStr =
                    "declare namespace v='http://vvv.com';"+
                    "declare namespace x='http://www.xxx.com';"+
                    "$v:var/x:xyz";

            XPathType xpath = new XPathType(xpathStr);

            assertEquals("$v:var/x:xyz", xpath.getXPath());
            assertEquals("http://vvv.com", xpath.getNamespaceMap().get("v"));
            assertEquals("http://www.xxx.com", xpath.getNamespaceMap().get("x"));

    }


    @Test
    public void dotTest() {

        XPathType dotPath = new XPathType(".");

        assertTrue(dotPath.toSegments().isEmpty());

        assertEquals(".", dotPath.getXPath());

    }

    @Test
    public void explicitNsParseTest() {

        String xpathStr =
                "declare namespace foo='http://ff.com/';\ndeclare default namespace 'http://default.com/';\n declare  namespace bar = 'http://www.b.com' ;declare namespace x= \"http://xxx.com/\";\nfoo:foofoo/x:bar";

        TrivialXPathParser parser = TrivialXPathParser.parse(xpathStr);

        assertEquals("http://ff.com/", parser.getNamespaceMap().get("foo"));
        assertEquals("http://www.b.com", parser.getNamespaceMap().get("bar"));
        assertEquals("http://xxx.com/", parser.getNamespaceMap().get("x"));
        assertEquals("http://default.com/", parser.getNamespaceMap().get(""));

        assertEquals("foo:foofoo/x:bar", parser.getPureXPathString());
    }

    @Test
    public void simpleXPathParseTest() {
        String xpathStr =
                "foo/bar";

        TrivialXPathParser parser = TrivialXPathParser.parse(xpathStr);

        assertEquals("foo/bar", parser.getPureXPathString());
    }

    @Test
    public void explicitNsRoundTripTest() {

        String xpathStr =
                "declare namespace foo='http://ff.com/';\ndeclare default namespace 'http://default.com/';\n declare  namespace bar = 'http://www.b.com' ;declare namespace x= \"http://xxx.com/\";\nfoo:foofoo/x:bar";

        XPathType xpath = new XPathType(xpathStr);

        System.out.println("Pure XPath: "+xpath.getXPath());
        assertEquals("foo:foofoo/x:bar", xpath.getXPath());

        System.out.println("ROUND TRIP: "+xpath.getXPathWithDeclarations());
        assertEquals("declare default namespace 'http://default.com/'; declare namespace idmdn='http://default.com/'; declare namespace foo='http://ff.com/'; declare namespace bar='http://www.b.com'; declare namespace x='http://xxx.com/'; foo:foofoo/x:bar",
                xpath.getXPathWithDeclarations());
        
    }

    @Test
    public void pureXPathRoundTripTest() {

        String xpathStr = "foo:foo/bar:bar";

        XPathType xpath = new XPathType(xpathStr);

        System.out.println("Pure XPath: "+xpath.getXPath());
        assertEquals("foo:foo/bar:bar", xpath.getXPath());

        System.out.println("ROUND TRIP: "+xpath.getXPathWithDeclarations());
        assertEquals("foo:foo/bar:bar", xpath.getXPathWithDeclarations());

    }


    @Test
    public void strangeCharsTest() throws FileNotFoundException, UnsupportedEncodingException, IOException {

        String xpathStr;

        // The file contains strange chanrs (no-break spaces), so we need to pull
        // it in exactly as it is.
        File file = new File(FILENAME_STRANGECHARS);
        FileInputStream stream = new FileInputStream(file);
        try {
            FileChannel fc = stream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            xpathStr = Charset.forName("UTF-8").decode(bb).toString();
        }
        finally {
            stream.close();
        }

        XPathType xpath = new XPathType(xpathStr);

        System.out.println("Stragechars Pure XPath: "+xpath.getXPath());
        assertEquals("$i:user/i:extension/ri:foobar", xpath.getXPath());

        System.out.println("Stragechars ROUND TRIP: "+xpath.getXPathWithDeclarations());

    }

}
