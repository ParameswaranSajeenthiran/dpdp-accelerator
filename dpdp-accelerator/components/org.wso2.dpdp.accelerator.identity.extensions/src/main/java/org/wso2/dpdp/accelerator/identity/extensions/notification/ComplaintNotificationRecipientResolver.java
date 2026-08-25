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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.UserBasicInfo;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.DPDPConsentPortalAppProvisioningUtil;
import org.wso2.dpdp.accelerator.identity.extensions.tenant.DPDPConsentPortalRoleProvisioningUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves email recipients for complaint notifications - either every member of the
 * {@code dpdp-consent-admin} role for a tenant (the complaint officers), or a single named user
 * (a complaint's original creator). Mirrors financial-services-accelerator's
 * {@code SMSNotificationProvider} claim-lookup pattern, just resolving the email claim instead of
 * the mobile claim, plus the role-membership lookup already used for provisioning in
 * {@link DPDPConsentPortalRoleProvisioningUtil}.
 */
public final class ComplaintNotificationRecipientResolver {

    private static final Log LOG = LogFactory.getLog(ComplaintNotificationRecipientResolver.class);
    private static final String EMAIL_CLAIM = "http://wso2.org/claims/emailaddress";
    private static final String GIVEN_NAME_CLAIM = "http://wso2.org/claims/givenname";
    private static final String LAST_NAME_CLAIM = "http://wso2.org/claims/lastname";
    private static final String ROLE_AUDIENCE = "application";

    private ComplaintNotificationRecipientResolver() {

    }

    /**
     * A resolved notification recipient - a username paired with the email address to send to.
     */
    public static final class Recipient {

        private final String username;
        private final String email;

        public Recipient(String username, String email) {
            this.username = username;
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }
    }

    /**
     * Resolves every member of {@code dpdp-consent-admin} for the given tenant that has a
     * resolvable email address. Members without one are skipped (logged), not fatal to the batch.
     */
    public static List<Recipient> resolveOfficers(String tenantDomain) {

        List<Recipient> recipients = new ArrayList<>();
        try {
            String applicationId = DPDPConsentPortalAppProvisioningUtil.getApplicationId(tenantDomain);
            if (applicationId == null) {
                LOG.warn("No DPDP Consent Portal application found for tenant '" + tenantDomain
                        + "'; cannot resolve complaint officers to notify.");
                return recipients;
            }

            RoleManagementService roleManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                    .getRoleManagementService();
            if (!roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                    ROLE_AUDIENCE, applicationId, tenantDomain)) {
                LOG.warn("Role '" + DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE + "' does not exist for "
                        + "tenant '" + tenantDomain + "'; cannot resolve complaint officers to notify.");
                return recipients;
            }

            String roleId = roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                    ROLE_AUDIENCE, applicationId, tenantDomain);
            List<UserBasicInfo> members = roleManagementService.getUserListOfRole(roleId, tenantDomain);
            for (UserBasicInfo member : members) {
                resolveEmail(member.getName(), tenantDomain)
                        .ifPresent(email -> recipients.add(new Recipient(member.getName(), email)));
            }
        } catch (Exception e) {
            LOG.error("Error resolving complaint officers to notify for tenant: " + tenantDomain, e);
        }
        return recipients;
    }

    /**
     * Resolves a complaint's original creator - used to notify them when an officer comments.
     * Prefers the given username; a citizen self-service complaint can have a {@code null}
     * username (see {@code Complaint#getUserName()}, only populated when the portal app's OIDC
     * client is configured with a username access-token attribute), so this falls back to
     * resolving the username from the stored user id first.
     */
    public static Optional<Recipient> resolveCreator(String userId, String userName, String tenantDomain) {

        Optional<String> resolvedUsername = resolveUsername(userId, userName, tenantDomain);
        if (!resolvedUsername.isPresent()) {
            LOG.warn("Could not resolve a username for complaint creator (userId: " + userId + ") in tenant '"
                    + tenantDomain + "'; cannot notify them.");
            return Optional.empty();
        }
        String username = resolvedUsername.get();
        return resolveEmail(username, tenantDomain).map(email -> new Recipient(username, email));
    }

    /**
     * Resolves a username given either a username or a user id - preferring the username when
     * present (a citizen self-service complaint can have a {@code null} username; see
     * {@code Complaint#getUserName()}, only populated when the portal app's OIDC client is
     * configured with a username access-token attribute), falling back to resolving it from the
     * stored user id otherwise. Used both to resolve a notification recipient and, separately, to
     * resolve a display name for a template placeholder for the same person.
     */
    public static Optional<String> resolveUsername(String userId, String userName, String tenantDomain) {

        if (userName != null && !userName.trim().isEmpty()) {
            return Optional.of(userName.trim());
        }
        return resolveUsernameById(userId, tenantDomain);
    }

    private static Optional<String> resolveUsernameById(String userId, String tenantDomain) {

        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
            UserRealm userRealm = DPDPIdentityExtensionDataHolder.getInstance().getRealmService()
                    .getTenantUserRealm(tenantId);
            if (userRealm == null) {
                return Optional.empty();
            }
            UserStoreManager userStoreManager = userRealm.getUserStoreManager();
            if (!(userStoreManager instanceof AbstractUserStoreManager)) {
                return Optional.empty();
            }
            String username = ((AbstractUserStoreManager) userStoreManager).getUserNameFromUserID(userId.trim());
            return (username == null || username.trim().isEmpty()) ? Optional.empty() : Optional.of(username.trim());
        } catch (Exception e) {
            LOG.error("Error resolving username for user id '" + userId + "' in tenant: " + tenantDomain, e);
            return Optional.empty();
        }
    }

    /**
     * Resolves a display name ("Given Family") for a template placeholder (e.g. the "Data
     * Principal" or "replied" actor name shown in a notification email). Falls back to the
     * username itself when the given-name/last-name claims aren't populated, so a missing profile
     * field never blocks the notification the way a missing email claim does.
     */
    public static String resolveDisplayName(String username, String tenantDomain) {

        try {
            int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
            UserRealm userRealm = DPDPIdentityExtensionDataHolder.getInstance().getRealmService()
                    .getTenantUserRealm(tenantId);
            if (userRealm == null) {
                return username;
            }
            UserStoreManager userStoreManager = userRealm.getUserStoreManager();
            if (!(userStoreManager instanceof AbstractUserStoreManager)) {
                return username;
            }
            AbstractUserStoreManager abstractUserStoreManager = (AbstractUserStoreManager) userStoreManager;
            String givenName = abstractUserStoreManager.getUserClaimValue(username, GIVEN_NAME_CLAIM, null);
            String lastName = abstractUserStoreManager.getUserClaimValue(username, LAST_NAME_CLAIM, null);
            String displayName = ((givenName == null ? "" : givenName.trim()) + " "
                    + (lastName == null ? "" : lastName.trim())).trim();
            return displayName.isEmpty() ? username : displayName;
        } catch (Exception e) {
            LOG.warn("Error resolving display name for user '" + username + "' in tenant: " + tenantDomain
                    + "; falling back to the username.", e);
            return username;
        }
    }

    private static Optional<String> resolveEmail(String username, String tenantDomain) {

        try {
            int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
            UserRealm userRealm = DPDPIdentityExtensionDataHolder.getInstance().getRealmService()
                    .getTenantUserRealm(tenantId);
            if (userRealm == null) {
                return Optional.empty();
            }
            UserStoreManager userStoreManager = userRealm.getUserStoreManager();
            if (!(userStoreManager instanceof AbstractUserStoreManager)) {
                return Optional.empty();
            }
            String email = ((AbstractUserStoreManager) userStoreManager)
                    .getUserClaimValue(username, EMAIL_CLAIM, null);
            if (email == null || email.trim().isEmpty()) {
                LOG.warn("No email claim resolvable for user '" + username + "' in tenant: " + tenantDomain);
                return Optional.empty();
            }
            return Optional.of(email.trim());
        } catch (Exception e) {
            LOG.error("Error resolving email claim for user '" + username + "' in tenant: " + tenantDomain, e);
            return Optional.empty();
        }
    }
}
