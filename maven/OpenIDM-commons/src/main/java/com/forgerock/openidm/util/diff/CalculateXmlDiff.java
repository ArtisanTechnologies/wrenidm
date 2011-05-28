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
package com.forgerock.openidm.util.diff;

import com.forgerock.openidm.logging.TraceManager;
import com.forgerock.openidm.util.DOMUtil;
import com.forgerock.openidm.util.DebugUtil;
import com.forgerock.openidm.util.ObjectTypeUtil;
import com.forgerock.openidm.util.Utils;
import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectFactory;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyModificationTypeType;
import com.forgerock.openidm.xml.schema.XPathSegment;
import com.forgerock.openidm.xml.schema.XPathType;
import com.sun.org.apache.xerces.internal.dom.AttrNSImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.custommonkey.xmlunit.ComparisonController;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.DifferenceConstants;
import org.custommonkey.xmlunit.DifferenceEngine;
import org.custommonkey.xmlunit.XMLUnit;
import org.slf4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Main class responsible to calculate XML differences
 *
 * @author Igor Farinic
 * @version $Revision$ $Date$
 * @since 0.1
 */
public class CalculateXmlDiff {

    //TODO: WARNING !!! Multivalue properties are not handled properly or for some scenarios are not handled at all (see OPENIDM-316)!!! WARNING

    private static final Logger logger = TraceManager.getTrace(CalculateXmlDiff.class);

    private static PropertyModificationTypeType decideModificationTypeForChildNodeNotFound(Difference diff) {
        //add or replace node
        String newObjectOid = Utils.getNodeOid(diff.getTestNodeDetail().getNode());
        PropertyModificationTypeType changeType;
        //HACK: accountRef, resourceRef - special treatment, ignore its oid
        if ((StringUtils.isEmpty(newObjectOid)) || (StringUtils.contains(diff.getTestNodeDetail().getNode().getNodeName(), "Ref"))) {
            changeType = PropertyModificationTypeType.add;
        } else {
            changeType = PropertyModificationTypeType.replace;
        }
        return changeType;
    }

    private static void setupXmlUnit() {
        //XmlUnit setup
        //Note: compareUnmatched has to be set to false to calculate diff properly,
        //to avoid matching of nodes that are not comparable
        XMLUnit.setCompareUnmatched(false);
        XMLUnit.setIgnoreAttributeOrder(true);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalize(true);
        XMLUnit.setNormalizeWhitespace(true);
    }

    private static List<Difference> calculateXmlUnitDifferences(InputStream inputStreamOld, InputStream inputStreamNew) throws DiffException {
        List<Difference> l;
        try {
            logger.trace("Calculate XmlUnit differences");
            //Following setup is required to explicitly inject OpenIdm XPath tracker
            //We will use the same behavior for ComparisonController as the one used by DetailedDiff
            ComparisonController controller = new ComparisonController() {

                @Override
                public boolean haltComparison(Difference arg0) {
                    return false;
                }
            };
            DifferenceEngine comparator = new OpenIdmDifferenceEngine(controller);
            Diff d = new Diff(
                    XMLUnit.buildControlDocument(new InputSource(inputStreamOld)),
                    XMLUnit.buildTestDocument(new InputSource(inputStreamNew)),
                    comparator);
            //end of setup for OpenIdm XPath tracker

            //calculate XMLUnit differences
            DetailedDiff dd = new DetailedDiff(d);
            dd.overrideElementQualifier(new OidQualifier());
            dd.overrideDifferenceListener(new OpenIdmDifferenceListener());
            l = dd.getAllDifferences();
            logger.trace("XmlUnit Differences are ready");
            return l;
        } catch (SAXException ex) {
            logger.error("Error calculating differences", ex);
            throw new DiffException(ex);
        } catch (IOException ex) {
            logger.error("Error calculating differences", ex);
            throw new DiffException(ex);
        }
    }

