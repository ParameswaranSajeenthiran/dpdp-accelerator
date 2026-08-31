/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.identity.extensions.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.function.Consumer;

/**
 * Shared by every {@code DPDPLifecycleEventListener} call site in this module. A failure here
 * must never block the caller's own mutation, so it's swallowed and logged, not propagated.
 */
public final class DPDPLifecycleEventUtil {

    private static final Log LOG = LogFactory.getLog(DPDPLifecycleEventUtil.class);

    private DPDPLifecycleEventUtil() {

    }

    public static void notify(Consumer<DPDPLifecycleEventListener> action) {

        DPDPLifecycleEventListener listener = DPDPIdentityExtensionDataHolder.getInstance()
                .getLifecycleEventListener();
        if (listener == null) {
            return;
        }
        try {
            action.accept(listener);
        } catch (Exception e) {
            LOG.error("Error notifying the DPDP lifecycle event listener", e);
        }
    }
}
