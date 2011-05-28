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

import com.forgerock.openidm.util.DOMUtil;
import com.forgerock.openidm.util.QNameUtil;
import com.forgerock.openidm.util.QueryUtil;
import com.forgerock.openidm.util.jaxb.JAXBUtil;
import com.forgerock.openidm.xml.ns._public.common.common_1.AccountShadowType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectContainerType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PagingType;
import com.forgerock.openidm.xml.ns._public.common.common_1.QueryType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceStateType;
import com.forgerock.openidm.xml.ns._public.common.common_1.UserType;
import com.forgerock.openidm.xml.ns._public.repository.repository_1.RepositoryPortType;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import com.forgerock.openidm.xml.schema.XPathSegment;
import com.forgerock.openidm.xml.schema.XPathType;
import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import javax.xml.bind.JAXBElement;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"../../../../../application-context-repository.xml", "../../../../../application-context-repository-test.xml"})
public class RepositorySearchTest {

    @Autowired(required = true)
    private DataSource dataSource;
    @Autowired(required = true)
    private RepositoryPortType repositoryService;

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public RepositoryPortType getRepositoryService() {
        return repositoryService;
    }

    public void setRepositoryService(RepositoryPortType repositoryService) {
        this.repositoryService = repositoryService;
    }

    public RepositorySearchTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        addDataToDB("target/test-data/dbunit/empty-dataset.xml");
        addDataToDB("target/test-data/dbunit/full-dataset.xml");
    }

    @After
    public void tearDown() {
        addDataToDB("target/test-data/dbunit/empty-dataset.xml");
    }

    private void addDataToDB(String filename) {

        try {
            Connection con = DataSourceUtils.getConnection(getDataSource());
            IDatabaseConnection connection = new DatabaseConnection(con);
            // initialize your dataset here
            IDataSet dataSet = new FlatXmlDataSet(new File(filename));
            try {
                DatabaseOperation.CLEAN_INSERT.execute(connection, dataSet);
            } finally {
                connection.close();
                con.close();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load test data to DB");
        }
    }

    @Test
    public void searchUser() throws Exception {
        QueryType query = (QueryType) ((JAXBElement) JAXBUtil.unmarshal(new File("target/test-data/query/query-user-by-name.xml"))).getValue();
        ObjectListType objectList = repositoryService.searchObjects(query, new PagingType());
        assertNotNull(objectList);
        assertNotNull(objectList.getObject());
        assertEquals(1, objectList.getObject().size());

        UserType user = (UserType) objectList.getObject().get(0);
        assertEquals("captain Jack Sparrow", user.getFullName());
    }

    @Test
    public void searchAccountByAttributes() throws Exception {
        QueryType query = (QueryType) ((JAXBElement) JAXBUtil.unmarshal(new File("target/test-data/query/query-account-by-attributes.xml"))).getValue();
        ObjectListType objectList = repositoryService.searchObjects(query, new PagingType());
        assertNotNull(objectList);
        assertNotNull(objectList.getObject());
        assertEquals(1, objectList.getObject().size());

        AccountShadowType accountShadow = (AccountShadowType) objectList.getObject().get(0);
        assertNotNull(accountShadow.getAttributes().getAny());
        assertEquals("cn=foobar,uo=people,dc=nlight,dc=eu", accountShadow.getAttributes().getAny().get(0).getTextContent());
    }

    @Test
    public void searchAccountByAttributesAndResourceRef() throws Exception {
        QueryType query = (QueryType) ((JAXBElement) JAXBUtil.unmarshal(new File("target/test-data/query/query-account-by-attributes-and-resource-ref.xml"))).getValue();
        ObjectListType objectList = repositoryService.searchObjects(query, new PagingType());
        assertNotNull(objectList);
        assertNotNull(objectList.getObject());
        assertEquals(1, objectList.getObject().size());

        AccountShadowType accountShadow = (AccountShadowType) objectList.getObject().get(0);
        assertNotNull(accountShadow.getAttributes().getAny());
        assertEquals("cn=foobar,uo=people,dc=nlight,dc=eu", accountShadow.getAttributes().getAny().get(0).getTextContent());
    }

    @Test
    public void searchResourceStateByResourceRef() throws Exception {
        //insert new resource state
        ResourceStateType newResourceState = new ResourceStateType();
        newResourceState.setName("ResourceStateForSearch");
        ObjectReferenceType resourceRef = new ObjectReferenceType();
        resourceRef.setOid("d0db5be9-cb93-401f-b6c1-86ffffe4cd5e");
        newResourceState.setResourceRef(resourceRef);
        ResourceStateType.SynchronizationState state = new ResourceStateType.SynchronizationState();
        Document doc = DOMUtil.getDocument();
        Element element = doc.createElement("fakeNode");
        element.setTextContent("fakeValue");
        doc.appendChild(element);
        state.getAny().add((Element) doc.getFirstChild());
        newResourceState.setSynchronizationState(state);

        ObjectContainerType container = new ObjectContainerType();
        container.setObject(newResourceState);
        repositoryService.addObject(container);

        //run search for object
        QueryType query = (QueryType) ((JAXBElement) JAXBUtil.unmarshal(new File("target/test-data/query/query-resource-state-by-resource-ref.xml"))).getValue();
        ObjectListType objectList = repositoryService.searchObjects(query, new PagingType());
        assertNotNull(objectList);
        assertNotNull(objectList.getObject());
        assertEquals(1, objectList.getObject().size());

        ResourceStateType resourceState = (ResourceStateType) objectList.getObject().get(0);
        assertNotNull(resourceState);
        assertNotNull("d0db5be9-cb93-401f-b6c1-86ffffe4cd5e", resourceState.getResourceRef().getOid());
        assertNotNull(resourceState.getSynchronizationState().getAny());
    }

    @Test(expected = IllegalArgumentException.class)
    public void searchAccountByNoAttributesUseQueryUtil() throws Exception {
        XPathSegment xpathSegment = new XPathSegment(SchemaConstants.I_ATTRIBUTES);
        Document doc = DOMUtil.getDocument();
        List<XPathSegment> xpathSegments = new ArrayList<XPathSegment>();
        xpathSegments.add(xpathSegment);
        XPathType xpath = new XPathType(xpathSegments);

        List<Element> values = new ArrayList<Element>();

        Element filter =
                QueryUtil.createAndFilter(doc,
                QueryUtil.createTypeFilter(doc, QNameUtil.qNameToUri(SchemaConstants.I_ACCOUNT_TYPE)),
                QueryUtil.createEqualFilter(doc, xpath, values));

        QueryType query = new QueryType();
        query.setFilter(filter);

        ObjectListType objectList = repositoryService.searchObjects(query, new PagingType());

    }

    @Test
    public void searchAccountByAttributesUseQueryUtil() throws Exception {
        XPathSegment xpathSegment = new XPathSegment(SchemaConstants.I_ATTRIBUTES);
        Document doc = DOMUtil.getDocument();
        List<XPathSegment> xpathSegments = new ArrayList<XPathSegment>();
        xpathSegments.add(xpathSegment);
        XPathType xpath = new XPathType(xpathSegments);

        List<Element> values = new ArrayList<Element>();
        values.add((Element) DOMUtil.parseDocument("<dj:__UID__ xmlns:dj=\"http://openidm.forgerock.com/xml/ns/samples/localhostOpenDJ\">cn=foobar,uo=people,dc=nlight,dc=eu</dj:__UID__>").getFirstChild());

        Element filter =
                QueryUtil.createAndFilter(doc,
                QueryUtil.createTypeFilter(doc, QNameUtil.qNameToUri(SchemaConstants.I_ACCOUNT_TYPE)),
                QueryUtil.createEqualFilter(doc, xpath, values));

        System.out.println(DOMUtil.serializeDOMToString(filter));

        QueryType query = new QueryType();
        query.setFilter(filter);

        ObjectListType objectList = repositoryService.searchObjects(query, new PagingType());

        assertNotNull(objectList);
        assertNotNull(objectList.getObject());
        assertEquals(1, objectList.getObject().size());

        AccountShadowType accountShadow = (AccountShadowType) objectList.getObject().get(0);
        assertNotNull(accountShadow.getAttributes().getAny());
        assertEquals("cn=foobar,uo=people,dc=nlight,dc=eu", accountShadow.getAttributes().getAny().get(0).getTextContent());

    }
}
