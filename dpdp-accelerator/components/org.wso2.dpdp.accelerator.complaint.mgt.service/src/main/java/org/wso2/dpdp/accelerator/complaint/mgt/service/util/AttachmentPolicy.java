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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import java.util.Set;

/**
 * Matches the encoding.file.contentType list declared in the OpenAPI spec for both attachment
 * upload endpoints. Max size is configurable via deployment.toml's
 * [dpdp_accelerator.complaints] attachment_max_size_bytes key, the same way
 * StatutoryDuePeriodPolicy reads its own setting - see the DPDPConfigurationService OSGi
 * reference bound into {@link ComplaintServiceDataHolder} by {@code ComplaintServiceComponent},
 * templated into dpdp-accelerator.xml at server startup. Defaults to 10 MB if unset.
 */
public class AttachmentPolicy {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of( // default fallabck value
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg");

    private AttachmentPolicy() {
    }

    public static long getMaxSizeBytes() {
        return ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .getComplaintsAttachmentMaxSizeBytes();
    }

    public static int getMaxFilesPerUpload() {
        return ComplaintServiceDataHolder.getInstance().getConfigurationService()
                .getComplaintsAttachmentMaxFilesPerUpload();
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.trim());
    }
}
