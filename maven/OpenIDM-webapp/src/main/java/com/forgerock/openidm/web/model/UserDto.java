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

/**
 *
 * @author semancik
 */
public class UserDto extends ExtensibleObjectDto {

    public UserDto() {
    }

    public UserDto(UserType object) {
        super(object);
    }

    public UserDto(ObjectStage stage) {
        super(stage);
    }

    private UserType getUserType() {
        return (UserType) getXmlObject();
    }

    public String getFullName() {
        return getUserType().getFullName();
    }

    public void setFullName(String value) {
        getUserType().setFullName(value);
    }

    public String getGivenName() {
        return getUserType().getGivenName();
    }

    public void setGivenName(String value) {
        getUserType().setGivenName(value);
    }

    public String getFamilyName() {
        return getUserType().getFamilyName();
    }

    public void setFamilyName(String value) {
        getUserType().setFamilyName(value);
    }

    public void setEmail(String email) {
        List<String> list = getUserType().getEMailAddress();
        list.clear();
        list.add(email);
    }

    public String getEmail() {
        List<String> list = getUserType().getEMailAddress();
        if (list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    public String getHonorificPrefix() {
        return getUserType().getHonorificPrefix();
    }

    public void setHonorificPrefix(String value) {
        getUserType().setHonorificPrefix(value);
    }

    public String getHonorificSuffix() {
        return getUserType().getHonorificSuffix();
    }

    public void setHonorificSuffix(String value) {
        getUserType().setHonorificSuffix(value);
    }

    public List<AccountShadowDto> getAccount() {
        List<AccountShadowType> accounts = getUserType().getAccount();
        List<AccountShadowDto> accountDtos = new ArrayList<AccountShadowDto>();

        for (AccountShadowType account : accounts) {
            accountDtos.add(new AccountShadowDto(account));
        }
        return accountDtos;
    }

    public List<ObjectReferenceDto> getAccountRef() {
        List<ObjectReferenceType> accountRefs = getUserType().getAccountRef();
        List<ObjectReferenceDto> accountRefDtos = new ArrayList<ObjectReferenceDto>();

        for (ObjectReferenceType ref : accountRefs) {
            accountRefDtos.add(new ObjectReferenceDto(ref));
        }

        return accountRefDtos;
    }

    public String getEmployeeNumber() {
        return getUserType().getEmployeeNumber();
    }

    public void setEmployeeNumber(String value) {
        getUserType().setEmployeeNumber(value);
    }

    public String getLocality() {
        return getUserType().getLocality();
    }

    public void setLocality(String value) {
        getUserType().setLocality(value);
    }
}
