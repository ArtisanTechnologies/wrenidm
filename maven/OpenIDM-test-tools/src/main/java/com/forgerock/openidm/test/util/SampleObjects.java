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
package com.forgerock.openidm.test.util;

/**
 * Sample Class Doc
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0.0
 */
public enum SampleObjects {

    /**
     * Localhost OpenDJ (ResourceType)
     */
    RESOURCETYPE_LOCALHOST_OPENDJ("ef2bc95b-76e0-48e2-86d6-3d4f02d3e1a2"),
    /**
     * Flatfile (ResourceType)
     */
    RESOURCETYPE_FLATFILE("eced6f20-df52-11df-9e9a-0002a5d5c51b"),
    /**
     * Localhost DatabaseTable (ResourceType)
     */
    RESOURCETYPE_LOCALHOST_DATABASETABLE("aae7be60-df56-11df-8608-0002a5d5c51b"),
    /**
     * OpenDJ jbond (AccountShadowType)
     */
    ACCOUNTSHADOWTYPE_OPENDJ_JBOND("dbb0c37d-9ee6-44a4-8d39-016dbce18b4c"),
    /**
     * OpenDJ unsaved jdoe (AccountShadowType)
     */
    ACCOUNTSHADOWTYPE_OPENDJ_JDOE("e341e691-0b89-4245-9ec8-c20f63b69714"),
    /**
     * Identity Connector Integration (ResourceAccessConfigurationType)
     */
    RESOURCEACCESSCONFIGURATIONTYPE_IDENTITY_CONNECTOR_INTEGRATION("acf6bd6d-d3ee-4756-bf94-22c2f5168f63");
    private String OID;

    private SampleObjects(String _key) {
        this.OID = _key;
    }

    public String getOID() {
        return OID;

    }

    public static SampleObjects findByOID(String value) {
        for (SampleObjects f : SampleObjects.values()) {
            if (f.getOID().equals(value)) {
                return f;
            }
        }
        return null;
    }
}
