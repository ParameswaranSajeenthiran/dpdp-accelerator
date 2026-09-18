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
import { listMyComplaints } from '../../complaints/api/complaintsApi'
import type { ComplaintRecordAPI } from '../../../types/complaint'

const DASHBOARD_PAGE_SIZE = 100
const DASHBOARD_MAX_PAGES = 20

/**
 * Walks the self-service complaint pages until a short page arrives. There is
 * no `/me/complaints/stats` endpoint, so counts are aggregated here the same
 * way useDashboardConsentsQuery aggregates personal consents client-side.
 */
async function fetchComplaintPage(
  offset: number,
  collected: ComplaintRecordAPI[],
  remainingPages: number,
): Promise<ComplaintRecordAPI[]> {
  const response = await listMyComplaints({ limit: DASHBOARD_PAGE_SIZE, offset })
  const complaints = [...collected, ...response.data]

  if (response.data.length < DASHBOARD_PAGE_SIZE || remainingPages <= 1) {
    return complaints
  }

  return fetchComplaintPage(offset + response.data.length, complaints, remainingPages - 1)
}

async function fetchAllMyComplaints(): Promise<ComplaintRecordAPI[]> {
  return fetchComplaintPage(0, [], DASHBOARD_MAX_PAGES)
}

export default function useDashboardMyComplaintsQuery(
  enabled: boolean,
): UseQueryResult<ComplaintRecordAPI[]> {
  return useQuery({
    queryKey: ['complaints', 'dashboard', 'self'],
    queryFn: fetchAllMyComplaints,
    enabled,
  })
}