    public static ObjectModificationType calculateChanges(ObjectType oldObject, ObjectType newObject) throws DiffException {

        Validate.notNull(oldObject);
        Validate.notNull(newObject);
        Validate.isTrue(oldObject.getOid().equals(newObject.getOid()));


        try {
            //wrap objects into JAXBElement
            ObjectFactory of = new ObjectFactory();
            JAXBElement<ObjectType> jaxbOld = of.createObject(oldObject);
            JAXBElement<ObjectType> jaxbNew = of.createObject(newObject);

            String stringOld = JAXBUtil.marshal(jaxbOld);
            String stringNew = JAXBUtil.marshal(jaxbNew);

            logger.trace("Old Object {}", stringOld);
            logger.trace("New Object {}", stringNew);

            return calculateChanges(IOUtils.toInputStream(stringOld, "utf-8"), IOUtils.toInputStream(stringNew, "utf-8"), oldObject.getOid());
        } catch (JAXBException ex) {
            throw new DiffException(ex);
        } catch (IOException ex) {
            throw new DiffException(ex);
        }
    }

    public static ObjectModificationType calculateChanges(File oldObjectFile, File newObjectFile) throws DiffException {
        try {
            JAXBElement<ObjectType> jaxbObject = (JAXBElement<ObjectType>) JAXBUtil.unmarshal(oldObjectFile);
            String objectOid = jaxbObject.getValue().getOid();
            return calculateChanges(new FileInputStream(oldObjectFile), new FileInputStream(newObjectFile), objectOid);
        } catch (JAXBException ex) {
            throw new DiffException(ex);
        } catch (FileNotFoundException ex) {
            throw new DiffException(ex);
        }
    }

    private static XPathType modifyXpath(String originalXpath, Node node) {
        XPathType xpathType = new XPathType(originalXpath, node);
        return modifyXpath(xpathType);

    }

    private static XPathType modifyXpath(XPathType originalXpath) {
        logger.trace("XPath generated by XMLUnit {}", originalXpath);
        List<XPathSegment> segments = originalXpath.toSegments();
        List<XPathSegment> modifiedSegments = new ArrayList<XPathSegment>();
        if (segments.size() > 2) {
            modifiedSegments = segments.subList(1, segments.size() - 1);
        }
        XPathType modifiedXpath = new XPathType(modifiedSegments);
        logger.trace("XPath modified for OpenIdm functionality {}", modifiedXpath.getXPath());
        return modifiedXpath;
    }

    private static Node getReplacePropertyNode(Node testNode, XPathType xpathForChange, String replacePropertyName) {
        Node node = testNode;

        List<XPathSegment> segments = xpathForChange.toSegments();
        for (int j = segments.size() - 1; j >= 0; j--) {
            XPathSegment segment = segments.get(j);
            if (replacePropertyName.equals(segment.getQName().getLocalPart())) {
                return node;
            } else {
                //Note: we had do instanceof for specific impl, because node type was null
                if (node instanceof AttrNSImpl) {
                    node = ((AttrNSImpl)node).getOwnerElement();
                } else {
                    node = node.getParentNode();
                }
            }
        }

        throw new IllegalArgumentException("Error getting node for replace property");
    }

    private static XPathType getXPathTypeForReplaceProperty(XPathType xpathForChange, String replacePropertyName) {
        List<XPathSegment> segments = xpathForChange.toSegments();
        int segmentWithReplaceProperty = 0;

        for (int j = segments.size() - 1; j >= 0; j--) {
            XPathSegment segment = segments.get(j);
            if (replacePropertyName.equals(segment.getQName().getLocalPart())) {
                segmentWithReplaceProperty = j;
                break;
            }
        }

        List<XPathSegment> modifiedSegments = segments.subList(0, segmentWithReplaceProperty + 1);
        return new XPathType(modifiedSegments);
    }

