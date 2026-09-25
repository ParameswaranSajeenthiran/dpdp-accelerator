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

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getComplaintSlaState,
  getComplaintSlaSummary,
} from '../features/complaints/utils/complaintDisplay'

const commonEn: { complaints: Record<string, Record<string, string>> } = JSON.parse(
  readFileSync(resolve(import.meta.dirname, '../../public/i18n/en/common.json'), 'utf8'),
)

// Local-time construction so calendar-day expectations hold in any runner time zone.
const NOW = new Date(2026, 2, 9, 12, 0).getTime()
const HOUR_IN_MS = 60 * 60 * 1000

function localTime(day: number, hour: number, minute = 0): number {
  return new Date(2026, 2, day, hour, minute).getTime()
}

describe('Complaint SLA summary', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('derives the SLA state from the due date and status', () => {
    expect(getComplaintSlaState(localTime(29, 12), 'OPEN')).toBe('onTrack')
    expect(getComplaintSlaState(localTime(12, 12), 'OPEN')).toBe('atRisk')
    expect(getComplaintSlaState(localTime(8, 12), 'OPEN')).toBe('breached')
    expect(getComplaintSlaState(localTime(8, 12), 'RESOLVED')).toBe('met')
  })

  it('counts calendar days, not 24-hour blocks', () => {
    expect(getComplaintSlaSummary(localTime(12, 9), 'OPEN')).toEqual({
      state: 'atRisk',
      labelKey: 'complaints.sla.daysLeft',
      count: 3,
    })
    // Only 13 hours away, but tomorrow on the calendar.
    expect(getComplaintSlaSummary(localTime(10, 1), 'OPEN')).toMatchObject({
      labelKey: 'complaints.sla.daysLeft',
      count: 1,
    })
    expect(getComplaintSlaSummary(localTime(9, 23, 59), 'OPEN')).toMatchObject({
      labelKey: 'complaints.sla.dueToday',
    })
  })

  it('never pairs a breached state with "Due today"', () => {
    expect(getComplaintSlaSummary(NOW - 3 * HOUR_IN_MS, 'OPEN')).toEqual({
      state: 'breached',
      labelKey: 'complaints.sla.overdue',
      count: 0,
    })
    expect(getComplaintSlaSummary(localTime(8, 23), 'IN_PROGRESS')).toEqual({
      state: 'breached',
      labelKey: 'complaints.sla.overdueDays',
      count: 1,
    })
    expect(getComplaintSlaSummary(localTime(4, 12), 'OPEN')).toMatchObject({
      labelKey: 'complaints.sla.overdueDays',
      count: 5,
    })
  })

  it('reports a resolved complaint as met regardless of the deadline', () => {
    expect(getComplaintSlaSummary(localTime(4, 12), 'RESOLVED')).toEqual({
      state: 'met',
      labelKey: 'complaints.status.resolved',
      count: 0,
    })
  })

  it('only returns label keys that exist in the English catalog', () => {
    const dueDates = [localTime(12, 9), localTime(9, 23), NOW - HOUR_IN_MS, localTime(4, 12)]
    const labelKeys = dueDates.map((dueDate) => getComplaintSlaSummary(dueDate, 'OPEN').labelKey)

    labelKeys.forEach((labelKey) => {
      const [, group, key] = labelKey.split('.')
      const catalogGroup = commonEn.complaints[group]

      expect(catalogGroup[key] ?? catalogGroup[`${key}_other`]).toBeTruthy()
    })
  })
})
