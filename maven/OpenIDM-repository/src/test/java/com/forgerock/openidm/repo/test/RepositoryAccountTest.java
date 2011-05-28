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
package com.forgerock.openidm.repo.test;

import com.forgerock.openidm.util.QNameUtil;
import com.forgerock.openidm.util.Utils;
import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.xml.ns._public.common.common_1.AccountShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectContainerType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.OrderDirectionType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PagingType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserType;
import com.forgerock.openidm.xml.ns._public.repository.repository_1.RepositoryPortType;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import java.io.File;
import java.math.BigInteger;
import javax.xml.bind.JAXBElement;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 *
 * @author sleepwalker
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"../../../../../application-context-repository.xml", "../../../../../application-context-repository-test.xml"})
public class RepositoryAccountTest {

    @Autowired(required = true)
    private RepositoryPortType repositoryService;

    public RepositoryPortType getRepositoryService() {
        return repositoryService;
    }

    public void setRepositoryService(RepositoryPortType repositoryService) {
        this.repositoryService = repositoryService;
    }

    public RepositoryAccountTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testAccount() throws Exception {
        String oid = "dbb0c37d-9ee6-44a4-8d39-016dbce18b4c";
        final String resourceOid = "aae7be60-df56-11df-8608-0002a5d5c51b";
        try {
            //add resource referenced by resourcestate
            ObjectContainerType objectContainer = new ObjectContainerType();
            ResourceType resource = ((JAXBElement<ResourceType>) JAXBUtil.unmarshal(new File("target/test-data/repository/aae7be60-df56-11df-8608-0002a5d5c51b.xml"))).getValue();
            objectContainer.setObject(resource);
            repositoryService.addObject(objectContainer);
            ObjectContainerType retrievedObjectContainer = repositoryService.getObject(resourceOid, new PropertyReferenceListType());
            assertEquals(resource.getOid(), ((ResourceType) (retrievedObjectContainer.getObject())).getOid());

            objectContainer = new ObjectContainerType();
            AccountShadowType accountShadow = ((JAXBElement<AccountShadowType>) JAXBUtil.unmarshal(new File("src/test/resources/account.xml"))).getValue();
            objectContainer.setObject(accountShadow);
            repositoryService.addObject(objectContainer);
            retrievedObjectContainer = repositoryService.getObject(oid, new PropertyReferenceListType());
            assertEquals(accountShadow.getOid(), ((AccountShadowType) (retrievedObjectContainer.getObject())).getOid());
            assertEquals(accountShadow.getCredentials().getPassword().getAny().toString(), ((AccountShadowType) (retrievedObjectContainer.getObject())).getCredentials().getPassword().getAny().toString());

            PagingType pagingType = new PagingType();
            pagingType.setMaxSize(BigInteger.valueOf(5));
            pagingType.setOffset(BigInteger.valueOf(-1));
            pagingType.setOrderBy(Utils.fillPropertyReference("name"));
            pagingType.setOrderDirection(OrderDirectionType.ASCENDING);
            ObjectListType objects = repositoryService.listObjects(QNameUtil.qNameToUri(SchemaConstants.I_ACCOUNT_TYPE), pagingType);
            assertEquals(1, objects.getObject().size());
            assertEquals(oid, objects.getObject().get(0).getOid());
        } finally {
            repositoryService.deleteObject(oid);
            repositoryService.deleteObject(resourceOid);
        }
    }

}
