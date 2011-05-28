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
package com.forgerock.openidm.provisioning.service;

import com.forgerock.openidm.provisioning.schema.ResourceSchema;
import com.forgerock.openidm.provisioning.schema.util.DOMToSchemaParser;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceType;
import org.w3c.dom.Element;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

/**
 * Parse the {@link ResourceType} and converts it into more usefull java object.
 * <p/>
 * The schema and schemaHandling are processed by this class. The rest must be 
 * processed by the class that extends it.
 *
 * @see ResourceType#schema
 * @see ResourceType#schemaHandling
 * 
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0.0
 */
public abstract class ResourceConnector<C> {

    public static final String code_id = "$Id$";
    public static final QName QN_SCHEMA = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema");
    protected ResourceType _resource;
    private final ResourceSchema _resourceSchema;

    /**
     * Dummy constructor for test purposes.
     */
    public ResourceConnector() {
        _resourceSchema = null;
    }

    public ResourceConnector(ResourceType resourceType) {
        DOMToSchemaParser parser = new DOMToSchemaParser();
        Element resourceSchema = null;
        Element schemaHandling = null;
        if (null != resourceType.getSchema() && !resourceType.getSchema().getAny().isEmpty()) {
            for (Element e : resourceType.getSchema().getAny()) {
                if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(e.getNamespaceURI()) && "schema".equals(e.getLocalName())) {
                    resourceSchema = e;
                    break;
                }
            }
        }
        //TODO: Fix the SchemaHandling part
        if (null != resourceType.getSchemaHandling()) {
        }
        _resourceSchema = parser.getSchema(resourceSchema, schemaHandling);
        this._resource = resourceType;
    }

    /**
     * The {@Link BaseResourceIntegration} has already parsed the {@ ResourceType}
     * its more like a copy Constructor
     * 
     * @param res
     */
    public ResourceConnector(BaseResourceIntegration res) {
        this(res.getResource());
    }

    /**
     * Gets the configuration object that parses the {@link ResourceType#configuration} 
     * of the resource instance.
     * 
     * Every integration has it's own configuration and must implement the calss that
     * can parse.
     * 
     * @return  the configuration holder object.
     */
    public abstract C getConfiguration();

    public String getOid() {
        return _resource.getOid();
    }

    public String getNamespace() {
        return _resource.getNamespace();
    }

    public boolean isAccountNameIsUid() {
        //TODO: calculate this
        return true;
    }   

    public ResourceSchema getSchema() {
        return _resourceSchema;
    }

    public ResourceType getResource() {
        return _resource;
    }
}
