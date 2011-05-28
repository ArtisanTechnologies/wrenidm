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
 * $Id$
 */
package com.forgerock.openidm.provisioning.synchronization;

import com.forgerock.openidm.api.logging.Trace;
import com.forgerock.openidm.logging.TraceManager;
import com.forgerock.openidm.provisioning.service.ProvisioningService;
import com.forgerock.openidm.util.QNameUtil;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectListType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ObjectType;
import com.forgerock.openidm.xml.ns._public.common.common_1.OperationalResultType;
import com.forgerock.openidm.xml.ns._public.common.common_1.PagingType;
import com.forgerock.openidm.xml.ns._public.common.common_1.ResourceType;
import com.forgerock.openidm.xml.ns._public.common.common_1.SynchronizationType;
import com.forgerock.openidm.xml.ns._public.provisioning.provisioning_1.FaultMessage;
import com.forgerock.openidm.xml.schema.SchemaConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.ws.Holder;

/**
 *
 * @author semancik
 */
public class SynchronizationProcess extends Thread {

    // TODO: This should be configurable.
    // But we don't have global configuration obejct now.
    private static final long SLEEP_INTERVAL = 60000;

    ProvisioningService provisioning;
    private boolean enabled = true;
    private long lastLoopRun = 0;
    private Map<String, Long> lastResourceRun;
    private static final transient Trace logger = TraceManager.getTrace(SynchronizationProcess.class);

    public SynchronizationProcess(ProvisioningService provisioningService) {
        enabled = true;
        provisioning = provisioningService;
        lastResourceRun = new HashMap<String, Long>();
    }

    @Override
    public void run() {
        logger.info("Synchronization thread starting");
        while (enabled) {

            logger.trace("Synchronization thread loop: start");

            lastLoopRun = System.currentTimeMillis();

            PagingType paging = new PagingType();
            Holder<OperationalResultType> resultHolder = new Holder<OperationalResultType>();
            ObjectListType listObjects = null;
            try {
                listObjects = provisioning.listObjects(QNameUtil.qNameToUri(SchemaConstants.I_RESOURCE_TYPE), paging, resultHolder);
            } catch (FaultMessage ex) {
                // TODO: Better error reporting
                logger.error("Synchronizatoin thread got provisioning fault (listObjects):" + ex);
            }
            if (listObjects != null) {
                List<ObjectType> objectList = listObjects.getObject();
                for (Object o : objectList) {
                    if (o instanceof ResourceType) {
                        ResourceType resource = (ResourceType) o;
                        logger.trace("Synchronization thread: Start processing resource " + resource.getName() + " (OID: " + resource.getOid() + ")");

                        SynchronizationType synchronization = resource.getSynchronization();
                        if (synchronization == null) {
                            logger.trace("Synchronization thread: skipping resource " + resource.getName() + " because it does not have synchronization section");
                        } else {
                            if (synchronization.isEnabled() == null || synchronization.isEnabled()) {

                                String oid = resource.getOid();
                                long pollingInterval = 0;
                                if (synchronization.getPollingInterval() != null) {
                                    // The time in XML is in seconds. We need millis here.
                                    pollingInterval = synchronization.getPollingInterval().longValue() * 1000;
                                }

                                if (lastResourceRun.get(oid) == null || lastResourceRun.get(oid) + pollingInterval < System.currentTimeMillis()) {
                                    long startTime = System.currentTimeMillis();

                                    try {

                                        logger.debug("Synchronization Thread: calling synchronize() for resource "+resource.getName()+" oid: "+oid);

                                        provisioning.synchronize(oid);

                                        // Remember the start time only if the call is successful
                                        lastResourceRun.put(oid, startTime);
                                        
                                    } catch (FaultMessage ex) {
                                        // TODO: Better error reporting
                                        logger.error("Synchronizatoin thread got provisioning fault (synchronize): {} : {}",new Object[]{ex.getClass().getSimpleName(),ex.getMessage(),ex});
                                    } catch (RuntimeException ex) {
                                        // Runtime exceptions are used from time to time, althought all the
                                        // exceptions that could be reasonably caught should be transformed to
                                        // Faults, obvious not all of them are. Do not cause this thread to die
                                        // because of bug in the synchronize method.

                                        // TODO: Better error reporting
                                        logger.error("Synchronizatoin thread got runtime exception (synchronize): {} : {}",new Object[]{ex.getClass().getSimpleName(),ex.getMessage(),ex});
                                    }
                                }
                            } else {
                                logger.trace("Synchronization thread: skipping resource " + resource.getName() + " because it is not enabled");
                            }

                        }

                        logger.trace("Synchronization thread: End processing resource " + resource.getName() + " (OID: " + resource.getOid() + ")");
                    } else {
                        logger.error("Synchronization thread got unexpected object type in listObjects: " + o.getClass().getName());
                        // skip it
                    }
                }
            }

            if (lastLoopRun + SLEEP_INTERVAL > System.currentTimeMillis()) {

                // Let's sleep a while to slow down the synch, to avoid
                // overloading the system with sync polling

                logger.trace("Synchronization thread loop: going to sleep");

                try {
                    Thread.sleep(SLEEP_INTERVAL - (System.currentTimeMillis() - lastLoopRun));
                } catch (InterruptedException ex) {
                    logger.trace("Synchronization thread got InterruptedException: " + ex);
                    // Safe to ignore
                }
            }
            logger.trace("Synchronization thread loop: end");
        }
        logger.info("Synchronization thread stopping");
    }

    public void disable() {
        enabled = false;
    }

    public void enable() {
        enabled = true;
    }
}
