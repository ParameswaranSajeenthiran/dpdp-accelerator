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
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class DPDPNotificationServletTest {

    @Mock
    private IdentityEventService identityEventService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private DPDPNotificationServlet servlet;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setIdentityEventService(identityEventService);
        servlet = new DPDPNotificationServlet();
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getParameter(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE))
                .thenReturn(DPDPComplaintEventConstants.NOTIFICATION_TYPE_COMPLAINT_CREATED);
        when(request.getParameter(IdentityEventConstants.EventProperty.TENANT_DOMAIN)).thenReturn("tenant-a.com");
    }

    @Test
    public void rejectsRequestsFromNonLoopbackAddresses() throws Exception {

        when(request.getRemoteAddr()).thenReturn("203.0.113.5");

        servlet.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(identityEventService, never()).handleEvent(any(Event.class));
    }

    @Test
    public void rejectsRequestsMissingNotificationTypeOrTenantDomain() throws Exception {

        when(request.getParameter(DPDPComplaintEventConstants.PROP_NOTIFICATION_TYPE)).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).sendError(org.mockito.ArgumentMatchers.eq(HttpServletResponse.SC_BAD_REQUEST),
                any(String.class));
        verify(identityEventService, never()).handleEvent(any(Event.class));
    }

    @Test
    public void buildsAndFiresTheCustomEventWithOnlyThePresentFields() throws Exception {

        when(request.getParameter(DPDPComplaintEventConstants.PROP_COMPLAINT_ID)).thenReturn("c1");
        when(request.getParameter(DPDPComplaintEventConstants.PROP_REFERENCE_ID)).thenReturn("CMP-2026-00001");
        when(request.getParameter(DPDPComplaintEventConstants.PROP_CATEGORY)).thenReturn("DATA_BREACH");
        when(request.getParameter(DPDPComplaintEventConstants.PROP_ACTOR_ROLE)).thenReturn(null);

        servlet.doPost(request, response);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        Event event = captor.getValue();
        assertEquals(event.getEventName(), DPDPComplaintEventConstants.COMPLAINT_NOTIFICATION_EVENT);
        Map<String, Object> props = event.getEventProperties();
        assertEquals(props.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN), "tenant-a.com");
        assertEquals(props.get(DPDPComplaintEventConstants.PROP_COMPLAINT_ID), "c1");
        assertEquals(props.get(DPDPComplaintEventConstants.PROP_REFERENCE_ID), "CMP-2026-00001");
        assertNull(props.get(DPDPComplaintEventConstants.PROP_ACTOR_ROLE));
        verify(response).setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    @Test
    public void respondsWithServerErrorWhenTheEventServiceThrows() throws Exception {

        doThrow(new IdentityEventException("boom")).when(identityEventService).handleEvent(any(Event.class));

        servlet.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
