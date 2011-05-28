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
package com.forgerock.openidm.util;

import com.forgerock.openidm.api.logging.Trace;
import com.forgerock.openidm.logging.TraceManager;
import com.forgerock.openidm.xml.schema.ExpressionHolder;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import com.forgerock.openidm.xml.schema.XPathType;
import com.forgerock.openidm.xpath.OpenIdmNamespaceContext;
import com.forgerock.openidm.xpath.OpenIdmXPathFunctionResolver;
import com.forgerock.openidm.xpath.functions.CapitalizeFunction;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathVariableResolver;
import org.apache.commons.lang.Validate;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Vilo Repan
 */
public class XPathUtil {

    private static final Trace logger = TraceManager.getTrace(XPathUtil.class);

    public static Object evaluateExpression(Map<QName, Variable> variables, ExpressionHolder expressionHolder, QName returnType) {
        return new XPathUtil().evaluateExpr(variables, expressionHolder, returnType);
    }

    protected Object evaluateExpr(Map<QName, Variable> variables, ExpressionHolder expressionHolder, QName returnType) {
        logger.trace("Expression '{}' will be evaluated in context: variables = '{}' and namespaces = '{}'", new Object[]{expressionHolder.getExpressionAsString(), variables.values(), expressionHolder.getNamespaceMap()});

        Validate.notNull(expressionHolder);
//        Validate.notNull(variables);
//        Validate.notEmpty(variables);

        try {
            XPath xpath = setupXPath(variables, expressionHolder.getNamespaceMap());
            XPathExpression expr = xpath.compile(expressionHolder.getExpressionAsString());
            //TODO: we will probably need to update interface and add parameter nodeForEval - node on which do the xpath evaluation
            Node nodeForEval = variables.get(SchemaConstants.I_ACCOUNT) == null ? null : (Node) variables.get(SchemaConstants.I_ACCOUNT).getObject();
            if (null == nodeForEval) {
                nodeForEval = variables.get(SchemaConstants.I_USER) == null ? null : (Node) variables.get(SchemaConstants.I_USER).getObject();
            }
            Object evaluatedExpression = expr.evaluate(nodeForEval, returnType);
            logger.trace("Expression '{}' was evaluated to '{}' ", new Object[]{expressionHolder.getExpressionAsString(), evaluatedExpression});
            return evaluatedExpression;
        } catch (XPathExpressionException ex) {
            //TODO: implement properly
            throw new IllegalArgumentException("Expression (" + expressionHolder.getExpressionAsString() + ") evaluation failed", ex);
            //return "";
        }
    }

    protected XPath setupXPath() {
        //Note: probably no validation required
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();

        return xpath;
    }

    protected XPath setupXPath(Map<QName, Variable> variables, Map<String, String> namespaces) {
        //Note: probably no validation required
        XPath xpath = setupXPath();
        if (null != variables) {
            XPathVariableResolver variableResolver = new MapXPathVariableResolver(variables);
            xpath.setXPathVariableResolver(variableResolver);
        }
        xpath.setNamespaceContext(new OpenIdmNamespaceContext(namespaces));
        OpenIdmXPathFunctionResolver fc = new OpenIdmXPathFunctionResolver();
        fc.registerFunction(new QName("http://openidm.forgerock.com/custom", "capitalize"), new CapitalizeFunction());
        xpath.setXPathFunctionResolver(fc);
        return xpath;
    }

    public NodeList matchedNodesByXPath(XPathType xpathType, Map<QName, Variable> variables, Node domObject) throws XPathExpressionException {
        Validate.notNull(xpathType, "xpathType is null");
        Validate.notNull(domObject, "domObject is null");
        try {
            XPath xpath = setupXPath(variables, xpathType.getNamespaceMap());
            XPathExpression expr = xpath.compile(xpathType.getXPath());
            NodeList result = (NodeList) expr.evaluate(domObject, XPathConstants.NODESET);

            return result;
        } catch (XPathExpressionException ex) {
            throw new IllegalStateException(ex);
        }

    }
//    protected static NodeList matchedNodesByXPath(Document doc, XPath xpath, String xpathString, Map namespaces) throws XPathExpressionException {
//
//        //we have to register all namespaces required by xpath,
//        //for every change it could be different set of namespaces
//        xpath.setNamespaceContext(new OpenIdmNamespaceContext(namespaces));
//        XPathExpression expr = xpath.compile(xpathString);
//        //Note: Here we are doing xpath evaluation not on document, but on the first child = root parentNode
//        NodeList result = (NodeList) expr.evaluate(doc.getFirstChild(), XPathConstants.NODESET);
//
//        return result;
//
//    }
}
