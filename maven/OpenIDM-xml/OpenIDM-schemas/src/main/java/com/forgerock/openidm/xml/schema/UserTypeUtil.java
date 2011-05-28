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
 * "Portions Copyrighted 2011 [name of copyright owner]"
 *
 * Portions Copyrighted 2011 Radovan Semancik
 * 
 * $Id$
 */

package com.forgerock.openidm.xml.schema;

import com.forgerock.openidm.xml.ns._public.common.common_1.AccountShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserType;

/**
 * Utility class for easier manipulation of UserType.
 *
 * This exists because UserType is generated from XML (JAXB) and it is not
 * practical to add methods there. So the methods that should be in UserType
 * are here. Ugly, but work.
 * 
 * @author semancik
 */
public class UserTypeUtil {

    /**
     * Returns accountRef for supplied OID.
     * If there is appopriate account object, it will get converted to
     * the reference and returned as well. So this may be used to check if
     * user has an account already.
     *
     * @param oid OID of resource to look up in user accounts (must no be null)
     * @return object reference describing the appropriate accountRef or account,
     *          null if nothing was found
     * @throws IllegalStateException if more than one reference exists for a resource
     */
    public static ObjectReferenceType findAccountRef(UserType user, String resourceOid) {

        ObjectReferenceType res = null;

        for (ObjectReferenceType ref : user.getAccountRef()) {
            if (resourceOid.equals(ref.getOid())) {
                if (res == null) {
                    res = ref;
                } else {
                    throw new IllegalStateException("User "+user.getOid()+" has more than one account for resource "+resourceOid);
                }
            }
        }

        for (AccountShadowType acc : user.getAccount()) {
            if (resourceOid.equals(acc.getOid())) {
                if (res == null) {
                    res = new ObjectReferenceType();
                    res.setOid(resourceOid);
                    res.setType(SchemaConstants.I_ACCOUNT_TYPE);
                } else {
                    throw new IllegalStateException("User "+user.getOid()+" has more than one account for resource "+resourceOid);
                }
            }
        }

        return res;
    }

}
