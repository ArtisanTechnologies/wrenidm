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

import com.forgerock.openidm.util.constants.OpenIdmConstants;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PropertyReferenceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import com.forgerock.openidm.xml.schema.XPathSegment;
import com.forgerock.openidm.xml.schema.XPathType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;
import org.w3c.dom.Node;

/**
 * TODO
 *
 * @author Igor Farinic
 * @version $Revision$ $Date$
 * @since 0.1
 */
public class Utils {

    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Utils.class);

    public static String getObjectType(String objectName) {

        if (SchemaConstants.I_USER_TYPE.getLocalPart().equals(objectName)) {
            return QNameUtil.qNameToUri(SchemaConstants.I_USER_TYPE);
        }

        if (SchemaConstants.I_ACCOUNT_TYPE.getLocalPart().equals(objectName)) {
            return QNameUtil.qNameToUri(SchemaConstants.I_ACCOUNT_TYPE);
        }

        if (SchemaConstants.I_RESOURCE_TYPE.getLocalPart().equals(objectName)) {
            return QNameUtil.qNameToUri(SchemaConstants.I_RESOURCE_TYPE);
        }

        if (SchemaConstants.I_RESOURCE_STATE_TYPE.getLocalPart().equals(objectName)) {
            return QNameUtil.qNameToUri(SchemaConstants.I_RESOURCE_STATE_TYPE);
        }

        if (SchemaConstants.I_USER_TEMPLATE_TYPE.getLocalPart().equals(objectName)) {
            return QNameUtil.qNameToUri(SchemaConstants.I_USER_TEMPLATE_TYPE);
        }

        if (SchemaConstants.I_GENERIC_OBJECT_TYPE.getLocalPart().equals(objectName)) {
            return QNameUtil.qNameToUri(SchemaConstants.I_GENERIC_OBJECT_TYPE);
        }

        throw new IllegalArgumentException("UnsupportedObjectType = " + objectName);

    }

    public static String getPropertyName(String name) {
        if (null == name) {
            return "";
        }
        return StringUtils.lowerCase(name);
    }

    public static String getPropertySilent(Object object, String property) {
        String result = null;
        try {
            result = BeanUtils.getProperty(object, property);
        } catch (IllegalAccessException ex) {
            logger.warn("Failed to get property for instances {}, {}. Error message was {}", new Object[]{object.getClass().getName(), property, ex.getMessage()});
        } catch (InvocationTargetException ex) {
            logger.warn("Failed to get property for instances {}, {}. Error message was {}", new Object[]{object.getClass().getName(), property, ex.getMessage()});
        } catch (NoSuchMethodException ex) {
            logger.warn("Failed to get property for instances {}, {}. Error message was {}", new Object[]{object.getClass().getName(), property, ex.getMessage()});
        }
        return result;
    }

    public static void copyPropertiesSilent(Object target, Object source) {
        try {
            BeanUtils.copyProperties(target, source);

            //copy properties of type List, if target destination does not have setter (e.g. JAXB java object)
            final Field fields[] = target.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; ++i) {
                if ("List".equals(fields[i].getType().getSimpleName())) {
                    ParameterizedType type = (ParameterizedType) fields[i].getGenericType();
                    if (null != type && type.getActualTypeArguments().length > 0 && "String".equals(((Class)type.getActualTypeArguments()[0]).getSimpleName())) {

                        boolean existsSetter = true;
                        try {
                            Method targetSetterMethod = target.getClass().getDeclaredMethod("set" + StringUtils.capitalise(fields[i].getName()), List.class);
                            if (null == targetSetterMethod) {
                                existsSetter = false;
                            }
                        } catch (NoSuchMethodException ex) {
                            //if there is setter for the property on target object, then
                            existsSetter = false;
                        }
                        if (!existsSetter) {
                            Method targetMethod = target.getClass().getDeclaredMethod("get" + StringUtils.capitalise(fields[i].getName()));
                            Method sourceMethod = source.getClass().getDeclaredMethod("get" + StringUtils.capitalise(fields[i].getName()));
                            if (null != targetMethod && null != sourceMethod) {
                                List<String> targetList = (List) targetMethod.invoke(target);
                                List<String> sourceList = (List) sourceMethod.invoke(source);
                                if (targetList != null) {
                                    targetList.clear();
                                    if (sourceList != null) {
                                        for (Object str : sourceList) {
                                            if (str instanceof String) {
                                                targetList.add((String) str);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (NoSuchMethodException ex) {
            logger.warn("Failed to copy properties for instances {}, {}. Error message was {}", new Object[]{source, target, ex.getMessage()});
        } catch (SecurityException ex) {
            logger.warn("Failed to copy properties for instances {}, {}. Error message was {}", new Object[]{source, target, ex.getMessage()});
        } catch (IllegalAccessException ex) {
            logger.warn("Failed to copy properties for instances {}, {}. Error message was {}", new Object[]{source, target, ex.getMessage()});
        } catch (InvocationTargetException ex) {
            logger.warn("Failed to copy properties for instances {}, {}. Error message was {}", new Object[]{source, target, ex.getMessage()});
        }
    }

    public static boolean toResolve(String propertyName, PropertyReferenceListType resolve) {
        for (PropertyReferenceType property : resolve.getProperty()) {
            XPathType xpath = new XPathType(property.getProperty());
            List<XPathSegment> segments = xpath.toSegments();
            if (!CollectionUtils.isEmpty(segments)) {
                if (getPropertyName(propertyName).equals(segments.get(0).getQName().getLocalPart())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void unresolveResource(ResourceObjectShadowType shadow) {
        if (null != shadow.getResource()) {
            ObjectReferenceType ort = new ObjectReferenceType();
            ort.setOid(shadow.getResource().getOid());
            ort.setType(SchemaConstants.I_RESOURCE_TYPE);
            shadow.setResourceRef(ort);
            shadow.setResource(null);

        }
    }

    public static void unresolveResourceForAccounts(List<? extends ResourceObjectShadowType> shadows) {
        for (ResourceObjectShadowType shadow : shadows) {
            unresolveResource(shadow);
        }
    }

    public static PropertyReferenceType fillPropertyReference(String resolve) {
        PropertyReferenceType property = new PropertyReferenceType();
        com.forgerock.openidm.xml.schema.XPathType xpath = new com.forgerock.openidm.xml.schema.XPathType(Utils.getPropertyName(resolve));
        property.setProperty(xpath.toElement(SchemaConstants.NS_C, "property"));
        return property;
    }

    public static PropertyReferenceListType getResolveResourceList() {
        PropertyReferenceListType resolveListType = new PropertyReferenceListType();
        resolveListType.getProperty().add(Utils.fillPropertyReference("Account"));
        resolveListType.getProperty().add(Utils.fillPropertyReference("Resource"));
        return resolveListType;
    }

    public static String getNodeOid(Node node) {
        Node oidNode = null;
        if ((null == node.getAttributes()) ||
                (null == (oidNode = node.getAttributes().getNamedItem(OpenIdmConstants.ATTR_OID_NAME))) ||
                (StringUtils.isEmpty(oidNode.getNodeValue()))) {
            return null;
        }
        String oid = oidNode.getNodeValue();
        return oid;
    }

    /**
     * Removing non-printable UTF characters from the string.
     *
     * This is not really used now. It was done as a kind of prototype for
     * filters. But may come handy and it in fact tests that the pattern is
     * doing what expected, so it may be useful.
     *
     * @param bad string with bad chars
     * @return string without bad chars
     */
    public static String cleanupUtf(String bad) {

        StringBuilder sb = new StringBuilder(bad.length());

        for(int cp, i = 0; i < bad.length(); i += Character.charCount(cp)) {
                cp = bad.codePointAt(i);
                if (isValidXmlCodepoint(cp)) {
                    sb.append(Character.toChars(cp));
                }
        }

        return sb.toString();
    }

    /**
     * According to XML specification, section 2.2:
     * http://www.w3.org/TR/REC-xml/
     * 
     * @param c
     * @return
     */
    public static boolean isValidXmlCodepoint(int cp) {
        return (cp == 0x0009 ||
                cp == 0x000a ||
                cp == 0x000d ||
                ( cp >= 0x0020 && cp <= 0xd7ff) ||
                ( cp >= 0xe000 && cp <= 0xfffd) ||
                ( cp >= 0x10000 && cp <= 0x10FFFF));
    }
}
