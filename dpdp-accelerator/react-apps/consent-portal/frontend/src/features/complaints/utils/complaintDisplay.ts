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

import type {
  ComplaintActorRole,
  ComplaintPriorityAPI,
  ComplaintSlaState,
  ComplaintStatus,
} from '../../../types/complaint'

type ChipColor = 'success' | 'warning' | 'error' | 'info' | 'default'

export function getComplaintPriorityChipColor(priority: ComplaintPriorityAPI): ChipColor {
  switch (priority) {
    case 'CRITICAL':
      return 'error'
    case 'HIGH':
      return 'warning'
    case 'MEDIUM':
      return 'info'
    case 'LOW':
    default:
      return 'default'
  }
}

export function getComplaintStatusChipColor(
  status: ComplaintStatus,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): ChipColor {
  switch (status) {
    case 'OPEN':
      return 'info'
    case 'IN_PROGRESS':
      return 'warning'
    case 'AWAITING_INTERNAL_REVIEW':
      return viewerRole === 'DataPrincipal' ? 'default' : 'error'
    case 'WAITING_ON_CLIENT':
      return viewerRole === 'DataPrincipal' ? 'error' : 'default'
    case 'RESOLVED':
      return 'success'
    default:
      return 'default'
  }
}

const STATUS_LABEL_KEYS: Record<ComplaintStatus, string> = {
  OPEN: 'open',
  IN_PROGRESS: 'investigation',
  WAITING_ON_CLIENT: 'awaitingInfo',
  AWAITING_INTERNAL_REVIEW: 'waitingOnDpo',
  RESOLVED: 'resolved',
}

export function getComplaintStatusLabelKey(status: ComplaintStatus): string {
  // Falls back to a generic key for a status this closed map doesn't recognize, rather than
  // returning undefined and having every caller interpolate "complaints.status.undefined" into a
  // translation lookup.
  return STATUS_LABEL_KEYS[status] ?? 'unknown'
}

const CHIP_COLOR_TO_SX_PATH: Record<ChipColor, string> = {
  success: 'success.main',
  warning: 'warning.main',
  error: 'error.main',
  info: 'info.main',
  default: 'text.disabled',
}

export function getComplaintStatusAccentColor(
  status: ComplaintStatus,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): string {
  return CHIP_COLOR_TO_SX_PATH[getComplaintStatusChipColor(status, viewerRole)]
}

// The deadline is an exact moment (submission + N x 24h), not the end of a day, so the time is
// shown too - otherwise the badge can turn "Overdue" while the page still shows today's date.
export const SLA_DEADLINE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
}

const SLA_AT_RISK_THRESHOLD_HOURS = 24 * 14
const DAY_IN_MS = 1000 * 60 * 60 * 24

export function getComplaintSlaState(
  statutoryDueDate: number,
  status: ComplaintStatus,
  now: number = Date.now(),
): ComplaintSlaState {
  if (status === 'RESOLVED') {
    return 'met'
  }

  const hoursRemaining = (statutoryDueDate - now) / (1000 * 60 * 60)

  if (hoursRemaining < 0) {
    return 'breached'
  }

  if (hoursRemaining <= SLA_AT_RISK_THRESHOLD_HOURS) {
    return 'atRisk'
  }

  return 'onTrack'
}

// Derived per complaint rather than read from config, so it stays right for complaints filed
// before an operator changed statutory_due_period_days.
export function getComplaintStatutoryPeriodDays(
  submittedAt: number,
  statutoryDueDate: number,
): number {
  return Math.round((statutoryDueDate - submittedAt) / DAY_IN_MS)
}

// Counted in local calendar days so the wording always agrees with the due date printed beside
// it, which a raw 24-hour division does not (3 hours before a midnight deadline is "today").
function getCalendarDaysBetween(from: number, to: number): number {
  const fromDay = new Date(from).setHours(0, 0, 0, 0)
  const toDay = new Date(to).setHours(0, 0, 0, 0)

  // Rounded because a DST change makes one calendar day 23 or 25 hours long.
  return Math.round((toDay - fromDay) / DAY_IN_MS)
}

export interface ComplaintSlaSummary {
  state: ComplaintSlaState
  labelKey: string
  count: number
}

// The single source for the SLA colour and its wording, so the two can never disagree (a red
// "breached" state beside "Due today").
export function getComplaintSlaSummary(
  statutoryDueDate: number,
  status: ComplaintStatus,
  now: number = Date.now(),
): ComplaintSlaSummary {
  const state = getComplaintSlaState(statutoryDueDate, status, now)

  if (state === 'met') {
    return { state, labelKey: `complaints.status.${getComplaintStatusLabelKey(status)}`, count: 0 }
  }

  if (state === 'breached') {
    const overdueDays = getCalendarDaysBetween(statutoryDueDate, now)

    return overdueDays === 0
      ? { state, labelKey: 'complaints.sla.overdue', count: 0 }
      : { state, labelKey: 'complaints.sla.overdueDays', count: overdueDays }
  }

  const daysLeft = getCalendarDaysBetween(now, statutoryDueDate)

  return daysLeft === 0
    ? { state, labelKey: 'complaints.sla.dueToday', count: 0 }
    : { state, labelKey: 'complaints.sla.daysLeft', count: daysLeft }
}
