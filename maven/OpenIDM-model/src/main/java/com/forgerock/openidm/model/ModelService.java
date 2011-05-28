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
package com.forgerock.openidm.model;

import com.forgerock.openidm.api.logging.Trace;
import com.forgerock.openidm.logging.TraceManager;
import com.forgerock.openidm.model.xpath.SchemaHandling;
import com.forgerock.openidm.model.xpath.SchemaHandlingException;
import com.forgerock.openidm.util.Base64;
import com.forgerock.openidm.util.DOMUtil;
import com.forgerock.openidm.util.DebugUtil;
import com.forgerock.openidm.util.ObjectTypeUtil;
import com.forgerock.openidm.util.QNameUtil;
import com.forgerock.openidm.util.RandomString;
import com.forgerock.openidm.util.Utils;
import com.forgerock.openidm.util.diff.CalculateXmlDiff;
import com.forgerock.openidm.util.diff.DiffException;
import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.util.patch.PatchException;
import com.forgerock.openidm.util.patch.PatchXml;
import com.forgerock.openidm.xml.ns._public.common.common_1.*;
import com.forgerock.openidm.xml.ns._public.common.common_1.SchemaHandlingType.AccountType;
import com.forgerock.openidm.xml.ns._public.common.common_1.SchemaHandlingType.AccountType.Credentials;
import com.forgerock.openidm.xml.ns._public.model.model_1.FaultMessage;
import com.forgerock.openidm.xml.ns._public.model.model_1.ModelPortType;
import com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.ProvisioningPortType;
import com.forgerock.openidm.xml.ns._public.repository.repository_1.RepositoryPortType;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import com.forgerock.openidm.xml.schema.XPathSegment;
import com.forgerock.openidm.xml.schema.XPathType;
import java.util.ArrayList;
import java.util.List;
import javax.ejb.Stateless;
import javax.interceptor.Interceptors;
import javax.jws.WebService;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ejb.interceptor.SpringBeanAutowiringInterceptor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * TODO
 *
 * @author Igor Farinic
 * @version $Revision$ $Date$
 * @since 0.1
 */
@WebService(serviceName = "modelService", portName = "modelPort", endpointInterface = "com.forgerock.openidm.xml.ns._public.model.model_1.ModelPortType", targetNamespace = "http://openidm.forgerock.com/xml/ns/public/model/model-1.wsdl")// , wsdlLocation = "META-INF/wsdl/xml/ns/private/model/modelWrapper.wsdl")
@Stateless
@Interceptors(SpringBeanAutowiringInterceptor.class)
@Service
public class ModelService implements ModelPortType {

    @Autowired(required = true)
    private ProvisioningPortType provisioningService;
    @Autowired(required = true)
    private RepositoryPortType repositoryService;
    private static final transient Trace logger = TraceManager.getTrace(ModelService.class);
    @Autowired(required = true)
    private SchemaHandling schemaHandling;

    @Override
    public java.lang.String addObject(ObjectContainerType objectContainer) throws FaultMessage {
        logger.info("### MODEL # Enter addObject({})", DebugUtil.prettyPrint(objectContainer));

        ObjectType object = objectContainer.getObject();
        String name = object.getName();
        if (name == null || name.isEmpty()) {
            throw createFaultMessage("Object (" + object.getClass().getSimpleName() + ") with oid '" +
                    object.getOid() + "' doesn't have required 'name' attribute defined.",
                    IllegalArgumentFaultType.class, false, null, null);
        }

        String result;
        if (isManagedByProvisioning(object)) {
            result = addProvisioningObject(object);
        } else {
            if (object instanceof UserType) {
                UserType user = (UserType) object;
                preprocessUserType(user);
            }

            try {
                result = repositoryService.addObject(objectContainer);
            } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
                logger.error("### MODEL # Fault addObject(..): Repository WS client failed for method addObject : ", ex);
                throw createFaultMessage("Repository invocation failed (addObject)", ex.getFaultInfo(), ex, null);
            }
        }

