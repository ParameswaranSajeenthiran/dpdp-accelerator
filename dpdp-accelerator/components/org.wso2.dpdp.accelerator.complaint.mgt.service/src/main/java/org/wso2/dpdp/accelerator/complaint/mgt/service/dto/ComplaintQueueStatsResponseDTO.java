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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;

/** Response body for GET /complaints/stats - see ComplaintQueuePage's summary tiles. */
public class ComplaintQueueStatsResponseDTO {

    private int openCount;
    private int awaitingInternalReviewCount;
    private int resolvedCount;
    private int slaBreachedCount;

    public ComplaintQueueStatsResponseDTO() {
    }

    public static ComplaintQueueStatsResponseDTO from(ComplaintQueueStats stats) {
        ComplaintQueueStatsResponseDTO bean = new ComplaintQueueStatsResponseDTO();
        bean.openCount = stats.getOpenCount();
        bean.awaitingInternalReviewCount = stats.getAwaitingInternalReviewCount();
        bean.resolvedCount = stats.getResolvedCount();
        bean.slaBreachedCount = stats.getSlaBreachedCount();
        return bean;
    }

    public int getOpenCount() {
        return openCount;
    }

    public void setOpenCount(int openCount) {
        this.openCount = openCount;
    }

    public int getAwaitingInternalReviewCount() {
        return awaitingInternalReviewCount;
    }

    public void setAwaitingInternalReviewCount(int awaitingInternalReviewCount) {
        this.awaitingInternalReviewCount = awaitingInternalReviewCount;
    }

    public int getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(int resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public int getSlaBreachedCount() {
        return slaBreachedCount;
    }

    public void setSlaBreachedCount(int slaBreachedCount) {
        this.slaBreachedCount = slaBreachedCount;
    }
}
