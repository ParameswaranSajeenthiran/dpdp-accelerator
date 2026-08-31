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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.listener.DPDPLifecycleEventListener;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DPDPLifecycleEventUtilTest {

    @Mock
    private DPDPLifecycleEventListener listener;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void notifyInvokesTheActionWhenAListenerIsRegistered() {

        DPDPIdentityExtensionDataHolder.getInstance().setLifecycleEventListener(listener);
        AtomicBoolean invoked = new AtomicBoolean(false);

        DPDPLifecycleEventUtil.notify(l -> invoked.set(true));

        assertTrue(invoked.get());
    }

    @Test
    public void notifyIsANoOpWhenNoListenerIsRegistered() {

        DPDPIdentityExtensionDataHolder.getInstance().setLifecycleEventListener(null);
        AtomicBoolean invoked = new AtomicBoolean(false);

        DPDPLifecycleEventUtil.notify(l -> invoked.set(true));

        assertFalse(invoked.get());
    }

    @Test
    public void notifySwallowsAnExceptionFromTheAction() {

        DPDPIdentityExtensionDataHolder.getInstance().setLifecycleEventListener(listener);

        DPDPLifecycleEventUtil.notify(l -> {
            throw new RuntimeException("boom");
        });
        // No exception propagated out of notify().
    }
}
