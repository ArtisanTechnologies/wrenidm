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
 * Portions Copyrighted 2011 Igor Farinic
 * Portions Copyrighted 2011 Radovan Semancik
 * 
 * $Id$
 */
package com.forgerock.openidm.model.xpath;

import com.forgerock.openidm.model.filter.Filter;
import com.forgerock.openidm.model.filter.FilterManager;
import com.forgerock.openidm.provisioning.schema.ResourceAttributeDefinition;
import com.forgerock.openidm.provisioning.schema.ResourceObjectDefinition;
import com.forgerock.openidm.provisioning.schema.ResourceSchema;
import com.forgerock.openidm.provisioning.schema.util.DOMToSchemaParser;
import com.forgerock.openidm.util.Variable;
import com.forgerock.openidm.util.DOMUtil;
import com.forgerock.openidm.util.DebugUtil;
import com.forgerock.openidm.util.ObjectTypeUtil;
import com.forgerock.openidm.util.XPathUtil;
import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.util.patch.PatchException;
import com.forgerock.openidm.util.patch.PatchXml;
import com.forgerock.openidm.xml.ns._public.common.common_1.AccountConstructionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.AccountShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.AttributeDescriptionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectFactory;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyConstructionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyModificationType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyModificationTypeType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowType.Attributes;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.SchemaHandlingType.AccountType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserTemplateType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ValueConstructionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ValueConstructionType.Value;
import com.forgerock.openidm.xml.ns._public.common.common_1.ValueFilterType;
import com.forgerock.openidm.xml.ns._public.common.common_1.VariableDefinitionType;
import com.forgerock.openidm.xml.ns._public.model.model_1.FaultMessage;
import com.forgerock.openidm.xml.ns._public.model.model_1.ModelPortType;
import com.forgerock.openidm.xml.schema.ExpressionHolder;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import com.forgerock.openidm.xml.schema.UserTypeUtil;
import com.forgerock.openidm.xml.schema.ValueAssignmentHolder;
import com.forgerock.openidm.xml.schema.XPathSegment;
import com.forgerock.openidm.xml.schema.XPathType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 
 *
 * @author Igor Farinic
 * @version $Revision$ $Date$
 * @since 0.1
 */
