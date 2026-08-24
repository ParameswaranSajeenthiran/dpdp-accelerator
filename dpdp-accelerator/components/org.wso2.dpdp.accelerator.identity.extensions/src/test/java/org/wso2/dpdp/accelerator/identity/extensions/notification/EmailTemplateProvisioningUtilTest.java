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

package org.wso2.dpdp.accelerator.identity.extensions.notification;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.governance.exceptions.notiification.NotificationTemplateManagerException;
import org.wso2.carbon.identity.governance.model.NotificationTemplate;
import org.wso2.carbon.identity.governance.service.notification.NotificationTemplateManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class EmailTemplateProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String EMAIL_CHANNEL = "EMAIL";
    private static final String DEFAULT_LOCALE = "en_US";

    @Mock
    private NotificationTemplateManager notificationTemplateManager;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setNotificationTemplateManager(notificationTemplateManager);
    }

    @Test
    public void provisionTemplatesCreatesBothTemplatesWhenNeitherExists() throws Exception {

        when(notificationTemplateManager.getNotificationTemplate(eq(EMAIL_CHANNEL), anyString(), eq(DEFAULT_LOCALE),
                eq(TENANT_DOMAIN))).thenReturn(null);

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager).addNotificationTemplateType(
                eq(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));
        verify(notificationTemplateManager).addNotificationTemplateType(
                eq(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));

        ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(notificationTemplateManager, times(2)).addNotificationTemplate(captor.capture(), eq(TENANT_DOMAIN));
        List<String> types = captor.getAllValues().stream().map(NotificationTemplate::getType).toList();
        assertTrue(types.contains(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED));
        assertTrue(types.contains(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED));
        for (NotificationTemplate template : captor.getAllValues()) {
            assertEquals(template.getNotificationChannel(), EMAIL_CHANNEL);
            assertEquals(template.getLocale(), DEFAULT_LOCALE);
        }
    }

    @Test
    public void provisionTemplatesSkipsATemplateThatAlreadyExists() throws Exception {

        NotificationTemplate existing = new NotificationTemplate();
        existing.setType(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED);
        when(notificationTemplateManager.getNotificationTemplate(EMAIL_CHANNEL,
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED, DEFAULT_LOCALE, TENANT_DOMAIN))
                .thenReturn(existing);
        when(notificationTemplateManager.getNotificationTemplate(EMAIL_CHANNEL,
                DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED, DEFAULT_LOCALE, TENANT_DOMAIN))
                .thenReturn(null);

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager, never()).addNotificationTemplateType(
                eq(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED), anyString(), anyString());
        verify(notificationTemplateManager).addNotificationTemplateType(
                eq(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMMENT_ADDED), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void provisionTemplatesStillAttemptsCreationWhenLookupThrows() throws Exception {

        when(notificationTemplateManager.getNotificationTemplate(eq(EMAIL_CHANNEL), anyString(), eq(DEFAULT_LOCALE),
                eq(TENANT_DOMAIN))).thenThrow(new NotificationTemplateManagerException("not found"));

        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);

        verify(notificationTemplateManager, times(2)).addNotificationTemplateType(anyString(), eq(EMAIL_CHANNEL),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void provisionTemplatesDoesNotThrowWhenAddingFails() throws Exception {

        when(notificationTemplateManager.getNotificationTemplate(eq(EMAIL_CHANNEL), anyString(), eq(DEFAULT_LOCALE),
                eq(TENANT_DOMAIN))).thenReturn(null);
        org.mockito.Mockito.doThrow(new NotificationTemplateManagerException("boom"))
                .when(notificationTemplateManager).addNotificationTemplateType(anyString(), anyString(), anyString());

        // Must not throw - a provisioning failure for one tenant shouldn't break the caller.
        EmailTemplateProvisioningUtil.provisionTemplates(TENANT_DOMAIN);
    }
}
