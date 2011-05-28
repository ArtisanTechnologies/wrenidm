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

package com.forgerock.openidm.util.test;

import com.forgerock.openidm.util.DOMUtil;
import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserType;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static org.junit.Assert.*;

/**
 *
 * @author semancik
 */
public class JAXBUtilTest {

    public JAXBUtilTest() {
    }

    static Document doc;

    @BeforeClass
    public static void setUpClass() throws Exception {
        doc = DOMUtil.getDocument();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Test
    public void jaxbToDomTest1() throws JAXBException {
        UserType o = new UserType();
        o.setOid("1234");
        o.setName("foobar");

        Element el = JAXBUtil.jaxbToDom(o, new QName("http://foo/","bar","x"), doc);

        assertNotNull(el);
        assertEquals("bar",el.getLocalName());

//        System.out.println("EL: "+el);
//        System.out.println(DOMUtil.serializeDOMToString(el));
        
    }

        @Test
    public void jaxbToDomTest2() throws JAXBException {
        ObjectReferenceType o = new ObjectReferenceType();
        o.setOid("1234");

        Element el = JAXBUtil.jaxbToDom(o, new QName("http://foo/","accountRef","x"), doc);

        assertNotNull(el);
        assertEquals("accountRef",el.getLocalName());

        System.out.println("EL2: "+el);
        System.out.println(DOMUtil.serializeDOMToString(el));

    }


}