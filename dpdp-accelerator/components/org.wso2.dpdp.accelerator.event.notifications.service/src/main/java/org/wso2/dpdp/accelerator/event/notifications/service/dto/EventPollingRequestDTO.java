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

package org.wso2.dpdp.accelerator.event.notifications.service.dto;

import java.util.List;
import java.util.Map;

/** Request body for short polling and acknowledgement synchronization. */
public class EventPollingRequestDTO {

    private List<String> ack;
    private String orgId;
    private Map<String, PollSetErrorDTO> setErrs;
    private Integer maxEvents;
    private Boolean returnImmediately;

    public String getOrgId() {

        return orgId;
    }

    public void setOrgId(String orgId) {

        this.orgId = orgId;
    }

    public List<String> getAck() {

        return ack;
    }

    public void setAck(List<String> ack) {

        this.ack = ack;
    }

    public Map<String, PollSetErrorDTO> getSetErrs() {

        return setErrs;
    }

    public void setSetErrs(Map<String, PollSetErrorDTO> setErrs) {

        this.setErrs = setErrs;
    }

    public Integer getMaxEvents() {

        return maxEvents;
    }

    public void setMaxEvents(Integer maxEvents) {

        this.maxEvents = maxEvents;
    }

    public Boolean getReturnImmediately() {

        return returnImmediately;
    }

    public void setReturnImmediately(Boolean returnImmediately) {

        this.returnImmediately = returnImmediately;
    }
}
