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

package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

import java.util.List;

public class TimelineListResponseDTO {

    private List<ComplaintTimelineEntryResponseDTO> data;
    private PageMetadataDTO metadata;

    public TimelineListResponseDTO() {
    }

    public TimelineListResponseDTO(List<ComplaintTimelineEntryResponseDTO> data, PageMetadataDTO metadata) {
        this.data = data;
        this.metadata = metadata;
    }

    public List<ComplaintTimelineEntryResponseDTO> getData() {
        return data;
    }

    public void setData(List<ComplaintTimelineEntryResponseDTO> data) {
        this.data = data;
    }

    public PageMetadataDTO getMetadata() {
        return metadata;
    }

    public void setMetadata(PageMetadataDTO metadata) {
        this.metadata = metadata;
    }
}