@Component
public class SchemaHandling extends XPathUtil {

    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SchemaHandling.class);
    public static final String TAG_VALUE_EXPRESSION = "valueExpression";
    public static final String TAG_VALUE = "value";
    public static final String TAG_ATTRIBUTE_REF = "ref";
    @Autowired
    private ModelPortType model;
    @Autowired
    private FilterManager filterManager;

    //TODO: ineffective, we should cache result of resource schema parsing
    private ResourceSchema createResourceSchema(ResourceType resourceType) throws SchemaHandlingException {
        //TODO: DOMToSchemaParser and related classes - are util classes, should be moved out of provisioning
        DOMToSchemaParser parser = new DOMToSchemaParser();
        Element elementResourceSchema = null;
        if (null != resourceType.getSchema() && !resourceType.getSchema().getAny().isEmpty()) {
            for (Element e : resourceType.getSchema().getAny()) {
                if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(e.getNamespaceURI()) && "schema".equals(e.getLocalName())) {
                    elementResourceSchema = e;
                    break;
                }
            }
        }
        ResourceSchema resourceSchema = parser.getSchema(elementResourceSchema);
        return resourceSchema;
    }

    private void evaluateAndApplyOutboundExpressionValues(UserType user, AccountShadowType accountShadow, ResourceType resource) throws SchemaHandlingException {
        logger.debug("SCHEMA HANDLING: Account before processing of schema handling: {}", DebugUtil.prettyPrint(accountShadow));
        logger.trace("Account before processing of schema handling: {}", JAXBUtil.silentMarshalWrap(accountShadow, SchemaConstants.I_ACCOUNT_TYPE));

        Validate.notNull(user, "user is null");
        Validate.notNull(accountShadow, "accountShadow is null");

        AccountType accountType = ObjectTypeUtil.getAccountTypeDefinitionFromSchemaHandling(accountShadow, resource);
        List<AttributeDescriptionType> attributesHandling = accountType.getAttribute();

        Map<QName, Variable> variables = getDefaultXPathVariables(user, accountShadow);

        ResourceObjectDefinition objectDefinition = getResourceObjectDefinition(resource);

        for (AttributeDescriptionType attributeHandling : attributesHandling) {
            ResourceAttributeDefinition attributeDefinition = objectDefinition.getAttributeDefinition(attributeHandling.getRef());
            if (null != attributeDefinition) {
                insertUserDefinedVariables(attributeHandling, variables);
                processOutboundSection(attributeHandling, variables, accountShadow);
            } else {
                logger.warn("Attribute {} defined in schema handling is not defined in the resource schema (oid={}). Attribute was not processed", attributeHandling.getRef(), resource.getOid());
            }
        }

        logger.debug("SCHEMA HANDLING: Account with applied schema handling: {}", DebugUtil.prettyPrint(accountShadow));
        logger.trace("Account with applied schema handling: {}", JAXBUtil.silentMarshalWrap(accountShadow, SchemaConstants.I_ACCOUNT_TYPE));

    }

    private UserType evaluateAndApplyInboundExpressionValues(UserType user, ResourceObjectShadowType accountShadow) throws SchemaHandlingException {
        logger.debug("INBOUND: User before processing of inbound expressions: {}", DebugUtil.prettyPrint(user,true));
        logger.trace("User before processing of inbound expressions: {}", JAXBUtil.silentMarshalWrap(user, SchemaConstants.I_USER_TYPE));

        Validate.notNull(user, "user is null");
        Validate.notNull(accountShadow, "accountShadow is null");

        Element domUser = null;
        try {
            domUser = JAXBUtil.objectTypeToDom(user, null);
        } catch (JAXBException ex) {
            throw new SchemaHandlingException(ex);
        }

        //Prerequisite: resourceRef was resolved to resource
        ResourceType resource = accountShadow.getResource();
        AccountType accountType = ObjectTypeUtil.getAccountTypeDefinitionFromSchemaHandling(accountShadow, resource);
        List<AttributeDescriptionType> attributesHandling = accountType.getAttribute();

        Map<QName, Variable> variables = getDefaultXPathVariables(user, accountShadow);

        ResourceObjectDefinition objectDefinition = getResourceObjectDefinition(resource);

        for (AttributeDescriptionType attributeHandling : attributesHandling) {
            ResourceAttributeDefinition attributeDefinition = objectDefinition.getAttributeDefinition(attributeHandling.getRef());
            if (null != attributeDefinition) {
                domUser = (Element) processInboundSection(attributeHandling, variables, accountShadow, domUser, user);
            } else {
                logger.warn("Attribute {} defined in schema handling is not defined in the resource schema (oid={}). Attribute was not processed", attributeHandling.getRef(), resource.getOid());
            }

        }

        try {
            JAXBElement<UserType> jaxbUser = (JAXBElement<UserType>) JAXBUtil.unmarshal(DOMUtil.serializeDOMToString(domUser));
            user = jaxbUser.getValue();
        } catch (JAXBException ex) {
            throw new SchemaHandlingException(ex);
        }
        logger.debug("INBOUND: User with applied inbound expressions: {}", DebugUtil.prettyPrint(user,true));
        logger.trace("User with applied inbound expressions: {}", JAXBUtil.silentMarshalWrap(user, SchemaConstants.I_USER_TYPE));

        return user;
    }

    private String evaluateExpression(Map<QName, Variable> variables, ExpressionHolder expressionHolder) {
        return (String) evaluateExpr(variables, expressionHolder, XPathConstants.STRING);
    }

    private void applyValue(AccountShadowType account, QName accountAttributeName, String accountAttributeValue) {
        Validate.notNull(account, "account is null");
        Validate.notNull(accountAttributeName, "accountAttributeName is null");

        //TODO: multi value attributes are not supported, yet
        //TODO: attributes could have only simple values
        logger.trace("Account's attribute '{}' assign value '{}'", accountAttributeName, accountAttributeValue);
        String namespace = accountAttributeName.getNamespaceURI();
        String localName = accountAttributeName.getLocalPart();
        logger.trace("Account's attribute namespace = '{}' and name = '{}'", new Object[]{namespace, localName});

        AccountShadowType.Attributes attrs = account.getAttributes();
        if (null == attrs) {
            attrs = new AccountShadowType.Attributes();
            account.setAttributes(attrs);
        }
        List<Element> attributes = attrs.getAny();

        for (Element attribute : attributes) {
            logger.trace("attribute: namespaceURI = '{}', localName = '{}'", attribute.getNamespaceURI(), attribute.getLocalName());
            if (localName.equals(attribute.getLocalName()) && namespace.equals(attribute.getNamespaceURI())) {
                attribute.setTextContent(accountAttributeValue);
                logger.trace("Changed account's attribute '{}' value to '{}'", accountAttributeName, accountAttributeValue);
                return;
            }
        }

        //no value was set yet for the attribute, so create new attribute with value
        logger.trace("Account's attribute '{}' was not set, yet", accountAttributeName);
        Document doc = DOMUtil.getDocument();
        Element element;
        if (StringUtils.isNotEmpty(accountAttributeName.getNamespaceURI())) {
            element = doc.createElementNS(namespace, localName);
        } else {
            element = doc.createElementNS(SchemaConstants.NS_C, accountAttributeName.getLocalPart());
        }
        element.setTextContent(accountAttributeValue);
        attributes.add(element);
        logger.trace("Created account's attribute '{}' with value '{}'", accountAttributeName, accountAttributeValue);

    }

    private Map<QName, Variable> getDefaultXPathVariables(UserType user, ResourceObjectShadowType account) {
        Map<QName, Variable> variables = new HashMap<QName, Variable>();
        try {
            ObjectFactory of = new ObjectFactory();

            if (user != null) {
                // Following code is wrong, but it works
                JAXBElement<ObjectType> userJaxb = of.createObject(user);
                Document userDoc = DOMUtil.parseDocument(JAXBUtil.marshal(userJaxb));
                variables.put(SchemaConstants.I_USER, new Variable(userDoc.getFirstChild(), false));

// Following code should work, but it fails. FIXME
//                JAXBElement<ObjectType> userJaxb = of.createObject(user);
//                Element userEl = JAXBUtil.objectTypeToDom(userJaxb.getValue(), null);
//                variables.put(SchemaConstants.I_USER, new Variable(userEl, false));

            }

            if (account != null) {
                JAXBElement<ObjectType> accountJaxb = of.createObject(account);
                Element accountEl = JAXBUtil.objectTypeToDom(accountJaxb.getValue(), null);
                variables.put(SchemaConstants.I_ACCOUNT, new Variable(accountEl, false));
            }

            if (account != null && account.getResource() != null) {
                JAXBElement<ObjectType> resourceJaxb = of.createObject(account.getResource());
                Element resourceEl = JAXBUtil.objectTypeToDom(resourceJaxb.getValue(), null);
                variables.put(SchemaConstants.I_RESOURCE, new Variable(resourceEl, false));
            }

        } catch (JAXBException ex) {
            throw new IllegalArgumentException(ex);
        }
        return variables;
    }

    private ValueConstructionType getOutbound(AttributeDescriptionType attribute) {
        Validate.notNull(attribute, "attribute is null");
        return attribute.getOutbound();
    }

    private List<ValueAssignmentHolder> getInbound(AttributeDescriptionType attribute) {
        Validate.notNull(attribute, "attribute is null");
        List<ValueAssignmentHolder> result = new ArrayList<ValueAssignmentHolder>();
        for (Element e : attribute.getInbound()) {
            result.add(new ValueAssignmentHolder(e));
        }
        return result;
    }

    private ExpressionHolder extractExpressionHolder(ValueConstructionType outbound) {
        Validate.notNull(outbound, "outbound is null");
        if (null == outbound.getValueExpression()) {
            return null;
        }
        ExpressionHolder expression = new ExpressionHolder(outbound.getValueExpression());
        return expression;
    }

    private String extractValue(AttributeDescriptionType attributeWithValueElement) {
        Validate.notNull(attributeWithValueElement, "attributeWithValueElement is null");

        JAXBElement<Value> jaxbValue = attributeWithValueElement.getOutbound().getValue();
        if (null == jaxbValue) {
            return null;
        }

        StringBuffer serializedContent = new StringBuffer();
        for (Object content : jaxbValue.getValue().getContent()) {
            if (content instanceof String) {
                serializedContent.append(content);
            } else {
                throw new IllegalArgumentException("Unsupported content for value element in attribute's handling outbound section for attribute: " + attributeWithValueElement.getRef());
            }
        }
        return serializedContent.toString();
    }

    private QName extractAttributeName(AttributeDescriptionType attribute) {
        Validate.notNull(attribute, "attribute is null");

        QName attributeName = attribute.getRef();
        return attributeName;
    }

    private ResourceObjectDefinition getResourceObjectDefinition(ResourceType resource) throws SchemaHandlingException {
        ResourceSchema resourceSchema = createResourceSchema(resource);
        QName objectDefinitionQName = new QName(resourceSchema.getResourceNamespace(), "Account");
        ResourceObjectDefinition objectDefinition = resourceSchema.getObjectDefinition(objectDefinitionQName);
        if (null == objectDefinition) {
            logger.error("Object definition with QName {} not found in the resource schema (oid={})", objectDefinitionQName, resource.getOid());
            throw new SchemaHandlingException("Object definition with QName " + objectDefinitionQName + " not found in the resource schema (oid=" + resource.getOid() + ")");
        }
        return objectDefinition;
    }

    private XPathType getXPathForAccountsAttribute(QName accountAttributeName) {
        Validate.notNull(accountAttributeName, "accountAttributeName is null");

        //construct XPath that points to account's attribute
        List<XPathSegment> segments = new ArrayList<XPathSegment>();
        XPathSegment segment = new XPathSegment(SchemaConstants.I_ATTRIBUTES);
        segments.add(segment);
        segment = new XPathSegment(accountAttributeName);
        segments.add(segment);
        XPathType xpathType = new XPathType(segments);
        return xpathType;
    }

    private void processOutboundSection(AttributeDescriptionType attributeHandling, Map<QName, Variable> variables, AccountShadowType accountShadow) throws SchemaHandlingException {
        Validate.notNull(attributeHandling, "attributeHandling is null");
        Validate.notNull(variables, "variables are null");
        Validate.notEmpty(variables, "variables are empty");
        Validate.notNull(accountShadow, "accountShadow is null");

        QName accountAttributeName = extractAttributeName(attributeHandling);
        logger.trace("Start outbound processing of attribute handling for attribute '{}'", accountAttributeName);
        String accountAttributeValue;
        ValueConstructionType outbound = getOutbound(attributeHandling);
        if (null == outbound) {
            //outbound is optional
            return;
        }

        ExpressionHolder expressionHolder = extractExpressionHolder(outbound);
        XPathType xpathType = getXPathForAccountsAttribute(accountAttributeName);

        if (isApplicablePropertyConstruction(attributeHandling.getOutbound().isDefault(), xpathType, variables, accountShadow)) {
            if (null != expressionHolder && !StringUtils.isEmpty(expressionHolder.getExpressionAsString())) {
                accountAttributeValue = evaluateExpression(variables, expressionHolder);
                if (logger.isDebugEnabled()) {
                    logger.debug("OUTBOUND: expression '{}' for attribute {}, resource {}, was evaluated to '{}'", new Object[]{expressionHolder.getExpressionAsString(), accountAttributeName, DebugUtil.resourceFromShadow(accountShadow), accountAttributeValue});
                    if (variables != null) {
                        for (Entry<QName, Variable> entry : variables.entrySet()) {
                            logger.debug("OUTBOUND:  variable {}: {}", entry.getKey(), entry.getValue());
                        }
                    }
                }
            } else {
                accountAttributeValue = extractValue(attributeHandling);
                logger.trace("Account's attribute value is '{}'", accountAttributeValue);
            }
            applyValue(accountShadow, accountAttributeName, accountAttributeValue);
        }
        logger.trace("Finished outbound processing of attribute handling for attribute '{}'", accountAttributeName);
    }

    private List<Element> extractAttributeValuesFromAccountShadow(ResourceObjectShadowType accountShadow, AttributeDescriptionType attributeHandling) {
        Validate.notNull(accountShadow, "accountShadow is null");
        Validate.notNull(attributeHandling, "attributeHandling is null");

        QName attributeName = extractAttributeName(attributeHandling);
        Attributes attributes = accountShadow.getAttributes();
        List<Element> result = new ArrayList<Element>();

        if (null == attributes) {
            return result;
        }

        for (Element accountAttribute : attributes.getAny()) {
            if (StringUtils.equals(attributeName.getNamespaceURI(), accountAttribute.getNamespaceURI()) && StringUtils.equals(attributeName.getLocalPart(), accountAttribute.getLocalName())) {
                result.add(accountAttribute);
            }
        }

        return result;

    }

    private XPathType constructParentXpath(XPathType xpathType) {
        Validate.notNull(xpathType, "xpathType is null");
        List<XPathSegment> segments = xpathType.toSegments();
        List<XPathSegment> parentSegments = segments.subList(0, segments.size() - 1);
        XPathType parentXpath = new XPathType(parentSegments);

        return parentXpath;
    }

    private QName getUserPropertyQNameFromXPathType(XPathType xpathType) {
        Validate.notNull(xpathType, "xpathType is null");
        List<XPathSegment> segments = xpathType.toSegments();
        XPathSegment lastSegment = segments.get(segments.size() - 1);
        return lastSegment.getQName();
    }

    private Node processInboundSection(AttributeDescriptionType attributeHandling, Map<QName, Variable> variables, ResourceObjectShadowType accountShadow, Node domUser, UserType user) throws SchemaHandlingException {
        Validate.notNull(attributeHandling, "attributeHandling is null");
        Validate.notNull(variables, "variables are null");
        Validate.notEmpty(variables, "variables are empty");
        Validate.notNull(accountShadow, "accountShadow is null");
        Validate.notNull(domUser, "domUser is null");
        QName accountAttributeName = extractAttributeName(attributeHandling);
        logger.trace("Start inbound processing of attribute handling for attribute '{}'", accountAttributeName);

        List<ValueAssignmentHolder> inbounds = getInbound(attributeHandling);

        //inbound is optional
        if (null == inbounds || inbounds.size() == 0) {
            logger.trace("Finished inbound processing of attribute handling for attribute '{}' - nothing to process", accountAttributeName);
            return domUser;
        }

        for (ValueAssignmentHolder inbound : inbounds) {

            if (null != inbound.getSource()) {
                throw new SchemaHandlingException("Inbound attribute where source is not null is not supported yet (OPENIDM-285)");
            }

            List<ValueFilterType> filters = inbound.getFilter();

            XPathType xpathType = inbound.getTarget();

            NodeList matchedNodes;
            try {
                matchedNodes = matchedNodesByXPath(xpathType, variables, domUser);
            } catch (XPathExpressionException ex) {
                throw new SchemaHandlingException(ex);
            }

            //I. extract new nodesAttributesFromAccountShadow with values from accountShadow
            List<Element> nodesAttributesFromAccountShadow = extractAttributeValuesFromAccountShadow(accountShadow, attributeHandling);

            Node parentNode;
            QName userPropertyQName;

            if (matchedNodes.getLength() == 0) {
                //if the value does not exists
                logger.warn("No nodes were matched by xpath '{}' in context of namespaces '{}' and variables '{}'", new Object[]{xpathType.getXPath(), xpathType.getNamespaceMap(), variables});
                //if no matches found, then we will search for the parent of the element and add new node to it

                XPathType parentXpath = constructParentXpath(xpathType);
                try {
                    matchedNodes = matchedNodesByXPath(parentXpath, variables, domUser);
                    parentNode = matchedNodes.item(0);

                    if (null == parentNode) {
                        //if there is no extension section at all and we have extension attributes then we will create it
                        List<XPathSegment> segments = parentXpath.toSegments();
                        XPathType relativeParentXPath = null;
                        //if first element in the xpath is variable, we assume it is referencing the doc and we will replace it
                        if (segments != null && !segments.isEmpty() && segments.get(0).isVariable()) {
                            //we will remove the segment, because DOMUtil.createNodesDefinedByXPath expects relative xpath
                            relativeParentXPath = new XPathType(segments.subList(1, segments.size()));
                            Document doc = DOMUtil.getDocument();
                            doc.adoptNode(domUser);
                            doc.appendChild(domUser);
                            DOMUtil.createNodesDefinedByXPath(doc, relativeParentXPath);
                            domUser = doc.getFirstChild();
                            Validate.notNull(domUser, "null value assigned to domUser");
                            variables.put(SchemaConstants.I_USER, new Variable(domUser, false));
                            matchedNodes = matchedNodesByXPath(parentXpath, variables, domUser);
                        }
                    }
                } catch (XPathExpressionException ex) {
                    throw new SchemaHandlingException(ex);
                }
                parentNode = matchedNodes.item(0);
                //TODO: reimplement
                boolean wasEmpty = false;
                if (null == parentNode) {
                    parentNode = domUser;
                    wasEmpty = true;
                }
                userPropertyQName = getUserPropertyQNameFromXPathType(xpathType);
                //II. transform tag names from account's attribute to user tags
                List<Element> newNodes = transformAccountAttributeToUserProperty(nodesAttributesFromAccountShadow, userPropertyQName, filters);
                //III.b set new values in transformed nodesAttributesFromAccountShadow to user
                DOMUtil.addChildNodes(parentNode, newNodes);
                //IV. set modified user
                if (wasEmpty) {
                    domUser = parentNode;
                    Validate.notNull(domUser, "null value assigned to domUser");
                } else {
                    domUser = parentNode.getOwnerDocument().getFirstChild();
                    Validate.notNull(domUser, "null value assigned to domUser");
                }
                logger.debug("INBOUND: expression [attribute:{}] =(new)=> [user:{},path:{}] {}",new Object[]{ attributeHandling.getName(), DebugUtil.prettyPrint(user), xpathType, DebugUtil.prettyPrint(newNodes) });

            } else {
                userPropertyQName = new QName(matchedNodes.item(0).getNamespaceURI(), matchedNodes.item(0).getLocalName());
                parentNode = matchedNodes.item(0).getParentNode();
                Validate.notNull(parentNode, "parentNode is null");
                //II. transform tag names from account's attribute to user tags
                List<Element> newNodes = transformAccountAttributeToUserProperty(nodesAttributesFromAccountShadow, userPropertyQName, filters);
                //III.a replace values in transformed nodesAttributesFromAccountShadow to user
                DOMUtil.replaceChildNodes(parentNode, newNodes);
                //IV. set modified user
                domUser = parentNode.getOwnerDocument().getFirstChild();
                logger.debug("INBOUND: expression [attribute:{}] =(replace)=> [user:{},path:{}] {}",new Object[]{ attributeHandling.getName(), DebugUtil.prettyPrint(user), xpathType, DebugUtil.prettyPrint(newNodes) });
                Validate.notNull(domUser, "null value assigned to domUser");
            }

        }
        logger.trace("Finished inbound processing of attribute handling for attribute '{}'", accountAttributeName);
//        if (null != domUser) {
//            domUser.normalize();
//        }

        return domUser;
    }

    private Node applyFilters(List<ValueFilterType> filters, Node node) {
        if (null == filters || filters.isEmpty()) {
            //no filters defined return immediately
            logger.trace("No filters defined");
            return node;
        }

        Node returnNode = node;
        for (ValueFilterType filterType : filters) {
            Filter filter = filterManager.getFilterInstance(filterType.getType(), filterType.getAny());
            if (null != filter) {
                returnNode = filter.apply(returnNode);
            } else {
                logger.warn("Filter not found for uri {}", filterType.getType());
            }
        }

        return returnNode;
    }

    private List<Element> transformAccountAttributeToUserProperty(List<Element> nodes, QName propertyQName, List<ValueFilterType> filters) throws DOMException {
        Validate.notNull(nodes, "nodes are null");
        Validate.notNull(propertyQName, "propertyQName are null");

        List<Element> newNodes = new ArrayList<Element>();
        Document doc = DOMUtil.getDocument();
        for (Element element : nodes) {
            Element transformedElement = doc.createElementNS(propertyQName.getNamespaceURI(), propertyQName.getLocalPart());
            //transformedElement.setPrefix("attr" + (new Random()).nextInt(Integer.MAX_VALUE));
            if (StringUtils.isNotEmpty(propertyQName.getPrefix())) {
                transformedElement.setPrefix(propertyQName.getPrefix());
            } else {
                transformedElement.setPrefix(propertyQName.getLocalPart());
            }
            Node valueNode = applyFilters(filters, element.getFirstChild());
            if (null != valueNode) {
                transformedElement.getOwnerDocument().adoptNode(valueNode);
                transformedElement.appendChild(valueNode);
            }
            newNodes.add(transformedElement);
        }
        return newNodes;
    }

    private boolean isApplicablePropertyConstruction(boolean isDefault, XPathType xpathType, Map<QName, Variable> variables, ObjectType objectType) throws SchemaHandlingException {
        Validate.notNull(xpathType, "xpathType is null");
        Validate.notNull(variables, "variables are null");
        Validate.notNull(objectType, "objectType is null");

        if (isDefault) {
            QName elementQName = null;
            if (objectType instanceof UserType) {
                elementQName = SchemaConstants.I_USER_TYPE;
            } else if (objectType instanceof AccountShadowType) {
                elementQName = SchemaConstants.I_ACCOUNT_TYPE;
            } else {
                throw new SchemaHandlingException("Provided unsupported object type: " + objectType.getClass());
            }

            try {
                Node domObject = JAXBUtil.jaxbToDom(objectType, elementQName, DOMUtil.getDocument());
                NodeList nodes;
                try {
                    nodes = matchedNodesByXPath(xpathType, variables, domObject);
                } catch (XPathExpressionException ex) {
                    throw new SchemaHandlingException(ex);
                }
                if (null != nodes && nodes.getLength() > 0) {
                    return false;
                }
            } catch (JAXBException ex) {
                throw new SchemaHandlingException("Couldn't transform jaxb object '" +
                        objectType + "' to dom.", ex);
            }
        }

        return true;
    }

    public UserType applyUserTemplate(UserType user, UserTemplateType userTemplate) throws PatchException, SchemaHandlingException {
        Validate.notNull(user, "user is null");
        Validate.notNull(userTemplate, "userTemplate is null");

        logger.debug("USER TEMPLATE: Processing template {} for user {}",DebugUtil.prettyPrint(userTemplate),DebugUtil.prettyPrint(user));

        ObjectModificationType objectModifications = new ObjectModificationType();
        objectModifications.setOid(user.getOid());

        // Process propertyConstruction

        List<PropertyModificationType> changes = new ArrayList<PropertyModificationType>();
        for (PropertyConstructionType pct : userTemplate.getPropertyConstruction()) {
            XPathType xpathType = new XPathType(pct.getProperty());
            XPathType parentXpathType = constructParentXpath(xpathType);
            QName propertyQName = getUserPropertyQNameFromXPathType(xpathType);

            ExpressionHolder expression = new ExpressionHolder(pct.getValueConstruction().getValueExpression());
            //we have to re-register variables, because there could be dependencies in property construction definitions
            Map<QName, Variable> variables = getDefaultXPathVariables(user, null);
            String evaluatedExpression = evaluateExpression(variables, expression);

            if (isApplicablePropertyConstruction(pct.getValueConstruction().isDefault(), xpathType, variables, user)) {
                PropertyModificationType change = ObjectTypeUtil.createPropertyModificationType(PropertyModificationTypeType.replace, parentXpathType, propertyQName, evaluatedExpression);
                changes.add(change);
            }
        }

        // Process accountConstruction

        for (AccountConstructionType accountConstr : userTemplate.getAccountConstruction()) {
            String resourceOid;
            if (accountConstr.getResource()!=null) {
                resourceOid = accountConstr.getResource().getOid();
            } else if (accountConstr.getResourceRef()!=null) {
                resourceOid = accountConstr.getResourceRef().getOid();
            } else {
                logger.error("Account construction in user template (OID:{}) must have resource or resourceRef dedined.",userTemplate.getOid());
                throw new IllegalArgumentException("Account construction in user template (OID:"+userTemplate.getOid()+") must have resource or resourceRef dedined.");
            }

            // We assume only default account type now. Ignore everything else.

            // check if the account exists already
            if (UserTypeUtil.findAccountRef(user, resourceOid)!=null) {
                logger.debug("USER TEMPLATE: Account on resource {} already exists for user {}, skipping",resourceOid,user.getOid());
                continue;
            }

            AccountShadowType account = new AccountShadowType();
            ObjectReferenceType resourceRef = new ObjectReferenceType();
            resourceRef.setOid(resourceOid);
            resourceRef.setType(SchemaConstants.I_RESOURCE_TYPE);
            account.setResourceRef(resourceRef);
            // There is no OID in the account, that means a new account will be created
            PropertyModificationType change = ObjectTypeUtil.createPropertyModificationType(PropertyModificationTypeType.add, null, SchemaConstants.I_ACCOUNT, account);
            changes.add(change);
        }

        objectModifications.getPropertyModification().addAll(changes);

        String stringUser = (new PatchXml()).applyDifferences(objectModifications, user);

        try {
            return (UserType) ((JAXBElement<UserType>) JAXBUtil.unmarshal(stringUser)).getValue();
        } catch (JAXBException ex) {
            throw new SchemaHandlingException("Couldn't create unmarshall UserType from string.", ex);
        }
    }

    public UserType applyInboundSchemaHandlingOnUser(UserType user, ResourceObjectShadowType resourceObjectShadow) throws SchemaHandlingException {
        Validate.notNull(user, "user object is null");
        Validate.notNull(resourceObjectShadow, "resourceObjectShadow is null");
        Validate.notNull(resourceObjectShadow.getResource(), "resourceObjectShadow.getResource() is null");

        user = evaluateAndApplyInboundExpressionValues(user, resourceObjectShadow);
        return user;
    }

    public ResourceObjectShadowType applyOutboundSchemaHandlingOnAccount(UserType user,
            ResourceObjectShadowType resourceObjectShadow, ResourceType resource) throws SchemaHandlingException {
        Validate.notNull(user, "user is null");
        Validate.notNull(resourceObjectShadow, "resourceObjectShadow is null");

        AccountShadowType accountShadow = (AccountShadowType) resourceObjectShadow;
        evaluateAndApplyOutboundExpressionValues(user, accountShadow, resource);

        return accountShadow;
    }

    public boolean confirmUser(UserType user, ResourceObjectShadowType resourceObjectShadow, ExpressionHolder expression) {
        Validate.notNull(user, "user object is null");
        Validate.notNull(resourceObjectShadow, "resourceObjectShadow is null");
        Validate.notNull(expression, "expression is null");

        Map<QName, Variable> variables = getDefaultXPathVariables(user, resourceObjectShadow);
        String confirmed = evaluateExpression(variables, expression);
        return new Boolean(confirmed);
    }

    public String evaluateCorrelationExpression(ResourceObjectShadowType resourceObjectShadow, ExpressionHolder expression) {
        Validate.notNull(resourceObjectShadow);
        Validate.notNull(expression);

        Map<QName, Variable> variables = getDefaultXPathVariables(null, resourceObjectShadow);
        if (logger.isDebugEnabled()) {
            for (Entry<QName, Variable> entry : variables.entrySet()) {
                logger.debug("CORRELATION: Variable {}: {}", entry.getKey(), entry.getValue());
            }
        }
        return evaluateExpression(variables, expression);
    }

    private void insertUserDefinedVariables(AttributeDescriptionType attributeHandling, Map<QName, Variable> variables) throws SchemaHandlingException {
        //clear old variables defined by user
        for (Iterator<Entry<QName, Variable>> i = variables.entrySet().iterator(); i.hasNext();) {
            Entry<QName, Variable> entry = i.next();
            if (entry.getValue().isUserDefined())
            {
                i.remove();
            }
        }

        // TODO: this code has to be refactored to provide better errors to the user
        // (e.g. "object not found" instead of "cannot resolve")

        if (attributeHandling.getOutbound() == null) {
            return;
        }

        List<VariableDefinitionType> variableDefinitions = attributeHandling.getOutbound().getVariable();
        for (VariableDefinitionType varDef : variableDefinitions) {
            Object object = varDef.getValue();
            if (object == null) {
                ObjectReferenceType reference = varDef.getObjectRef();
                // This throws exceptions while the later code is trying to
                // skip errors. So how it really should work?
                ObjectType objectType = findObjectByOid(reference.getOid());
                if (objectType != null) {
                    // We have got JAXB object here, but the code expects DOM
                    try {
                        object = JAXBUtil.objectTypeToDom(objectType, null);
                    } catch (JAXBException ex) {
                        logger.warn("Variable '{}' couldn't be serialized to XML, skipping. Error: {}", varDef.getName(), ex.getMessage());
                        continue;
                    }
                }
            }

            if (object != null) {
                variables.put(varDef.getName(), new Variable(object));
            } else {
                // TODO: How to get attribute and resource name here?
                logger.warn("Variable '{}' couldn't be resolved, skipping.", varDef.getName());
            }
        }
    }

    private ObjectType findObjectByOid(String oid) throws SchemaHandlingException {
        if (oid == null) {
            return null;
        }

        if (model == null) {
            return null;
        }

        try {
            return model.getObject(oid, new PropertyReferenceListType()).getObject();
        } catch (FaultMessage ex) {
            throw new SchemaHandlingException("Couldn't resolve oid '" + oid + "', reason: " +
                    ex.getMessage(), ex, ex.getFaultInfo());
        }
    }
}