    private static boolean isReplacePropertyModificationRegistrered(ObjectModificationType changes, XPathType xpathType, String replacePropertyName) {
        //Prerequisite: xpath is for property that is "replaceProperty"
        for (PropertyModificationType change : changes.getPropertyModification()) {
            if (PropertyModificationTypeType.replace.equals(change.getModificationType())) {
                XPathType registeredXPath = new XPathType(change.getPath());
                if (xpathType.getXPath().equals(registeredXPath.getXPath())) {
                    //TODO: supported is list with only one element
                    if (replacePropertyName.equals(change.getValue().getAny().get(0).getLocalName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ObjectModificationType calculateChanges(InputStream inputStreamOld, InputStream inputStreamNew, String objectOid) throws DiffException {

        setupXmlUnit();
        List<Difference> l = calculateXmlUnitDifferences(inputStreamOld, inputStreamNew);
        ObjectModificationType changes = new ObjectModificationType();
        //if there are no differences, return immidiatelly
        if (null == l || l.isEmpty()) {
            logger.trace("No differences found. Returning empty list of differences");
            return changes;
        }

        //prerequisite: changes are made only on container objects: user, account, resource, ...
        //String rootOid = getRootNodeOid(l.get(0));
        changes.setOid(objectOid);

        logger.trace("Iterate through differences and create relative changes out of them");
        PropertyModificationType change = null;
        XPathType xpathType;
        for (Difference diff : l) {
            logger.trace("Start processing of difference: {}", diff.getDescription());

            //process differences for replace properties
            if (DiffConstants.isForReplaceProperty(diff)) {
                String replacePropertyName = DiffConstants.getReplacePropertyName(diff);
                XPathType differenceXpath;
                if (null != diff.getTestNodeDetail().getXpathLocation()) {
                    //if xpath in test node is null then we are deleting, and the property delete is handled as standard property
                    //if xpath in test node is not null we are replacing

                    differenceXpath = new XPathType(diff.getTestNodeDetail().getXpathLocation(), diff.getTestNodeDetail().getNode());
                    xpathType = getXPathTypeForReplaceProperty(differenceXpath, replacePropertyName);

                    if (isReplacePropertyModificationRegistrered(changes, modifyXpath(xpathType), replacePropertyName)) {
                        //if the same converted modification is already in the list of property modification, then do nothing
                        continue;
                    }

                    //convert difference to propertyModification
                    Node testNodeWithReplaceProperty = getReplacePropertyNode(diff.getTestNodeDetail().getNode(), differenceXpath, replacePropertyName);
                    change = ObjectTypeUtil.createPropertyModificationType(PropertyModificationTypeType.replace, modifyXpath(xpathType), testNodeWithReplaceProperty);
                    changes.getPropertyModification().add(change);
                    continue;
                }
            }

            switch (diff.getId()) {
                //comparing two nodes and only one has any children
                case DifferenceConstants.HAS_CHILD_NODES_ID:
                //TODO: now it works, but the scenario for HAS_CHILD_NODES_ID should be treated separately, to generate correct diffs.

                //adding deleting node - account, extension
                case DifferenceConstants.CHILD_NODE_NOT_FOUND_ID:
                    //CHILD_NODE_NOT_FOUND_ID == presence of child node
                    if (StringUtils.isEmpty(diff.getTestNodeDetail().getXpathLocation())) { //delete node
                        //value removed, because it is not in new xml
                        //for removed property xpath is taken from original xml
                        xpathType = modifyXpath(diff.getControlNodeDetail().getXpathLocation(), diff.getControlNodeDetail().getNode());
                        change = ObjectTypeUtil.createPropertyModificationType(PropertyModificationTypeType.delete, xpathType, (Element) diff.getControlNodeDetail().getNode());
                    } else { //add or replace node
                        PropertyModificationTypeType changeType = decideModificationTypeForChildNodeNotFound(diff);
                        xpathType = modifyXpath(diff.getTestNodeDetail().getXpathLocation(), diff.getTestNodeDetail().getNode());
                        //for added and replaced property xpath is taken from new xml
                        change = ObjectTypeUtil.createPropertyModificationType(changeType, xpathType, (Element) diff.getTestNodeDetail().getNode());
                    }
                    break;
                //change property value
                case DifferenceConstants.TEXT_VALUE_ID:
                    //remove /text()
                    String generatedXpath = diff.getTestNodeDetail().getXpathLocation();
                    String modifiedPath = StringUtils.substring(generatedXpath, 0, generatedXpath.lastIndexOf("/"));
                    xpathType = modifyXpath(modifiedPath, diff.getTestNodeDetail().getNode().getParentNode());
                    change = ObjectTypeUtil.createPropertyModificationType(PropertyModificationTypeType.replace, xpathType, (Element) diff.getTestNodeDetail().getNode().getParentNode());
                    break;
            }

            //if change was accepted then add it to the list of relative changes
            if (null != change) {
                changes.getPropertyModification().add(change);
                logger.trace("Finished processing of difference {}. Relative change for difference is change = {}", diff.getDescription(), DebugUtil.prettyPrint(change));
                if (null != change.getValue()) {
                    logger.trace("Relative change value= {}", DOMUtil.serializeDOMToString(change.getValue().getAny().get(0)));
                } else {
                    logger.trace("Relative change value was null");
                }
            }
        }

        logger.trace("Finished relative changes processing");
        logger.debug("Returning differences stored as relative changes = {}", changes);
        return changes;
    }
}
