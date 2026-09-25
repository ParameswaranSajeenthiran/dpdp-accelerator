/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { type UseQueryResult, useQuery } from '@tanstack/react-query'
import { fetchMyComplaintsTotal } from '../../complaints/api/complaintsApi'

export interface ComplaintStateCounts {
  open: number
  inProgress: number
  waitingOnClient: number
  waitingOnInternalReview: number
  resolved: number
}

/**
 * Unlike the consent counts elsewhere on this page, these are exact, not "100+" - the
 * complaints backend's total is a genuine COUNT(*) independent of the page requested (verified
 * against ComplaintDAOImpl's paired count/select queries), so a single limit=1 call per status
 * is both cheap and exact.
 */
async function fetchMyComplaintStateCounts(): Promise<ComplaintStateCounts> {
  const [open, inProgress, waitingOnClient, waitingOnInternalReview, resolved] = await Promise.all([
    fetchMyComplaintsTotal('OPEN'),
    fetchMyComplaintsTotal('IN_PROGRESS'),
    fetchMyComplaintsTotal('WAITING_ON_CLIENT'),
    fetchMyComplaintsTotal('AWAITING_INTERNAL_REVIEW'),
    fetchMyComplaintsTotal('RESOLVED'),
  ])
  return { open, inProgress, waitingOnClient, waitingOnInternalReview, resolved }
}

export default function useDashboardMyComplaintCountsQuery(
  enabled: boolean,
): UseQueryResult<ComplaintStateCounts> {
  return useQuery({
    queryKey: ['complaints', 'dashboard', 'self', 'state-counts'],
    queryFn: fetchMyComplaintStateCounts,
    enabled,
  })
}
