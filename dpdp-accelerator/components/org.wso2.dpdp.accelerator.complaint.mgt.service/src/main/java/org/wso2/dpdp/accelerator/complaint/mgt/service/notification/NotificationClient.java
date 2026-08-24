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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.common.config.ConfigProvider;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Notifies the {@code org.wso2.dpdp.accelerator.identity.extensions} OSGi bundle of complaint
 * events, so it can resolve recipients and trigger IS's native email notification mechanism (see
 * that bundle's {@code notification} package). This plain, non-OSGi module has no direct path to
 * {@code IdentityEventService}, so it bridges over a loopback-only internal HTTP call instead -
 * see {@code DPDPNotificationServlet} in that bundle for the receiving end of this contract.
 *
 * <p>Never lets a notification failure propagate to the caller - every public method here is
 * fire-and-forget by design, since a complaint or comment write must succeed independently of
 * whether anyone could be notified about it.
 */
public class NotificationClient {

    private static final Logger LOGGER = Logger.getLogger(NotificationClient.class.getName());

    private static final String CONFIG_INTERNAL_URL = "complaint_mgt.notifications.internal_url";
    private static final String SYS_PROP_INTERNAL_URL = "CO_NOTIFY_INTERNAL_URL";
    private static final String DEFAULT_INTERNAL_URL = "https://localhost:9443/dpdp-internal/notify";

    // Form field names - must match DPDPNotificationServlet's expected parameter names exactly.
    // See org.wso2.dpdp.accelerator.identity.extensions.notification.DPDPComplaintEventConstants,
    // the source of truth for this small wire contract between the two modules.
    private static final String FIELD_NOTIFICATION_TYPE = "notification-type";
    private static final String FIELD_TENANT_DOMAIN = "tenant-domain";
    private static final String FIELD_COMPLAINT_ID = "complaint-id";
    private static final String FIELD_REFERENCE_ID = "reference-id";
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_ACTOR_ROLE = "actor-role";
    private static final String FIELD_MESSAGE_EXCERPT = "message-excerpt";
    private static final String FIELD_CREATOR_USER_ID = "creator-user-id";
    private static final String FIELD_CREATOR_USER_NAME = "creator-user-name";

    private static final String NOTIFICATION_TYPE_COMPLAINT_CREATED = "ComplaintCreated";
    private static final String NOTIFICATION_TYPE_COMMENT_ADDED = "ComplaintCommentAdded";

    private static final int MAX_EXCERPT_LENGTH = 300;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final String internalUrl;
    private final HttpClient httpClient;

    public NotificationClient() {
        this.internalUrl = ConfigProvider.getString(CONFIG_INTERNAL_URL,
                System.getProperty(SYS_PROP_INTERNAL_URL, DEFAULT_INTERNAL_URL));
        this.httpClient = buildHttpClient(internalUrl);
    }

    /** Notifies the complaint officers (dpdp-consent-admin role members) that a complaint was filed. */
    public void notifyComplaintCreated(Complaint complaint) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_NOTIFICATION_TYPE, NOTIFICATION_TYPE_COMPLAINT_CREATED);
        fields.put(FIELD_TENANT_DOMAIN, complaint.getOrgId());
        putIfPresent(fields, FIELD_COMPLAINT_ID, complaint.getComplaintId());
        putIfPresent(fields, FIELD_REFERENCE_ID, complaint.getReferenceId());
        putIfPresent(fields, FIELD_CATEGORY, complaint.getCategory());
        send(fields);
    }

    /**
     * Notifies the other party of a new comment: complaint officers when a citizen comments, or
     * the complaint's original creator when an officer comments (see
     * {@code ComplaintNotificationHandler} for how the actor role decides this).
     */
    public void notifyCommentAdded(Complaint complaint, ComplaintEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_NOTIFICATION_TYPE, NOTIFICATION_TYPE_COMMENT_ADDED);
        fields.put(FIELD_TENANT_DOMAIN, complaint.getOrgId());
        putIfPresent(fields, FIELD_COMPLAINT_ID, complaint.getComplaintId());
        putIfPresent(fields, FIELD_REFERENCE_ID, complaint.getReferenceId());
        putIfPresent(fields, FIELD_CATEGORY, complaint.getCategory());
        putIfPresent(fields, FIELD_ACTOR_ROLE, event.getActorRole());
        putIfPresent(fields, FIELD_MESSAGE_EXCERPT, excerpt(event.getComment()));
        putIfPresent(fields, FIELD_CREATOR_USER_ID, complaint.getUserId());
        putIfPresent(fields, FIELD_CREATOR_USER_NAME, complaint.getUserName());
        send(fields);
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            fields.put(key, value);
        }
    }

    private static String excerpt(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_EXCERPT_LENGTH ? message : message.substring(0, MAX_EXCERPT_LENGTH) + "...";
    }

    private void send(Map<String, String> fields) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(internalUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(fields), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                LOGGER.warning("Complaint notification bridge at " + internalUrl + " returned status "
                        + response.statusCode());
            }
        } catch (Exception e) {
            // Deliberately never rethrown - see class javadoc. The complaint/comment write this
            // is called after has already committed; a notification failure must not surface as
            // one.
            LOGGER.log(Level.WARNING, "Error sending complaint notification to " + internalUrl, e);
        }
    }

    private static String encode(Map<String, String> fields) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    /**
     * Builds the HTTP client used for the internal bridge call. When the configured URL's host is
     * loopback (the only case this bridge is designed for - both sides run in the same JVM/Tomcat
     * instance), certificate validation is relaxed, since IS's own management-port certificate is
     * commonly self-signed and there is no practical way for this module to be handed IS's actual
     * keystore. This relaxation is scoped to loopback hosts only and to this one client instance -
     * it is never installed as a JVM-wide default - so a misconfigured, non-loopback
     * {@code internal_url} still gets full certificate validation rather than silently trusting
     * anything.
     */
    private static HttpClient buildHttpClient(String url) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT);
        if (isLoopbackUrl(url)) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{TRUST_ALL_LOOPBACK_ONLY}, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not relax TLS trust for loopback notification bridge; "
                        + "falling back to default certificate validation.", e);
            }
        }
        return builder.build();
    }

    private static boolean isLoopbackUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static final X509TrustManager TRUST_ALL_LOOPBACK_ONLY = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
