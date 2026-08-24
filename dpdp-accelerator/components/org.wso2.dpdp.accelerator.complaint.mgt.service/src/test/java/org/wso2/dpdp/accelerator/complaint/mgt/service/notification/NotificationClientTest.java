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

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link NotificationClient} against a mocked {@link IdentityEventService} - the real
 * lookup goes through {@code PrivilegedCarbonContext.getOSGiService}, which only resolves inside
 * an actual Carbon/OSGi runtime, so the package-private supplier constructor is the test seam.
 */
@ExtendWith(MockitoExtension.class)
class NotificationClientTest {

    private static final String COMPLAINT_NOTIFICATION_EVENT = "DPDP_COMPLAINT_NOTIFICATION_EVENT";

    @Mock
    private IdentityEventService identityEventService;

    private Complaint complaint() {
        return new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH", "CRITICAL", "OPEN",
                "desc", 1L, 2L, 3L);
    }

    @Test
    void notifyComplaintCreatedFiresExpectedEventProperties() throws Exception {
        NotificationClient client = new NotificationClient(() -> identityEventService);

        client.notifyComplaintCreated(complaint());

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        Event event = captor.getValue();
        assertEquals(COMPLAINT_NOTIFICATION_EVENT, event.getEventName());
        Map<String, Object> props = event.getEventProperties();
        assertEquals("ComplaintCreated", props.get("notification-type"));
        assertEquals("org1", props.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));
        assertEquals("c1", props.get("complaint-id"));
        assertEquals("CMP-2026-00001", props.get("reference-id"));
        assertEquals("DATA_BREACH", props.get("category"));
    }

    @Test
    void notifyCommentAddedFiresActorRoleAndExcerpt() throws Exception {
        NotificationClient client = new NotificationClient(() -> identityEventService);
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                true, "hello there", null, null, 100L);

        client.notifyCommentAdded(complaint(), event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        Map<String, Object> props = captor.getValue().getEventProperties();
        assertEquals("ComplaintCommentAdded", props.get("notification-type"));
        assertEquals("COMPLAINT_OFFICER", props.get("actor-role"));
        assertEquals("hello there", props.get("message-excerpt"));
        assertEquals("user1", props.get("creator-user-id"));
        assertEquals("User One", props.get("creator-user-name"));
    }

    @Test
    void truncatesLongMessagesToAnExcerpt() throws Exception {
        NotificationClient client = new NotificationClient(() -> identityEventService);
        String longMessage = "a".repeat(500);
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "user1", "User One", "USER", true, longMessage,
                null, null, 100L);

        client.notifyCommentAdded(complaint(), event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        String excerpt = (String) captor.getValue().getEventProperties().get("message-excerpt");
        assertTrue(excerpt.length() < longMessage.length());
        assertTrue(excerpt.endsWith("..."));
    }

    @Test
    void omitsCreatorUserNamePropertyWhenComplaintHasNone() throws Exception {
        NotificationClient client = new NotificationClient(() -> identityEventService);
        Complaint complaintWithoutUserName = new Complaint("c1", "org1", "user1", null, "CMP-2026-00001",
                "DATA_BREACH", "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
        ComplaintEvent event = new ComplaintEvent("e1", "org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                true, "hi", null, null, 100L);

        client.notifyCommentAdded(complaintWithoutUserName, event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(identityEventService).handleEvent(captor.capture());
        Map<String, Object> props = captor.getValue().getEventProperties();
        assertFalse(props.containsKey("creator-user-name"));
        assertEquals("user1", props.get("creator-user-id"));
    }

    @Test
    void neverThrowsWhenTheEventServiceIsUnresolvable() {
        // Mirrors real behaviour outside a Carbon/OSGi runtime - PrivilegedCarbonContext resolves
        // nothing, so the supplier returns null. A notification failure must never propagate.
        NotificationClient client = new NotificationClient(() -> null);

        client.notifyComplaintCreated(complaint());
    }

    @Test
    void neverThrowsWhenTheEventServiceThrows() throws Exception {
        doThrow(new RuntimeException("boom")).when(identityEventService).handleEvent(any(Event.class));
        NotificationClient client = new NotificationClient(() -> identityEventService);

        client.notifyComplaintCreated(complaint());

        verify(identityEventService).handleEvent(any(Event.class));
    }

    @Test
    void neverThrowsWhenTheSupplierItselfThrows() throws Exception {
        NotificationClient client = new NotificationClient(() -> {
            throw new NoClassDefFoundError("org.wso2.carbon.context.PrivilegedCarbonContext");
        });

        client.notifyComplaintCreated(complaint());

        verify(identityEventService, never()).handleEvent(any(Event.class));
    }
}
