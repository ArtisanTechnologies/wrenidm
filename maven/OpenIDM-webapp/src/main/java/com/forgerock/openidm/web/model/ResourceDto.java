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
package com.forgerock.openidm.web.model;

import com.forgerock.openidm.xml.ns._public.common.common_1.*;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import org.w3c.dom.Element;

/**
 *
 * @author semancik
 */
public class ResourceDto extends ExtensibleObjectDto {

    public ResourceDto() {
    }

    public ResourceDto(ResourceType object) {
        super(object);
    }

    public ResourceDto(ObjectStage stage) {
        super(stage);
    }

    ResourceType getResourceType() {
        return (ResourceType) getXmlObject();
    }

    public String getType() {
        return getResourceType().getType();
    }

    public void setType(String value) {
        getResourceType().setType(value);
    }

    public Element getSchema() {
        // TODO: Make this smarter ... if possible
        ResourceType.Schema schema = getResourceType().getSchema();
        if (schema != null && schema.getAny().size() != 0) {
            return (Element) getResourceType().getSchema().getAny().get(0);
        }

        return null;
    }

//    public ResourceType.SchemaHandling getSchemaHandling() {
//        return getResourceType().getSchemaHandling();
//    }
//
//    public void setSchemaHandling(ResourceType.SchemaHandling value) {
//        getResourceType().setSchemaHandling(value);
//    }
    public List<Element> getConfiguration() {
        return getResourceType().getConfiguration().getAny();
    }

    public void setConfiguration(Configuration value) {
        // TODO
    }

    public List<AccountTypeDto> getAccountTypes() {
        List<AccountTypeDto> accountTypeList = new ArrayList<AccountTypeDto>();
        if (getResourceType() == null || getResourceType().getSchemaHandling() == null) {
            return accountTypeList;
        }

        List<SchemaHandlingType.AccountType> list = getResourceType().getSchemaHandling().getAccountType();
        for (SchemaHandlingType.AccountType accountType : list) {
            accountTypeList.add(new AccountTypeDto(accountType.getName(), accountType.getObjectClass(), accountType.isDefault()));
        }
        return accountTypeList;
    }

    public String getNamespace() {
        return ((ResourceType)getXmlObject()).getNamespace();
    }

    public static class AccountTypeDto {

        private String name;
        private QName objectClass;
        private boolean def;

        private AccountTypeDto(String name, QName objectClass, boolean def) {
            this.name = name;
            this.objectClass = objectClass;
            this.def = def;
        }

        public String getName() {
            return name;
        }

        public QName getObjectClass() {
            return objectClass;
        }

        public boolean isDefault() {
            return def;
        }
    }
}
