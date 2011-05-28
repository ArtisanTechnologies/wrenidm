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
package com.forgerock.openidm.web.model.test;

import com.forgerock.openidm.util.Utils;
import com.forgerock.openidm.web.model.AccountShadowDto;
import com.forgerock.openidm.web.model.PagingDto;
import com.forgerock.openidm.web.model.PropertyAvailableValues;
import com.forgerock.openidm.web.model.PropertyChange;
import com.forgerock.openidm.web.model.ResourceDto;
import com.forgerock.openidm.web.model.UserDto;
import com.forgerock.openidm.web.model.UserManager;
import com.forgerock.openidm.web.model.WebModelException;
import com.forgerock.openidm.xml.ns._public.common.common_1.*;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowType.Attributes;
import com.forgerock.openidm.xml.schema.XPathType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author sleepwalker
 */
public class UserTypeManagerMock implements UserManager {

    @Autowired
    AccountShadowTypeManagerMock accountManagerMock;
    @Autowired
    ResourceTypeManagerMock resourceManagerMock;
    Map<String, UserDto> userTypeList = new HashMap<String, UserDto>();
    private final Class constructUserType;

    public UserTypeManagerMock(Class constructUserType) {
        this.constructUserType = constructUserType;
    }

    @Override
    public void delete(String oid) {
        userTypeList.remove(oid);
    }

    @Override
    public Collection<UserDto> list() {
        return userTypeList.values();
    }

    public UserDto get(String oid) {
        UserDto lookupUser = null;
        try {
            lookupUser = (UserDto) constructUserType.newInstance();
        } catch (InstantiationException ex) {
        } catch (IllegalAccessException ex) {
        }
        for (UserDto user : userTypeList.values()) {
            if (oid.equals(user.getOid())) {
                lookupUser = user;
            }
        }
        return lookupUser;
    }

    @Override
    public String add(UserDto newObject) {
        userTypeList.clear();
        newObject.setOid(UUID.randomUUID().toString());
        userTypeList.put(newObject.getOid(), newObject);
        return newObject.getOid();
    }

    @Override
    public Set<PropertyChange> submit(UserDto changedObject) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<PropertyAvailableValues> getPropertyAvailableValues(String oid, List<String> properties) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public UserDto create() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AccountShadowDto addAccount(UserDto userDto, String resourceOid) {
        AccountShadowDto accountDto = new AccountShadowDto();
        AccountShadowType accountType = new AccountShadowType();
        accountType.setAttributes(new Attributes());
        ResourceDto resourceDto = resourceManagerMock.get(resourceOid, new PropertyReferenceListType());
        accountType.setResource((ResourceType) resourceDto.getXmlObject());
        accountDto.setXmlObject(accountType);

        System.out.println("account Resource namespace " + accountDto.getResource().getNamespace());

        return accountDto;

    }

    @Override
    public UserDto get(String oid, PropertyReferenceListType resolve) {

        System.out.println("user mock");
        System.out.println("wanted " + oid);
        System.out.println("in list " + userTypeList.get(oid).getOid());
        UserDto userDto = null;
        for (UserDto user : userTypeList.values()) {
            if (user.getOid().equals(oid)) {
                userDto = user;
            }
        }

        if (!resolve.getProperty().isEmpty()) {
            Collection<AccountShadowDto> accounts = accountManagerMock.list();
            Collection<ResourceDto> resources = resourceManagerMock.list();
            for (PropertyReferenceType property : resolve.getProperty()) {
                if (Utils.getPropertyName("Account").equals((new XPathType(property.getProperty())).getXPath())) {
                    for (AccountShadowDto acc : accounts) {
                        if (acc.getOid().equals(userDto.getAccountRef().get(0).getOid())) {
                            ((UserType) userDto.getXmlObject()).getAccount().add((AccountShadowType) acc.getXmlObject());
                            System.out.println("acc res ref" + acc.getResourceRef().getOid());

                        }
                    }
                }

                if (Utils.getPropertyName("Resource").equals((new XPathType(property.getProperty())).getXPath())) {
                    for (ResourceDto res : resources) {
                        System.out.println("res oid " + res.getOid());
                        if (res.getOid().equals(userDto.getAccount().get(0).getResourceRef().getOid())) {
                            AccountShadowType accountType = new AccountShadowType();
                            accountType.setResource((ResourceType) res.getXmlObject());
                            System.out.println("account type res " + accountType.getResource().getName());
                            ((AccountShadowType) (userDto.getAccount().get(0).getXmlObject())).setResource((ResourceType) res.getXmlObject());
                            //((UserType) guiUserDto.getXmlObject()).getAccount().set(0, (AccountShadowType) accountType);

                        }
                    }
                }
            }
        }
        return userDto;
    }

    @Override
    public Collection<UserDto> list(PagingDto pagingDto) throws WebModelException {
        return userTypeList.values();
    }
}
