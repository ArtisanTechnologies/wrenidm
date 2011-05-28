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
package com.forgerock.openidm.app.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.faces.application.Application;
import javax.faces.application.ViewHandler;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Viliam Repan
 */
@ManagedBean(name = "lang")
@SessionScoped
public class LanguageBean implements Serializable {

    private final transient Logger logger = LoggerFactory.getLogger(LanguageBean.class);
    private Map<String, String> messages = new LangHashMap();
    private Locale currentLocale;

    public Map<String, String> getMessages() {
        return messages;
    }

    public String translate(String key) {
        return messages.get(key);
    }

    public Locale getCurrentLocale() {
        if (currentLocale == null) {
            Application application = FacesContext.getCurrentInstance().getApplication();
            Locale locale = application.getDefaultLocale();
            if (locale == null) {
                logger.warn("Default locale not found, using locale: '" + Locale.ENGLISH.toString() + "'.");
                locale = Locale.ENGLISH;
            }
            setCurrentLocale(locale);
        }
        
        return currentLocale;
    }

    public void setCurrentLocale(Locale locale) {
        logger.trace("setCurrentLocale::begin");
        try {
            if (locale == null) {
                throw new IllegalArgumentException("Locale can't be null.");
            }

            Application application = FacesContext.getCurrentInstance().getApplication();
            ResourceBundle bundle = ResourceBundle.getBundle(application.getMessageBundle(), locale);
            if (bundle != null) {
                messages = new LangHashMap();

                Enumeration<String> enumer = bundle.getKeys();
                while (enumer.hasMoreElements()) {
                    String key = enumer.nextElement();
                    messages.put(key, bundle.getString(key));
                }

                this.currentLocale = locale;

                FacesContext.getCurrentInstance().getViewRoot().setLocale(this.currentLocale);
//                refresh();
            } else {
                throw new RuntimeException("Can't find resource bundle for locale '" + locale + "'.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        logger.trace("setCurrentLocale::end");
    }

    @Deprecated
    public void refresh() {
        FacesContext context = FacesContext.getCurrentInstance();
        Application application = context.getApplication();
        ViewHandler viewHandler = application.getViewHandler();

        UIViewRoot viewRoot = viewHandler.createView(context, context.getViewRoot().getViewId());
        context.setViewRoot(viewRoot);
        context.renderResponse();
    }

    public void setCurrentLanguage(final String locale) {
        if (locale == null || locale.isEmpty()) {
            throw new IllegalArgumentException("Locale can't be null or empty string.");
        }

        String[] array = locale.split("_");
        if (array.length == 0) {
            throw new IllegalArgumentException("Unknown locale format '" + locale + "', should be en, en_US, en_US_US for example.");
        }
        String language = array[0];
        String country = "";
        String variant = "";
        if (array.length > 1) {
            country = array[1];
        }
        if (array.length > 2) {
            variant = array[2];
        }

        setCurrentLocale(new Locale(language, country, variant));
    }

    public List<Locale> getSupportedLocales() {
        List<Locale> locales = new ArrayList<Locale>();
        Application application = FacesContext.getCurrentInstance().getApplication();
        for (Iterator<Locale> it = application.getSupportedLocales(); it.hasNext();) {
            Locale locale = it.next();
            locales.add(locale);
        }

        Collections.sort(locales, new Comparator<Locale>() {

            @Override
            public int compare(Locale l1, Locale l2) {
                return String.CASE_INSENSITIVE_ORDER.compare(l1.toString(), l2.toString());
            }
        });

        return locales;
    }

    /**
     * Like normal HashMap, but if it can't translate key (can't find value) returns key.
     */
    private class LangHashMap extends HashMap<String, String> {

        @Override
        public String get(Object key) {
            if (key == null) {
                logger.warn("Key '" + key + "' for translation can't be null.");
                return null;
            }

            if (!super.containsKey(key.toString())) {
                logger.warn("Can't find key '" + key + "' in resource for translation.");
                return "!" + key.toString() + "!";
            }

            return super.get(key.toString());
        }
    }
}
