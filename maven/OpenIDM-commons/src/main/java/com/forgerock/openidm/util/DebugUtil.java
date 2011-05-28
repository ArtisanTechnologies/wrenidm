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
package com.forgerock.openidm.util;

import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.xml.ns._public.common.common_1.AccountShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.FaultType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectChangeAdditionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectChangeDeletionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectChangeModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectChangeType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectContainerType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyAvailableValuesListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyAvailableValuesType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.QueryType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowChangeDescriptionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserContainerType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserType;
import com.forgerock.openidm.xml.ns._public.provisioning.resource_object_change_listener_1.FaultMessage;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import com.forgerock.openidm.xml.schema.XPathType;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Marker;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 *
 * @author semancik
 */
public class DebugUtil {

    private static int SHOW_LIST_MEMBERS = 3;

    public static String prettyPrint(PropertyReferenceListType reflist) {
        if (reflist == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        Iterator<PropertyReferenceType> iterator = reflist.getProperty().iterator();
        while (iterator.hasNext()) {
            sb.append(prettyPrint(iterator.next()));
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String prettyPrint(PropertyReferenceType ref) {
        if (ref == null) {
            return "null";
        }
        XPathType xpath = new XPathType(ref.getProperty());
        return xpath.toString();

    }

    public static String prettyPrint(ObjectType object) {
        return prettyPrint(object,false);
    }

    public static String prettyPrint(ObjectType object, boolean showContent) {

        if (object instanceof AccountShadowType) {
            return prettyPrint((AccountShadowType)object,showContent);
        }
        
        if (object == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(object.getClass().getSimpleName());
        sb.append("(");
        sb.append(object.getOid());
        sb.append(",");
        sb.append(object.getName());

        if (showContent) {
            // This is just a fallback. Methods with more specific signature
            // should be used instead
            for (PropertyDescriptor desc : PropertyUtils.getPropertyDescriptors(object)) {
                if (!"oid".equals(desc.getName()) && !"name".equals(desc.getName())) {
                    try {
                        Object value = PropertyUtils.getProperty(object, desc.getName());
                        sb.append(desc.getName());
                        sb.append("=");
                        sb.append(value);
                        sb.append(",");
                    } catch (IllegalAccessException ex) {
                        sb.append(desc.getName());
                        sb.append(":");
                        sb.append(ex.getClass().getSimpleName());
                        sb.append(",");
                    } catch (InvocationTargetException ex) {
                        sb.append(desc.getName());
                        sb.append(":");
                        sb.append(ex.getClass().getSimpleName());
                        sb.append(",");
                    } catch (NoSuchMethodException ex) {
                        sb.append(desc.getName());
                        sb.append(":");
                        sb.append(ex.getClass().getSimpleName());
                        sb.append(",");
                    }
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public static String prettyPrint(AccountShadowType object, boolean showContent) {
        if (object == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(object.getClass().getSimpleName());
        sb.append("(");
        sb.append(object.getOid());
        sb.append(",name=");
        sb.append(object.getName());
        sb.append(",");
        if (showContent) {
            if (object.getResource()!=null) {
                sb.append("resource=(@");
                sb.append(object.getResource());
                sb.append("),");
            }
            if (object.getResourceRef()!=null) {
                sb.append("resourceRef=(@");
                sb.append(object.getResourceRef());
                sb.append("),");
            }
            sb.append("objectClass=");
            sb.append(object.getObjectClass());
            sb.append(",attributes=(");
            sb.append(prettyPrint(object.getAttributes()));
            sb.append("),...");
            // TODO: more
        }
        sb.append(")");
        return sb.toString();
    }

    public static String prettyPrint(AccountShadowType.Attributes attrs) {
        if (attrs == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (Element el : attrs.getAny()) {
            sb.append(prettyPrint(el));
        }
        return sb.toString();
    }


    public static String prettyPrint(ObjectContainerType container) {
        if (container == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("ObjectContainer(");
        sb.append(prettyPrint(container.getObject()));
        sb.append(")");
        return sb.toString();
    }

    public static String prettyPrint(ObjectModificationType objectChange) {
        if (objectChange == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("ObjectChange(");
        sb.append(objectChange.getOid());
        sb.append(",");
        List<PropertyModificationType> changes = objectChange.getPropertyModification();
        sb.append("[");
        Iterator<PropertyModificationType> iterator = changes.iterator();
        while (iterator.hasNext()) {
            sb.append(prettyPrint(iterator.next()));
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }
        sb.append("])");
        return sb.toString();
    }

    public static String prettyPrint(PropertyModificationType change) {
        if (change == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("Change(");
        sb.append(change.getModificationType());
        sb.append(",");
        if (change.getPath() != null) {
            XPathType xpath = new XPathType(change.getPath());
            sb.append(xpath.toString());
        } else {
            sb.append("xpath=null");
        }
        sb.append(",");

        sb.append(prettyPrint(change.getValue().getAny()));

        return sb.toString();
    }

    /**
     * Assumes that all elements in the lists have the same QName
     * @param list
     * @return
     */
    public static String prettyPrint(List<Element> list) {
        if (list == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        if (list.size() > 0) {
            Element el0 = list.get(0);
            QName elQName;
            if (el0.getPrefix() != null) {
                elQName = new QName(el0.getNamespaceURI(), el0.getLocalName(), el0.getPrefix());
            } else {
                elQName = new QName(el0.getNamespaceURI(), el0.getLocalName());
            }
            sb.append(elQName);
            sb.append("[");
            Iterator<Element> iterator = list.iterator();
            while (iterator.hasNext()) {
                // TODO: improve for non-strings
                Element el = iterator.next();
                sb.append(prettyPrint(el, false));
                if (iterator.hasNext()) {
                    sb.append(",");
                }
                sb.append("]");
            }
        } else {
            sb.append("[]");
        }
        return sb.toString();
    }

    public static String prettyPrint(Node node) {
        if (node instanceof Element) {
            return prettyPrint((Element) node);
        }
        // TODO: Better print
        return "Node:"+node.getNodeName();
    }

    public static String prettyPrint(Element element) {
        return prettyPrint(element, true);
    }

    public static String prettyPrint(Element element, boolean displayTag) {
        if (element == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        if (displayTag) {
            sb.append("<");
            sb.append(new QName(element.getNamespaceURI(), element.getLocalName()));
            sb.append(">");
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if ("xmlns".equals(attr.getPrefix())) {
                // Don't display XML NS declarations
                // they are too long for prettyPrint
                continue;
            }
            if ((attr.getPrefix() == null || attr.getPrefix().isEmpty()) && "xmlns".equals(attr.getLocalName())) {
                // Skip default ns declaration as well
                continue;
            }
            sb.append("@");
            sb.append(attr.getLocalName());
            sb.append("=");
            sb.append(attr.getTextContent());
            if (i < (attributes.getLength() - 1)) {
                sb.append(",");
            }
        }
        if (attributes.getLength() > 0) {
            sb.append(":");
        }
        StringBuilder content = new StringBuilder();
        Node child = element.getFirstChild();
        while (child!=null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                content.append(((Text)child).getTextContent());
            } else if (child.getNodeType() == Node.COMMENT_NODE) {
                // just ignore this
            } else {
                content = new StringBuilder("[complex content]");
                break;
            }
            child = child.getNextSibling();
        }
        
        sb.append(content);

        return sb.toString();
    }

    public static String prettyPrint(ObjectListType list) {
        if (list == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("ObjectList[");
        Iterator<ObjectType> iterator = list.getObject().iterator();
        int i = 0;
        while (iterator.hasNext()) {
            if (i < SHOW_LIST_MEMBERS) {
                sb.append(prettyPrint(iterator.next()));
                if (iterator.hasNext()) {
                    sb.append(",");
                }
            } else {
                sb.append("(and ");
                sb.append(list.getObject().size() - i);
                sb.append(" more)");
                break;
            }
            i++;
        }
        sb.append("]");
        return sb.toString();
    }

    public static String prettyPrint(PropertyAvailableValuesListType propertyAvailableValues) {
        if (propertyAvailableValues == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("PropertyAvailableValues[");
        List<PropertyAvailableValuesType> list = propertyAvailableValues.getAvailableValues();
        Iterator<PropertyAvailableValuesType> iterator = list.iterator();
        while (iterator.hasNext()) {
            PropertyAvailableValuesType values = iterator.next();
            sb.append(prettyPrint(values.getAny()));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String prettyPrint(UserContainerType container) {
        if (container == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("UserContainerType(");
        sb.append(prettyPrint(container.getUser()));
        sb.append(")");
        return sb.toString();
    }

    public static String prettyPrint(ResourceObjectShadowListType shadowListType) {
        if (shadowListType == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("ResourceObjectShadowListType(");

        sb.append("ResourceObjectShadow[");
        List<ResourceObjectShadowType> list = shadowListType.getObject();
        Iterator<ResourceObjectShadowType> iterator = list.iterator();
        while (iterator.hasNext()) {
            ResourceObjectShadowType values = iterator.next();
            sb.append(prettyPrint(values.getAny()));
        }
        sb.append("]");

        sb.append(")");
        return sb.toString();
    }

    public static String prettyPrint(QueryType query) {

        if (query == null) { return "null"; }
        
        Element filter = query.getFilter();

        StringBuilder sb = new StringBuilder("Query(");

        prettyPrintFilter(sb,filter);

        sb.append(")");

        return sb.toString();
    }

    private static void prettyPrintFilter(StringBuilder sb, Element filter) {

        if (filter==null) {
            sb.append("null");
            return;
        }

        String tag = filter.getLocalName();

        sb.append(tag);
        sb.append("(");

        if ("type".equals(tag)) {
            String uri = filter.getAttribute("uri");
            QName typeQname = QNameUtil.uriToQName(uri);
            sb.append(typeQname.getLocalPart());
            sb.append(")");
            return;
        }

        NodeList childNodes = filter.getChildNodes();
        for (int i=0; i<childNodes.getLength();i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType()==Node.TEXT_NODE) {
                sb.append("\"");
                sb.append(node.getTextContent());
                sb.append("\"");
            } else if (node.getNodeType()==Node.ELEMENT_NODE) {
                prettyPrintFilter(sb,(Element)node);
            } else {
                sb.append("!");
                sb.append(node.getNodeType());
            }
            sb.append(",");
        }

        sb.append(")");
    }

    public static String prettyPrint(ResourceObjectShadowChangeDescriptionType change) {
        if (change == null ) { return "null"; }
        StringBuilder sb = new StringBuilder("ResourceObjectShadowChangeDescriptionType(");
        sb.append(prettyPrint(change.getObjectChange()));
        sb.append(",");
        sb.append(change.getSourceChannel());
        sb.append(",");
        sb.append(prettyPrint(change.getShadow()));
        sb.append(",");
        sb.append(prettyPrint(change.getResource()));
        sb.append(")");
        return sb.toString();
    }

    public static String prettyPrint(ObjectChangeType change) {
        if (change == null ) { return "null"; }
        StringBuilder sb = new StringBuilder();
        if (change instanceof ObjectChangeAdditionType) {
            sb.append("ObjectChangeAdditionType(");
            ObjectChangeAdditionType add = (ObjectChangeAdditionType) change;
            sb.append(prettyPrint(add.getObject(),true));
            sb.append(")");
        } else if (change instanceof ObjectChangeModificationType) {
            sb.append("ObjectChangeModificationType(");
            ObjectChangeModificationType mod = (ObjectChangeModificationType) change;
            sb.append(prettyPrint(mod.getObjectModification()));
            sb.append(")");
        } else if (change instanceof ObjectChangeDeletionType) {
            sb.append("ObjectChangeDeletionType(");
            ObjectChangeDeletionType del = (ObjectChangeDeletionType) change;
            sb.append(del.getOid());
            sb.append(")");
        } else {
            sb.append("Unknown change type ");
            sb.append(change.getClass().getName());
        }
        return sb.toString();
    }

    public static String resourceFromShadow(ResourceObjectShadowType shadow) {
        if (shadow == null) {
            return null;
        }
        ResourceType resource = shadow.getResource();
        if (resource != null) {
            return resource.getName();
        }
        ObjectReferenceType resourceRef = shadow.getResourceRef();
        if (resourceRef != null) {
            return resourceRef.getOid();
        }
        return ("ERROR:noResource");
    }

    public static Object prettyPrint(FaultMessage fault) {
        if (fault == null) {
            return "resource-object-change-listener-1.FaultMessage=null";
        }
        StringBuilder sb = new StringBuilder("resource-object-change-listener-1.FaultMessage(FaultInfo(");
        FaultType faultInfo = fault.getFaultInfo();
        if (faultInfo!=null) {
            sb.append("\"");
            sb.append(faultInfo.getMessage());
            sb.append("\"");
        } else {
            sb.append("null");
        }
        sb.append("),");
        sb.append("\"");
        sb.append(fault.getMessage());
        sb.append("\")");

        return sb.toString();
    }

    public static String toReadableString(ResourceObjectShadowType shadow) {
        QName qname = SchemaConstants.I_RESOURCE_OBJECT_SHADOW;
        if (shadow instanceof AccountShadowType) {
            qname = SchemaConstants.I_ACCOUNT;
        }
        Element element;
        try {
            element = JAXBUtil.jaxbToDom(shadow, qname, null);
        } catch (JAXBException ex) {
            return("Error marshalling the object: "+ex.getLinkedException().getMessage());
        }
        NodeList resourceElements = element.getElementsByTagNameNS(SchemaConstants.I_RESOURCE.getNamespaceURI(), SchemaConstants.I_RESOURCE.getLocalPart());
        for (int i=0;i<resourceElements.getLength();i++) {
            Node el = (Element) resourceElements.item(i);
            el.setTextContent("[...]");
        }
        return DOMUtil.serializeDOMToString(element);
    }

    public static String toReadableString(UserType user) {
        QName qname = SchemaConstants.I_USER;
        try {
            return JAXBUtil.marshalWrap(user, qname);
        } catch (JAXBException ex) {
            return("Error marshalling the object: "+ex.getLinkedException().getMessage());
        }
    }
}
