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
import { useEffect, useState } from 'react'
import type { SubscriptionRecord } from '../../../types/subscription'
import { fetchSubscriptionById } from '../api/subscriptionsApi'
import { WEBHOOK_VERIFICATION_MONITOR_POLICY } from '../constants'

export type WebhookVerificationMonitorResult = UseQueryResult<SubscriptionRecord> & {
  normalizedStatus: string
  isMonitoring: boolean
  timedOut: boolean
}

interface VerificationTimeoutState {
  subscriptionId?: string
  timedOut: boolean
}

export function normalizeSubscriptionStatus(status?: string): string {
  return status?.trim().toUpperCase() ?? ''
}

export function useWebhookVerificationMonitor(
  subscriptionId?: string,
): WebhookVerificationMonitorResult {
  const [timeoutState, setTimeoutState] = useState<VerificationTimeoutState>({ timedOut: false })
  const timedOut = timeoutState.subscriptionId === subscriptionId && timeoutState.timedOut

  useEffect(() => {
    if (subscriptionId) return undefined

    const resetTimeout = window.setTimeout(() => {
      setTimeoutState({ timedOut: false })
    }, 0)

    return () => window.clearTimeout(resetTimeout)
  }, [subscriptionId])

  const query = useQuery({
    queryKey: ['subscription-verification-monitor', subscriptionId],
    queryFn: () => {
      if (!subscriptionId) {
        throw new Error('Subscription ID is required')
      }
      return fetchSubscriptionById(subscriptionId)
    },
    enabled: Boolean(subscriptionId) && !timedOut,
    retry: false,
    refetchInterval: (currentQuery) => {
      const status = normalizeSubscriptionStatus(currentQuery.state.data?.status)
      return !status || status === 'PENDING'
        ? WEBHOOK_VERIFICATION_MONITOR_POLICY.pollIntervalMs
        : false
    },
  })

  const normalizedStatus = normalizeSubscriptionStatus(query.data?.status)
  const isPending = !normalizedStatus || normalizedStatus === 'PENDING'

  useEffect(() => {
    if (!subscriptionId || !isPending || timedOut) return undefined

    const timeout = window.setTimeout(() => {
      setTimeoutState({ subscriptionId, timedOut: true })
    }, WEBHOOK_VERIFICATION_MONITOR_POLICY.observationTimeoutMs)

    return () => window.clearTimeout(timeout)
  }, [isPending, subscriptionId, timedOut])

  return {
    ...query,
    normalizedStatus,
    isMonitoring: Boolean(subscriptionId) && isPending && !timedOut,
    timedOut,
  }
}
