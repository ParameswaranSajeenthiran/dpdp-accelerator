/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Unit tests for {@link WebhookDeliveryTask}. The {@link HttpClient} is mocked; everything
 * else (delivery row, audit row, retry state) is verified through Mockito assertions.
 */
public class WebhookDeliveryTaskTest {

    @Mock
    private DeliveryDAO deliveryDAO;

    @Mock
    private HttpClient httpClient;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private WebhookDelivery delivery(int attemptCount) {
        return new WebhookDelivery(
                "deliv-1",
                "sub-1",
                "event-1",
                "pending",
                attemptCount,
                null,
                new Timestamp(System.currentTimeMillis() - 1000L),
                new Timestamp(System.currentTimeMillis() - 1000L),
                null);
    }

    private WebhookDeliveryTask task(WebhookDelivery delivery) {
        return new WebhookDeliveryTask(
                delivery,
                "org-1",
                "{\"hello\":\"world\"}",
                "https://callback.example.com/hook",
                "secret",
                deliveryDAO,
                httpClient);
    }

    // Raw type avoids HttpResponse<String> vs HttpResponse<Object> witness mismatch.
    private HttpResponse<?> mockResponse(int status) {
        HttpResponse<?> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("ignored");
        return response;
    }

    // doReturn bypasses Mockito's generic type-checking on HttpResponse<String>.
    @SuppressWarnings("unchecked")
    private void stubHttpResponse(int status) throws Exception {
        org.mockito.Mockito.doReturn(mockResponse(status))
                .when(httpClient)
                .send(any(HttpRequest.class), any());
    }

    @SuppressWarnings("unchecked")
    private void stubHttpException(IOException ex) throws Exception {
        org.mockito.Mockito.doThrow(ex)
                .when(httpClient)
                .send(any(HttpRequest.class), any());
    }

    @Test
    public void testSuccessMarksDeliveredAndWritesAudit() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.updateWebhookDeliveryStatus(any())).thenReturn(true);
        when(deliveryDAO.addWebhookDeliveryAudit(any())).thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDelivery> updatedCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).updateWebhookDeliveryStatus(updatedCaptor.capture());
        WebhookDelivery updated = updatedCaptor.getValue();
        assertEquals(updated.getStatus(), "delivered");
        assertEquals(updated.getAttemptCount(), 1);
        assertNotNull(updated.getDeliveredAt());
        assertNull(updated.getNextRetryAt());

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        verify(deliveryDAO).addWebhookDeliveryAudit(auditCaptor.capture());
        WebhookDeliveryAudit audit = auditCaptor.getValue();
        assertEquals(audit.getResponseCode(), "200");
        assertEquals(audit.getDeliveryId(), "deliv-1");

        // On success we never release.
        verify(deliveryDAO, never()).releaseWebhookDelivery(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    public void testRetryableFailureReleasesWithExponentialBackoff() throws Exception {
        // attempt 0 → fails → newAttempt = 1, which is below maxRetries (default 5).
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(500);
        when(deliveryDAO.addWebhookDeliveryAudit(any())).thenReturn(true);
        when(deliveryDAO.releaseWebhookDelivery(anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        verify(deliveryDAO).addWebhookDeliveryAudit(auditCaptor.capture());
        assertEquals(auditCaptor.getValue().getResponseCode(), "500");

        ArgumentCaptor<Integer> attemptCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Timestamp> nextCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(deliveryDAO).releaseWebhookDelivery(eq("deliv-1"), attemptCaptor.capture(), nextCaptor.capture());
        assertEquals(attemptCaptor.getValue().intValue(), 1);

        // 5s * 3^(1-1) = 5s after now; the next retry should be ~5_000ms in the future.
        long delayMs = nextCaptor.getValue().getTime() - System.currentTimeMillis();
        assertEquals(delayMs >= 4_000L && delayMs <= 6_500L, true,
                "expected ~5s backoff, got " + delayMs + "ms");

        // We don't mark as delivered or failed on a retryable failure.
        verify(deliveryDAO, never()).updateWebhookDeliveryStatus(any());
    }

    @Test
    public void testExceptionIsTreatedAsRetryableFailure() throws Exception {
        WebhookDelivery delivery = delivery(2);
        stubHttpException(new IOException("boom"));
        when(deliveryDAO.addWebhookDeliveryAudit(any())).thenReturn(true);
        when(deliveryDAO.releaseWebhookDelivery(anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        verify(deliveryDAO).addWebhookDeliveryAudit(auditCaptor.capture());
        assertEquals(auditCaptor.getValue().getResponseCode(), "EXCEPTION");

        verify(deliveryDAO).releaseWebhookDelivery(eq("deliv-1"), org.mockito.ArgumentMatchers.eq(3), any());
    }

    @Test
    public void testFifthFailedAttemptMarksTerminalFailed() throws Exception {
        // attempt 4 → fails → newAttempt = 5, which equals maxRetries (default) → failed.
        WebhookDelivery delivery = delivery(4);
        stubHttpResponse(500);
        when(deliveryDAO.addWebhookDeliveryAudit(any())).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any())).thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDelivery> updatedCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).updateWebhookDeliveryStatus(updatedCaptor.capture());
        assertEquals(updatedCaptor.getValue().getStatus(), "failed");
        assertEquals(updatedCaptor.getValue().getAttemptCount(), 5);
        assertNull(updatedCaptor.getValue().getNextRetryAt());

        // On terminal failure we never release for a retry.
        verify(deliveryDAO, never()).releaseWebhookDelivery(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());

        // Audit row is still written.
        verify(deliveryDAO, times(1)).addWebhookDeliveryAudit(any());
    }

    @Test
    public void testAuditIdIsUniquePerAttempt() throws Exception {
        // Two failed attempts of two different deliveries should produce two distinct audit IDs.
        WebhookDelivery d1 = delivery(0);
        WebhookDelivery d2 = delivery(0);
        d2.setDeliveryId("deliv-2");

        stubHttpResponse(200);
        when(deliveryDAO.addWebhookDeliveryAudit(any())).thenReturn(true);
        when(deliveryDAO.updateWebhookDeliveryStatus(any())).thenReturn(true);

        task(d1).run();
        task(d2).run();

        ArgumentCaptor<WebhookDeliveryAudit> captor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        verify(deliveryDAO, times(2)).addWebhookDeliveryAudit(captor.capture());
        java.util.List<WebhookDeliveryAudit> rows = captor.getAllValues();
        assertEquals(rows.size(), 2);
        // Different UUIDs for each.
        assertEquals(rows.get(0).getAuditId().equals(rows.get(1).getAuditId()), false,
                "audit IDs must be distinct per attempt");
        UUID.fromString(rows.get(0).getAuditId());
        UUID.fromString(rows.get(1).getAuditId());
    }

    // Quick helper; keeps the test file readable.
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