        logger.info("### MODEL # Exit addObject(..): {}", result);
        return result;
    }

    private void preprocessUserType(UserType user) throws FaultMessage {
        List<AccountShadowType> accounts = user.getAccount();
        List<ObjectReferenceType> references = new ArrayList<ObjectReferenceType>();
        //we're looking for accounts (now only resource references) which have to be created after user is saved
        List<AccountShadowType> accountsToCreate = new ArrayList<AccountShadowType>();
        for (AccountShadowType account : accounts) {
            ObjectReferenceType ref = account.getResourceRef();
            if (account.getName() == null && account.getOid() == null && ref != null &&
                    SchemaConstants.I_RESOURCE_TYPE.equals(ref.getType())) {
                references.add(ref);
                accountsToCreate.add(account);
            }
        }
        user.getAccount().removeAll(accountsToCreate);

        //create new account for every account resource ref
        List<ObjectReferenceType> newAccountRefs = new ArrayList<ObjectReferenceType>();
        AccountShadowType account;
        for (ObjectReferenceType ref : references) {
            try {
                account = new AccountShadowType();
                account.setName(ref.getOid() + "-" + user.getName());
                account.setResourceRef(ref);

                ResourceType resource = resolveResource(ref.getOid());
                account.setObjectClass(new QName(resource.getNamespace(), "Account"));

                account = (AccountShadowType) schemaHandling.applyOutboundSchemaHandlingOnAccount(user, account, resource);

                ObjectContainerType container = new ObjectContainerType();
                container.setObject(account);
                String oid = addObject(container);

                ObjectReferenceType accountRef = new ObjectReferenceType();
                accountRef.setOid(oid);
                accountRef.setType(SchemaConstants.I_ACCOUNT_TYPE);
                newAccountRefs.add(accountRef);
            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                String message = ex.getMessage();
                if (ex.getFaultInfo() != null && ex.getFaultInfo().getMessage() != null) {
                    message = ex.getFaultInfo().getMessage();
                }
                logger.error("### MODEL # preprocessUserType(), couldnt' create account on resource {}, " +
                        "provisioning problem, reason: {}", new Object[]{ref.getOid(), message});
            } catch (SchemaHandlingException ex) {
                logger.error("### MODEL # preprocessUserType(), couldnt' create account on resource {}, " +
                        "outbound schema handling problem, reason: {}", new Object[]{ref.getOid(), ex.getMessage()});
            }
        }
        //update account refs for user
        user.getAccountRef().addAll(newAccountRefs);
    }

    private String addProvisioningObject(ObjectType object) throws FaultMessage {
        try { // Call Web Service Operation
            if (object instanceof AccountShadowType) {
                AccountShadowType account = (AccountShadowType) object;
                int randomPasswordLength = getRandomPasswordLength(account);
                if (randomPasswordLength != -1) {
                    generatePassword(account, randomPasswordLength);
                }
            }

            OperationalResultType operationalResult = new OperationalResultType();
            Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);

            ScriptsType scripts = getScripts(object);
            ObjectContainerType container = new ObjectContainerType();
            container.setObject(object);
            java.lang.String result = provisioningService.addObject(container, scripts, holder);
            return result;
        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
            logger.error("### MODEL # Fault addObject(..): Provisioning WS client failed for method addObject : ", ex);
            throw createFaultMessage("Provisioning invocation failed (addObject)", ex.getFaultInfo(), ex, null);
        }
    }

    private int getRandomPasswordLength(AccountShadowType account) throws com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage {
        ResourceType resource = account.getResource();
        if (resource == null) {
            resource = resolveResource(account.getResourceRef().getOid());
        }

        if (resource == null || resource.getSchemaHandling() == null) {
            return -1;
        }

        AccountType accountType = ObjectTypeUtil.getAccountTypeDefinitionFromSchemaHandling(account, resource);
        if (accountType == null || accountType.getCredentials() == null) {
            return -1;
        }

        AccountType.Credentials credentials = accountType.getCredentials();
        if (credentials.getRandomPasswordLength() != null) {
            return credentials.getRandomPasswordLength().intValue();
        }

        return -1;
    }

    private void generatePassword(AccountShadowType account, int length) {
        String pwd = "";
        if (length > 0) {
            pwd = new RandomString(length).nextString();
        }

        CredentialsType.Password password = ModelService.getPassword(account);
        if (password.getAny() != null) {
            return;
        }

        Document document = DOMUtil.getDocument();
        Element hash = document.createElementNS(SchemaConstants.NS_C, "c:base64");
        hash.setTextContent(Base64.encode(pwd));
        password.setAny(hash);
    }

    public UserType listAccountShadowOwnerSilent(String accountOid) {
        try {
            UserContainerType container = listAccountShadowOwner(accountOid);
            return container.getUser();
        } catch (FaultMessage ex) {
            logger.error("Couldn't find owner for account with oid {}, reason: {}", accountOid, ex.getMessage());
        }

        return null;
    }

    private ScriptsType getScripts(ObjectType object) throws com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage {
        ScriptsType scripts = new ScriptsType();
        if (object instanceof ResourceType) {
            ResourceType resource = (ResourceType) object;
            scripts = resource.getScripts();
        } else if (object instanceof ResourceObjectShadowType) {
            ResourceObjectShadowType resourceObject = (ResourceObjectShadowType) object;
            if (resourceObject.getResource() != null) {
                scripts = resourceObject.getResource().getScripts();
            } else {
                ObjectReferenceType reference = resourceObject.getResourceRef();
                ResourceType resource = resolveResource(reference.getOid());
                if (resource != null) {
                    scripts = resource.getScripts();
                }
            }
        }

        return scripts;
    }

    private void processAddAccount(ObjectModificationType objectChange, String operation) throws FaultMessage {
        //handle add new account - it is modification of the user.
        //other changes won't be processed here

        logger.debug("Start search for new accounts in object changes");
        logger.trace("ObjectChange = {}", DebugUtil.prettyPrint(objectChange));

        for (PropertyModificationType change : objectChange.getPropertyModification()) {
            if (PropertyModificationTypeType.add.equals(change.getModificationType())) {
                Node node = change.getValue().getAny().get(0);
                String newValue = DOMUtil.serializeDOMToString(node);
                if ("account".equals(node.getLocalName())) {
                    logger.debug("Found new account");
                    logger.trace("New account is: {}", newValue);

                    JAXBElement<AccountShadowType> accountShadowJaxb;
                    try {
                        accountShadowJaxb = (JAXBElement<AccountShadowType>) JAXBUtil.unmarshal(newValue);
                    } catch (JAXBException ex) {
                        logger.error("### MODEL # Fault {}(..): Parsing of account failed : {}", operation, ex);
                        throw createFaultMessage("Parsing of account failed (" + operation + "): " + ex.getMessage(), SystemFaultType.class, false, ex, null);
                    }

                    //1. we will evaluate values for attributes from schema handling
                    ObjectContainerType userContainer = this.getObject(objectChange.getOid(), Utils.getResolveResourceList());
                    AccountShadowType account = accountShadowJaxb.getValue();
                    ResourceType resource = account.getResource();
                    if (resource == null) {
                        try {
                            resource = resolveResource(account.getResourceRef().getOid());
                        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                            throw createFaultMessage("Resolving resource " + account.getResourceRef().getOid() + " failed: " + ex.getMessage(), new IllegalArgumentFaultType(), ex, null);
                        }
                    }
                    try {
//                        SchemaHandling util = new SchemaHandling();
//                        util.setModel(this);
//                        util.setFilterManager(filterManager);
                        schemaHandling.applyOutboundSchemaHandlingOnAccount((UserType) userContainer.getObject(), account, resource);
                    } catch (SchemaHandlingException ex) {
                        logger.error("### MODEL # Fault {}(..): Parsing outbound schema hadling failed : {}", operation, ex);
                        throw createFaultMessage("Parsing outbound schema hadling failed (" + operation + "): " + ex.getMessage(), ex.getFaultType(), ex, null);
                    }

                    logger.trace("JAXBObject for account: {}", JAXBUtil.silentMarshal(accountShadowJaxb));

                    //2. we will send new account to provisioning
                    ObjectContainerType accountContainer = (new ObjectFactory()).createObjectContainerType();

                    logger.trace("ObjectCOntainer for account: {}", JAXBUtil.silentMarshalWrap(accountContainer, new QName(SchemaConstants.NS_C, "ObjectContainerType")));
                    accountContainer.setObject(accountShadowJaxb.getValue());
                    logger.trace("Account in ObjectCOntainer with applied schema handling: {}", JAXBUtil.silentMarshalWrap(accountContainer, new QName(SchemaConstants.NS_C, "ObjectContainerType")));

                    String accountOid = this.addObject(accountContainer);

                    //3. we will modify object change to contain only accountRef not whole account
                    //   modified object change will be later send to repository
                    ObjectReferenceType accountRef = new ObjectReferenceType();
                    accountRef.setOid(accountOid);
                    accountRef.setType(QNameUtil.uriToQName(Utils.getObjectType("AccountType")));

                    Element accountRefElement = null;
                    try {
                        accountRefElement = JAXBUtil.jaxbToDom(accountRef, SchemaConstants.I_ACCOUNT_REF, DOMUtil.getDocument());
                    } catch (JAXBException ex) {
                        logger.error("### MODEL # Fault {}(..): Parsing of account reference failed : {}", operation, ex);
                        throw createFaultMessage("Parsing of account reference failed (" + operation + "): " + ex.getMessage(), SystemFaultType.class, false, ex, null);
                    }
                    //TODO: for now we support only one vlaue in the list
                    change.getValue().getAny().clear();
                    change.getValue().getAny().add(accountRefElement);

                }
            }
        }
        logger.debug("End search for new accounts in object changes");
    }

    private AccountShadowType resolveAccount(String accountOid) throws com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage {

        OperationalResultType operationalResult = new OperationalResultType();
        Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);
        PropertyReferenceListType resolve = new PropertyReferenceListType();
        ObjectContainerType result = provisioningService.getObject(accountOid, resolve, holder);
        logger.trace("resolveAccount result = {}", result);
        return (AccountShadowType) result.getObject();

    }

    private ResourceType resolveResource(String resourceOid) throws com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage {

        OperationalResultType operationalResult = new OperationalResultType();
        Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);
        PropertyReferenceListType resolve = new PropertyReferenceListType();
        ObjectContainerType result = provisioningService.getObject(resourceOid, resolve, holder);
        logger.trace("resolveResource result = {}", result);
        return (ResourceType) result.getObject();

    }

    @Override
    public ObjectContainerType getObject(java.lang.String oid, PropertyReferenceListType resolve) throws FaultMessage {

        logger.info("### MODEL # Enter getObject({},{})", oid, DebugUtil.prettyPrint(resolve));

        if (logger.isDebugEnabled()) {
            for (PropertyReferenceType property : resolve.getProperty()) {
                XPathType xpath = new XPathType(property.getProperty());
                logger.trace("Resolve XPath = " + xpath);
            }
        }

        ObjectContainerType result = null;

        //Workaround: to get type of object we will ask repository
        try {

            result = repositoryService.getObject(oid, resolve);

        } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
            logger.error("### MODEL # Fault getObject(..): Repository invocation failed (getObject) : ", ex);
            throw createFaultMessage("Repository invocation failed (getObject)", ex.getFaultInfo(), ex, null);
        } catch (RuntimeException ex) {
            // Exceptions such as JBI messaging exceptions
            logger.error("### MODEL # Fault getObject(..): Repository invocation failed (getObject) : ", ex);
            throw createFaultMessage("Repository invocation failed (getObject)", SystemFaultType.class, false, ex, null);
        }

        if (isManagedByProvisioning(result.getObject())) {

            try { // Call Web Service Operation
                OperationalResultType operationalResult = new OperationalResultType();
                Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);

                result = provisioningService.getObject(oid, resolve, holder);

            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                throw createFaultMessage("Provisioning invocation failed (getObject)", ex.getFaultInfo(), ex, null);
            } catch (RuntimeException ex) {
                // Exceptions such as JBI messaging exceptions
                logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                throw createFaultMessage("Provisioning invocation failed (getObject)", SystemFaultType.class, false, ex, null);
            }
        }

        //attributes resolution
        //currently supported: account, resource

        ObjectType objectType = result.getObject();
        if (objectType instanceof UserType) {
            UserType userType = (UserType) objectType;
            if (Utils.toResolve("Account", resolve)) {
                for (ObjectReferenceType accountRef : userType.getAccountRef()) {
                    AccountShadowType account;
                    try {
                        account = resolveAccount(accountRef.getOid());
                    } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                        logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                        throw createFaultMessage("Provisioning invocation failed (getObject)", ex.getFaultInfo(), ex, null);
                    } catch (RuntimeException ex) {
                        // Exceptions such as JBI messaging exceptions
                        logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                        throw createFaultMessage("Provisioning invocation failed (getObject)", SystemFaultType.class, false, ex, null);
                    }
                    userType.getAccount().add(account);
                }
                userType.getAccountRef().clear();

                //resource in account will be resolved only if accounts should be resolved ???
                if (Utils.toResolve("Resource", resolve)) {
                    for (AccountShadowType account : userType.getAccount()) {
                        ResourceType resourceType;
                        try {
                            resourceType = resolveResource(account.getResourceRef().getOid());
                        } catch (RuntimeException ex) {
                            // Exceptions such as JBI messaging exceptions
                            logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                            throw createFaultMessage("Provisioning invocation failed (getObject)", SystemFaultType.class, false, ex, null);
                        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                            logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                            throw createFaultMessage("Provisioning invocation failed (getObject)", ex.getFaultInfo(), ex, null);
                        }
                        account.setResource(resourceType);
                        account.setResourceRef(null);
                    }
                }

            }
        }
        if (objectType instanceof AccountShadowType) {
            AccountShadowType accountShadowType = (AccountShadowType) objectType;
            if (Utils.toResolve("Resource", resolve)) {
                ResourceType resourceType;
                try {
                    resourceType = resolveResource(accountShadowType.getResourceRef().getOid());
                } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                    logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                    throw createFaultMessage("Provisioning invocation failed (getObject)", ex.getFaultInfo(), ex, null);
                } catch (RuntimeException ex) {
                    // Exceptions such as JBI messaging exceptions
                    logger.error("### MODEL # Fault getObject(..): Provisioning invocation failed (getObject) : ", ex);
                    throw createFaultMessage("Provisioning invocation failed (getObject) : ", SystemFaultType.class, false, ex, null);
                }
                accountShadowType.setResource(resourceType);
                accountShadowType.setResourceRef(null);
            }
        }

        logger.info("### MODEL # Exit getObject({},..): {}", oid, DebugUtil.prettyPrint(result));
        logger.trace("Method getObject() returned: {}", JAXBUtil.silentMarshalWrap(result, new QName(SchemaConstants.NS_C, "ObjectContainerType")));

        return result;

    }

    @Override
    public ObjectListType listObjects(java.lang.String objectType, PagingType paging) throws FaultMessage {

        logger.info("### MODEL # Enter listObjects({})", objectType);

        if (isObjectTypeManagedByProvisioning(objectType)) {
            try { // Call Web Service Operation

                OperationalResultType operationalResult = new OperationalResultType();
                Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);
                ObjectListType result = provisioningService.listObjects(objectType, paging, holder);

                //logger.info("### ###1### {}", JAXBUtil.silentMarshal(new JAXBElement<ObjectListType>(new QName(SchemaConstants.NS_C, "fooList"), ObjectListType.class, result)));
                if (result.getObject().size() > 0) {
                    ResourceType r = (ResourceType) result.getObject().get(0);
                    //logger.info("### ###2### {}", DOMUtil.showDom(r.getConfiguration().getAny()));
                }
                logger.info("### MODEL # Exit listObjects(..): {}", DebugUtil.prettyPrint(result));
                return result;
            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                logger.error("### MODEL # Exception listObjects(..): Provisioning WS client failed for method listObjects", ex);
                throw createFaultMessage("Provisioning invocation failed (listObjects)", ex.getFaultInfo(), ex, null);
            }
        } else {
            try {
                ObjectListType result = repositoryService.listObjects(objectType, paging);
                logger.info("### MODEL # Exit listObjects(..): {}", DebugUtil.prettyPrint(result));
                return result;
            } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
                logger.error("### MODEL # Exception listObjects(..): Repository WS client failed for method listObjects", ex);
                throw createFaultMessage("Repository invocation failed (listObjects)", ex.getFaultInfo(), ex, null);
            }
        }
    }

    @Override
    public ObjectListType searchObjects(QueryType filter, PagingType paging) throws FaultMessage {
        //search object is simple proxy to repository
        logger.info("### MODEL # Enter searchObjects({})", filter);
        try {
            ObjectListType result = repositoryService.searchObjects(filter, paging);
            logger.info("### MODEL # Exit searchObjects(..): {}", DebugUtil.prettyPrint(result));
            return result;
        } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
            logger.error("### MODEL # Exception searchObjects(..): Repository client failed for method searchObjects", ex);
            throw createFaultMessage("Repository invocation failed (searchObjects)", ex.getFaultInfo(), ex, null);
        }
    }

    @Override
    public void modifyObject(ObjectModificationType objectChange) throws FaultMessage {
        modifyObjectWithExclusion(objectChange, null);
    }

    /**
     * Should be removed after model API update, used now for password modification
     * @param objectChange
     * @param accountOid - account from where password change came from
     * @throws FaultMessage
     * @deprecated
     */
    @Deprecated
    public void modifyObjectWithExclusion(ObjectModificationType objectChange, String accountOid) throws FaultMessage {
        logger.info("### MODEL # Enter modifyObjectWithExclusion({})", DebugUtil.prettyPrint(objectChange));

        // Determine object type first

        String oid = objectChange.getOid();
        if (oid == null) {
            logger.error("### MODEL # Fault modifyObjectWithExclusion(..): No OID specified");
            throw new FaultMessage("No OID specified", new IllegalArgumentFaultType());
        }

        // Check for empty changes

        if (objectChange.getPropertyModification().isEmpty()) {
            // Nothing to do
            logger.warn("Received empty changes in Model modifyObjectWithExclusion() for OID {}", oid);
            logger.info("### MODEL # Exit modifyObjectWithExclusion(..)");
            return;
        }

        ObjectContainerType objectContainer;
        try {
            objectContainer = repositoryService.getObject(oid, new PropertyReferenceListType());
        } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
            logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Repository invocation failed (getObject) : {}", ex);
            throw createFaultMessage("Repository invocation failed (getObject)", ex.getFaultInfo(), ex, null);
        }
        ObjectType object = objectContainer.getObject();

        if (isManagedByProvisioning(object)) {
            OperationalResultType operationalResult = new OperationalResultType();
            Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);

            try {
                if (object instanceof AccountShadowType) {

                    // This seems to be wrong. What are the outboudn expressions only applied
                    // if an account has an owner?
                    UserType userType = listAccountShadowOwnerSilent(object.getOid());
                    if (userType != null) {
                        // We have object already, but that may be just "empty shell" from repository
                        // So we need to get the full object from provisioning now
                        ObjectContainerType provisioningObjectContainer = provisioningService.getObject(oid, new PropertyReferenceListType(), new Holder<OperationalResultType>());
                        AccountShadowType originalAccount = (AccountShadowType) provisioningObjectContainer.getObject();

                        // This is also wrong. The changes should not be computed by patching and diffing.
                        // The input changes should be transformed to output changes
                        // The originalAccount is there only as argument for $account and it should in fact reflect the changes
                        // done by the expressions as they are evaluated
                        // But that will be improved later

                        AccountShadowType changedAccount = (AccountShadowType) JAXBUtil.clone(originalAccount);

                        PatchXml xmlPatchTool = new PatchXml();
                        String xmlObject = xmlPatchTool.applyDifferences(objectChange, changedAccount);
                        JAXBElement<AccountShadowType> jaxb = (JAXBElement<AccountShadowType>) JAXBUtil.unmarshal(xmlObject);
                        changedAccount = jaxb.getValue();

//                        SchemaHandling util = new SchemaHandling();
//                        util.setModel(this);
//                        util.setFilterManager(filterManager);

                        // If the resource was resolved, this is the easiest way
                        ResourceType resource = originalAccount.getResource();

                        if (resource == null) {
                            // If not, do it the hard way
                            resource = resolveResource(originalAccount.getResourceRef().getOid());
                        }

                        object = schemaHandling.applyOutboundSchemaHandlingOnAccount(userType, changedAccount, resource);

                        objectChange = CalculateXmlDiff.calculateChanges(originalAccount, changedAccount);
                    }
                }

                // we don't want to send empty modification list to provisioning
                if (objectChange != null && objectChange.getOid() != null &&
                        !objectChange.getPropertyModification().isEmpty()) {
                    ScriptsType scripts = getScripts(object);
                    provisioningService.modifyObject(objectChange, scripts, holder);
                }
            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Provisioning invocation failed (modifyObject) : {}", ex);
                throw createFaultMessage("Provisioning invocation failed (modifyObjectWithExclusion)", ex.getFaultInfo(), ex, null);
            } catch (JAXBException ex) {
                throw createFaultMessage("Couldn't clone account '" + objectChange.getOid() + "'.",
                        SystemFaultType.class, true, ex, null);
            } catch (PatchException ex) {
                throw createFaultMessage("Couldn't patch account '" + objectChange.getOid() + "' xml.",
                        SystemFaultType.class, true, ex, null);
            } catch (SchemaHandlingException ex) {
                throw createFaultMessage("Couldn't apply outbound schema handling on account '" +
                        objectChange.getOid() + "'.", SystemFaultType.class, true, ex, null);
            } catch (DiffException ex) {
                throw createFaultMessage("Couldn't create account '" + objectChange.getOid() + "' diff.",
                        SystemFaultType.class, true, ex, null);
            }
        } else {
            processAddAccount(objectChange, "modifyObject");

            try {
                PropertyModificationType password = null;
                if (object instanceof UserType) {
                    password = getPasswordFromModification(objectChange);
                }

                repositoryService.modifyObject(objectChange);
                //if objectChange contains password change for user type, update all passwords on his accounts
                if (password != null) {
                    updateAccountPasswords((UserType) object, password, accountOid);
                }
                //update user accounts
                if (object instanceof UserType) {
                    ObjectContainerType container = getObject(object.getOid(), new PropertyReferenceListType());
                    UserType userType = (UserType) container.getObject();
                    if (logger.isDebugEnabled()) {
                        logger.debug("User before accounts update - outbound schema handling\n{}", DebugUtil.prettyPrint(userType));
                    }
                    updateUserAccounts(userType, accountOid);
                }
            } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
                logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Repository invocation failed (modifyObjectWithExclusion): {}", ex);
                throw createFaultMessage("Repository invocation failed (modifyObjectWithExclusion)", ex.getFaultInfo(), ex, null);
            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Provisioning method invocation failed: {}", ex);
                throw createFaultMessage("Provisioning method invocation failed", ex.getFaultInfo(), ex, null);
            } catch (PatchException ex) {
                logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Couldn't patch user xml: {}", ex);
                throw createFaultMessage("Couldn't patch user xml", null, ex, null);
            } catch (JAXBException ex) {
                logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Couldn't parse user xml: {}", ex);
                throw createFaultMessage("Couldn't parse user xml", null, ex, null);
            } catch (DiffException ex) {
                logger.error("### MODEL # Fault modifyObjectWithExclusion(..): Couldn't generate xml diff from user changes: {}", ex);
                throw createFaultMessage("Couldn't generate xml diff from user changes", null, ex, null);
            }
        }

        logger.info("### MODEL # Exit modifyObjectWithExclusion(..)");
    }

    private void updateUserAccounts(UserType user, String accountOid) throws FaultMessage {
        logger.trace("updateUserAccounts::begin - {}, {}", user.getOid(), accountOid);
        if (user == null) {
            throw createFaultMessage("User object is null, skipping account updates.", IllegalArgumentFaultType.class, false, null, null);
        }

        List<ObjectReferenceType> accountRefs = user.getAccountRef();
        List<AccountShadowType> accounts = new ArrayList<AccountShadowType>();
        for (ObjectReferenceType ref : accountRefs) {
            try {
                accounts.add(resolveAccount(ref.getOid()));
            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                String message = ex.getFaultInfo() == null ? ex.getMessage() : ex.getFaultInfo().getMessage();
                logger.error("Couldn't resolve account '{}', reason: {}.", ref.getOid(), message);
                logger.debug("Couldn't resolve account '{}'.", ref.getOid(), ex);
                throw createFaultMessage("Couldn't resolve account '" + ref.getOid() + "', reason: " +
                        message + ".", ex.getFaultInfo(), ex, null);
            }
        }

        for (AccountShadowType account : accounts) {
            //preventing cycles
            if (accountOid != null && accountOid.equals(account.getOid())) {
                logger.trace("Skipping user ({}) account update for '{}'", new Object[]{user.getOid(), account.getOid()});
                continue;
            }

            ResourceType resourceType = getResource(account);
            ResourceObjectShadowType newAccount = null;
            try {
                logger.trace("Applying resource ({}) outbound schema handling on account {}.", new Object[]{
                            resourceType.getOid(), account.getOid()});
                newAccount = schemaHandling.applyOutboundSchemaHandlingOnAccount(user,
                        (AccountShadowType) JAXBUtil.clone(account), resourceType);
            } catch (SchemaHandlingException ex) {
                logger.error("Failed to parse outbound schema hanling for account '{}' reason: {}.", account.getName(), ex.getMessage());
                throw createFaultMessage("Failed to parse outbound schema hanling: " + ex.getMessage(), ex.getFaultType(), ex, null);
            } catch (JAXBException ex) {
                logger.error("Failed to clone account object ({}), reason: {}", new Object[]{account.getOid(), ex.getMessage()});
                throw createFaultMessage("Failed to clone account object (" + account.getOid() + "), reason: " +
                        ex.getMessage(), SystemFaultType.class, false, ex, null);
            }

            try {
                Holder<OperationalResultType> holder = new Holder<OperationalResultType>();
                ObjectModificationType changes = CalculateXmlDiff.calculateChanges(account, newAccount);
                changes.setOid(account.getOid());

                ScriptsType scripts = resourceType.getScripts();
                if (scripts == null) {
                    scripts = new ScriptsType();
                }
                logger.trace("Sending object changes for account {} to provisioning.", account.getOid());
                provisioningService.modifyObject(changes, scripts, holder);
            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                String message = ex.getFaultInfo() == null ? ex.getMessage() : ex.getFaultInfo().getMessage();
                logger.error("Couldn't update account '{}', reason: {}.", account.getName(), message);
                logger.debug("Couldn't update account '{}'.", account.getOid(), ex);
                throw createFaultMessage("Provisioning couldn't update account, reason: " + message,
                        ex.getFaultInfo(), ex, null);
            } catch (DiffException ex) {
                logger.error("Couldn't create account diff for '{}', reason: {}.", account.getName(), ex.getMessage());
                logger.debug("Couldn't create account diff for '{}'.", account.getOid(), ex);
                throw createFaultMessage("Couldn't create diff for account, reason: " + ex.getMessage(),
                        SystemFaultType.class, false, ex, null);
            }
        }
        logger.trace("updateUserAccounts::end");
    }

    private ResourceType getResource(ResourceObjectShadowType resourceShadow) throws FaultMessage {
        ResourceType resource = resourceShadow.getResource();
        if (resource != null) {
            return resource;
        }

        ObjectReferenceType resourceRef = resourceShadow.getResourceRef();
        if (resourceRef == null) {
            FaultType fault = new SchemaViolationFaultType();
            fault.setMessage("Resource nor resource ref is defined.");
            throw createFaultMessage(fault.getMessage(), fault, null, null);
        }

        try {
            OperationalResultType operationalResult = new OperationalResultType();
            Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);
            ObjectContainerType container = provisioningService.getObject(resourceRef.getOid(), null, holder);
            ObjectType object = container.getObject();
            if (object == null) {
                throw createFaultMessage("Couldn't get resource with oid '" + resourceRef.getOid() +
                        "', reason: object returned from provisioning is null.", IllegalArgumentFaultType.class, false, null, null);
            }
            if (object instanceof ResourceType) {
                resource = (ResourceType) object;
            } else {
                throw createFaultMessage("Couldn't get resource with oid '" + resourceRef.getOid() +
                        "', reason: object type returned from provisioning was type '" +
                        object.getClass() + "' and not '" + ResourceType.class + "'.", IllegalArgumentFaultType.class, false, null, null);
            }
        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
            String message = ex.getFaultInfo() == null ? ex.getMessage() : ex.getFaultInfo().getMessage();
            logger.error("Couldn't get resource with oid '{}', reason: {}.", resourceRef.getOid(), message);

            throw createFaultMessage("Couldn't get resource with oid '" + resourceRef.getOid() +
                    "'.", ex.getFaultInfo(), ex, null);
        }

        return resource;
    }

    private void updateAccountPasswords(UserType user, PropertyModificationType password,
            String accountOid) throws com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage,
            JAXBException, PatchException, DiffException, FaultMessage {
        //2. update passwords on accounts
        List<ObjectReferenceType> accountRefs = user.getAccountRef();
        for (ObjectReferenceType reference : accountRefs) {
            AccountShadowType account = resolveAccount(reference.getOid());
            if (updateAccountPassword(account, accountOid)) {
                logger.debug("### MODEL # updating password for account: " + account.getName());
                ObjectModificationType changes = createPasswordModification(account, password);
                modifyObject(changes);
            }
        }

        List<AccountShadowType> accounts = user.getAccount();
        for (AccountShadowType account : accounts) {
            if (updateAccountPassword(account, accountOid)) {
                logger.debug("### MODEL # updating password for account: " + account.getName());
                ObjectModificationType changes = createPasswordModification(account, password);
                modifyObject(changes);
            }
        }
    }

    private ObjectModificationType createPasswordModification(AccountShadowType account, PropertyModificationType password) {
        ObjectModificationType changes = new ObjectModificationType();
        changes.setOid(account.getOid());
        changes.getPropertyModification().add(password);

        return changes;
    }

    private boolean updateAccountPassword(AccountShadowType account, String accountOid)
            throws com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage {
        if (accountOid != null && accountOid.equals(account.getOid())) {
            return false;
        }

        ResourceType resource = account.getResource();
        if (resource == null) {
            resource = resolveResource(account.getResourceRef().getOid());
        }

        SchemaHandlingType.AccountType handling = ObjectTypeUtil.getAccountTypeDefinitionFromSchemaHandling(account, resource);
        if (handling == null || handling.getCredentials() == null) {
            return false;
        }

        Credentials credentials = handling.getCredentials();
        Boolean update = credentials.isOutboundPassword();
        if (update == null) {
            return false;
        }

        return update;
    }

    public static CredentialsType.Password getPassword(AccountShadowType account) {
        CredentialsType credentials = account.getCredentials();
        ObjectFactory of = new ObjectFactory();
        if (credentials == null) {
            credentials = of.createCredentialsType();
            account.setCredentials(credentials);
        }
        CredentialsType.Password password = credentials.getPassword();
        if (password == null) {
            password = of.createCredentialsTypePassword();
            credentials.setPassword(password);
        }

        return password;
    }

    private PropertyModificationType getPasswordFromModification(ObjectModificationType objectChange) {
        List<PropertyModificationType> list = objectChange.getPropertyModification();
        for (PropertyModificationType propModification : list) {
            XPathType path = new XPathType(propModification.getPath());
            List<XPathSegment> segments = path.toSegments();
            if (segments.size() == 0 || !segments.get(0).getQName().equals(SchemaConstants.I_CREDENTIALS)) {
                continue;
            }

            PropertyModificationType.Value value = propModification.getValue();
            if (value == null) {
                continue;
            }
            List<Element> elements = value.getAny();
            for (Element element : elements) {
                if (SchemaConstants.I_PASSWORD.equals(new QName(element.getNamespaceURI(), element.getLocalName()))) {
                    return propModification;
                }
            }
        }

        return null;
    }

    @Override
    public void deleteObject(java.lang.String oid) throws FaultMessage {
        logger.info("### MODEL # Enter deleteObject({})", oid);
        //Workaround: to get type of object we will ask repository
        ObjectContainerType repositoryResult;
        try { // Call Web Service Operation
            PropertyReferenceListType resolve = new PropertyReferenceListType();
            repositoryResult = repositoryService.getObject(oid, resolve);

        } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
            logger.error("### MODEL # Exception deleteObject(..): Repository WS client failed for method getObject", ex);
            throw createFaultMessage("Repository invocation failed (getObject)", ex.getFaultInfo(), ex, null);
        }

        if ((repositoryResult.getObject() instanceof ResourceObjectShadowType) || (repositoryResult.getObject() instanceof ResourceType)) {
            try { // Call Web Service Operation
                OperationalResultType operationalResult = new OperationalResultType();
                Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);

                ScriptsType scripts = getScripts(repositoryResult.getObject());
                provisioningService.deleteObject(oid, scripts, holder);

            } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                logger.error("### MODEL # Exception deleteObject(..): Provisioning WS client failed for method deleteObject", ex);
                throw createFaultMessage("Provisioning invocation failed (deleteObject)", ex.getFaultInfo(), ex, null);
            }

        } else if ((repositoryResult.getObject() instanceof UserType)) {

            // Special handling for user. Delete all user accounts first

            UserType user = (UserType) repositoryResult.getObject();

            List<String> accountOids = ObjectTypeUtil.extractOids(user.getAccount(), user.getAccountRef());

            OperationalResultType operationalResult = new OperationalResultType();
            Holder<OperationalResultType> holder = new Holder<OperationalResultType>(operationalResult);

            com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage lastProvisioningFault = null;
            com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage lastRepositoryFault = null;

            ScriptsType scripts = null;
            for (String accountOid : accountOids) {
                try {
                    scripts = getScripts(resolveAccount(accountOid).getResource());

                    // delete Account shadow (and therefore also account on resource)                    
                    provisioningService.deleteObject(accountOid, scripts, holder);

                    // unlink account from user

                    ObjectReferenceType refToDelete = ObjectTypeUtil.findRef(accountOid, user.getAccountRef());

                    PropertyModificationType propertyChangeType = ObjectTypeUtil.createPropertyModificationType(PropertyModificationTypeType.delete, null, new QName(SchemaConstants.NS_IDENTITY, "accountRef"), refToDelete);

                    ObjectModificationType objectChange = new ObjectModificationType();
                    objectChange.setOid(oid);
                    objectChange.getPropertyModification().add(propertyChangeType);

                    repositoryService.modifyObject(objectChange);

                } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
                    logger.error("Delete of account {} failed for user {} (OID: {}) : {}", new Object[]{accountOid, user.getName(), user.getOid(), ex.getMessage()});
                    // Do something smart with Operational Result here
                    lastProvisioningFault = ex;

                } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
                    logger.error("Delete of account {} failed for user {} (OID: {}) : {}", new Object[]{accountOid, user.getName(), user.getOid(), ex.getMessage()});
                    // Do something smart with Operational Result here
                    lastRepositoryFault = ex;
                }
            }

            if (lastProvisioningFault != null) {
                // FIXME: not good, this only relays last fault. Improve it.
                logger.error("### MODEL # Fault deleteObject(..): Provisioning WS client failed, last fault: {} ", lastProvisioningFault.getMessage());
                throw createFaultMessage("Provisioning invocation failed (deleteObject)", lastProvisioningFault.getFaultInfo(), lastProvisioningFault, null);
            }
            if (lastRepositoryFault != null) {
                // FIXME: not good, this only relays last fault. Improve it.
                logger.error("### MODEL # Fault deleteObject(..): Repository WS client failed, last fault: {} ", lastRepositoryFault.getMessage());
                throw createFaultMessage("Repository invocation failed (deleteObject)", lastRepositoryFault.getFaultInfo(), lastRepositoryFault, null);
            }
            try {
                // Delete user, it has no accounts now
                repositoryService.deleteObject(oid);

            } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
                logger.error("### MODEL # Fault deleteObject(..): Delete user from repository failed : {} ", ex.getMessage());
                throw createFaultMessage("Repository invocation failed (deleteObject)", ex.getFaultInfo(), ex, null);
            }

        } else {
            try {
                repositoryService.deleteObject(oid);
            } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
                logger.error("### MODEL # Fault deleteObject(..): Delete object from repository failed : {} ", ex.getMessage());
                throw createFaultMessage("Repository invocation failed (deleteObject)", ex.getFaultInfo(), ex, null);
            }
        }
    }

    @Override
    public PropertyAvailableValuesListType getPropertyAvailableValues(java.lang.String oid, PropertyReferenceListType properties)
            throws FaultMessage {
        logger.info("### MODEL # Enter getPropertyAvailableValues({},{})", oid, DebugUtil.prettyPrint(properties));
        PropertyAvailableValuesListType propertyAvailableValues = new PropertyAvailableValuesListType();
        logger.info("### MODEL # Enter getPropertyAvailableValues(..) : ", DebugUtil.prettyPrint(propertyAvailableValues));
        return propertyAvailableValues;
    }

    @Override
    public UserContainerType listAccountShadowOwner(java.lang.String accountOid) throws FaultMessage {
        logger.info("### MODEL # Enter listAccountShadowOwner({})", accountOid);
        try {
            UserContainerType result = repositoryService.listAccountShadowOwner(accountOid);
            logger.info("### MODEL # Exit listAccountShadowOwner(..): {}", DebugUtil.prettyPrint(result));
            return result;
        } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
            logger.error("### MODEL # Exception listAccountShadowOwner(..): Repository client failed for method listAccountShadowOwner", ex);
            throw createFaultMessage("Repository invocation failed (listAccountShadowOwner)", ex.getFaultInfo(), ex, null);
        }
    }

    @Override
    public ResourceObjectShadowListType listResourceObjectShadows(java.lang.String resourceOid, java.lang.String resourceObjectShadowType) throws FaultMessage {
        logger.info("### MODEL # Enter listResourceObjectShadows({},{})", resourceOid, resourceObjectShadowType);
        try {
            ResourceObjectShadowListType result = repositoryService.listResourceObjectShadows(resourceOid, resourceObjectShadowType);
            logger.info("### MODEL # Exit listResourceObjectShadows(..): {}", DebugUtil.prettyPrint(result));
            return result;
        } catch (com.forgerock.openidm.xml.ns._public.repository.repository_1.FaultMessage ex) {
            logger.error("### MODEL # Exception listResourceObjectShadows(..): Repository client failed for method listResourceObjectShadows", ex);
            throw createFaultMessage("Repository invocation failed (listResourceObjectShadows)", ex.getFaultInfo(), ex, null);
        }
    }

    private boolean isManagedByProvisioning(ObjectType object) {
        if (object instanceof ResourceObjectShadowType) {
            return true;
        }
        if (object instanceof ResourceType) {
            return true;
        }
        return false;
    }

    private boolean isObjectTypeManagedByProvisioning(String objectType) {
        if ((Utils.getObjectType("ResourceType").equals(objectType)) || (Utils.getObjectType("AccountType").equals(objectType))) {
            return true;
        }
        return false;
    }

    private FaultMessage createFaultMessage(String message, FaultType faultType, Exception ex, OperationalResultType result) {
        if (faultType.getMessage() == null || faultType.getMessage().isEmpty()) {
            faultType.setMessage(message);
        } else {
            faultType.setMessage(message + " : " + faultType.getMessage());
        }
        return new FaultMessage(message, faultType, ex);
    }

    private FaultMessage createFaultMessage(String message, Class<? extends FaultType> faultTypeClass, boolean temporary, Exception exception, OperationalResultType result) {
        FaultType fault;
        try {
            fault = faultTypeClass.newInstance();
        } catch (InstantiationException ex) {
            // This should not happen
            throw new IllegalArgumentException("Cannot instantate " + faultTypeClass.getName(), ex);
        } catch (IllegalAccessException ex) {
            // This should not happen
            throw new IllegalArgumentException("Cannot instantate " + faultTypeClass.getName(), ex);
        }
        if (exception instanceof RuntimeException) {
            fault.setMessage(message + " : " + exception.getClass().getSimpleName() + " : " + exception.getMessage());
        } else {
            fault.setMessage(message);
        }
        fault.setTemporary(temporary);
        return new FaultMessage(message, fault, exception);
    }

    /*
     * Following two operations should not be here. They are in fact
    just diagnostics methods from the provisioning interface that need
    to be accessed from GUI. Do not use them for anything serious.
    The will disappear eventually.
     */
    @Override
    public ResourceTestResultType testResource(String resourceOid) throws FaultMessage {
        logger.info("### MODEL # Enter testResource({})", resourceOid);

        ResourceTestResultType result = null;

        try { // Call Web Service Operation

            result = provisioningService.testResource(resourceOid);

        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
            logger.error("### MODEL # Fault testResource(..): Provisioning invocation failed (getObject) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (getObject)", ex.getFaultInfo(), ex, null);
        } catch (RuntimeException ex) {
            // Exceptions such as JBI messaging exceptions
            logger.error("### MODEL # Fault testResource(..): Provisioning invocation failed (getObject) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (getObject)", SystemFaultType.class, false, ex, null);
        }

        logger.info("### MODEL # Exit testResource({}): {}", resourceOid, result);
        return result;
    }

    @Override
    public ObjectListType listResourceObjects(String resourceOid, String objectType, PagingType paging, Holder<OperationalResultType> resultHolder) throws FaultMessage {
        logger.info("### MODEL # Enter listResourceObjects({},{},...)", resourceOid, objectType);

        ObjectListType result = null;

        try { // Call Web Service Operation

            result = provisioningService.listResourceObjects(resourceOid, objectType, paging, resultHolder);

        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
            logger.error("### MODEL # Fault listResourceObjects(..): Provisioning invocation failed (getObject) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (getObject)", ex.getFaultInfo(), ex, null);
        } catch (RuntimeException ex) {
            // Exceptions such as JBI messaging exceptions
            logger.error("### MODEL # Fault listResourceObjects(..): Provisioning invocation failed (getObject) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (getObject)", SystemFaultType.class, false, ex, null);
        }

        logger.info("### MODEL # Exit listResourceObjects({},...): {}", resourceOid, result);

        return result;
    }

    @Override
    public EmptyType launchImportFromResource(String resourceOid, String objectClass) throws FaultMessage {
        logger.info("### MODEL # Enter launchImportFromResource({},{})", resourceOid, objectClass);

        EmptyType result = null;

        try { // Call Web Service Operation

            result = provisioningService.launchImportFromResource(resourceOid, objectClass);

        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
            logger.error("### MODEL # Fault launchImportFromResource(..): Provisioning invocation failed (launchImportFromResource) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (launchImportFromResource)", ex.getFaultInfo(), ex, null);
        } catch (RuntimeException ex) {
            // Exceptions such as JBI messaging exceptions
            logger.error("### MODEL # Fault launchImportFromResource(..): Provisioning invocation failed (launchImportFromResource) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (launchImportFromResource)", SystemFaultType.class, false, ex, null);
        }

        logger.info("### MODEL # Exit launchImportFromResource(..): {}", result);
        return result;
    }

    @Override
    public TaskStatusType getImportStatus(String resourceOid) throws FaultMessage {
        logger.info("### MODEL # Enter getImportStatus({})", resourceOid);

        TaskStatusType result = null;

        try { // Call Web Service Operation

            result = provisioningService.getImportStatus(resourceOid);

        } catch (com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage ex) {
            logger.error("### MODEL # Fault getImportStatus(..): Provisioning invocation failed (getImportStatus) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (getImportStatus)", ex.getFaultInfo(), ex, null);
        } catch (RuntimeException ex) {
            // Exceptions such as JBI messaging exceptions
            logger.error("### MODEL # Fault getImportStatus(..): Provisioning invocation failed (getImportStatus) : ", ex);
            throw createFaultMessage("Provisioning invocation failed (getImportStatus)", SystemFaultType.class, false, ex, null);
        }

        logger.info("### MODEL # Exit getImportStatus({}): {}", resourceOid, result);
        return result;
    }
}
