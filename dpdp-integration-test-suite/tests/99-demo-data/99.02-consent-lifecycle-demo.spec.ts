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

import type { Page } from '@playwright/test'
import { test, expect } from '../../fixtures/auth.fixtures'
import type { ConsentApiClient } from '../../clients/ConsentApiClient'
import { ConsentDetailPage } from '../../pages/ConsentDetailPage'
import { ConsentRegistryPage } from '../../pages/ConsentRegistryPage'
import { ElementFormDialog } from '../../pages/ElementFormDialog'
import { ElementListPage } from '../../pages/ElementListPage'
import { PurposeFormDialog } from '../../pages/PurposeFormDialog'
import { PurposeListPage } from '../../pages/PurposeListPage'
import { env } from '../../utils/env'

interface CatalogElement {
  id: string
  name: string
  displayName?: string
}

interface CatalogPurpose {
  id: string
  name: string
}

/** Reads the id out of a `.../<segment>/<id>` detail URL, e.g. after a create-form redirect. */
function idFromDetailUrl(url: string, segment: 'elements' | 'purposes'): string {
  const match = new RegExp(`/${segment}/([^/]+)$`).exec(url)
  if (!match) {
    throw new Error(`Could not read an id out of the ${segment} detail URL: ${url}`)
  }
  return match[1]
}

/**
 * Looks up an element by its (unique) name first and only creates it through the "Add Element"
 * UI form if missing, so this demo dataset stays exactly three elements no matter how many
 * times the flow is rehearsed, rather than accumulating a fresh "Full Name", "Full Name", "Full
 * Name"... on every run.
 */
async function getOrCreateElement(
  page: Page,
  api: ConsentApiClient,
  body: { name: string; displayName?: string; description?: string },
): Promise<CatalogElement> {
  const existing = await api.findElementByName(body.name)
  if (existing.status() === 200) {
    const { Elements } = (await existing.json()) as { Elements: CatalogElement[] }
    if (Elements[0]) {
      return Elements[0]
    }
  }
  const listPage = new ElementListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new ElementFormDialog(page)
  await dialog.fill(body)
  await dialog.submit()
  await expect(page).toHaveURL(/\/elements\/[^/]+$/)
  return { id: idFromDetailUrl(page.url(), 'elements'), name: body.name, displayName: body.displayName }
}

/**
 * Same idempotency reasoning as getOrCreateElement. Created with no elements attached through
 * the picker - the consent-mgt v2 API records whichever elements a Consent's own
 * `purposes[].elements[]` lists independently of what the Purpose definition itself requires,
 * so there's no need to fight the Purpose form's element picker (see PurposeFormDialog.addElements's
 * own comments on its quirks) just to satisfy this Purpose's own definition.
 */
async function getOrCreatePurpose(
  page: Page,
  api: ConsentApiClient,
  body: { name: string; type: string; version: string; description?: string },
): Promise<CatalogPurpose> {
  const existing = await api.findPurposeByName(body.name)
  if (existing.status() === 200) {
    const { Purposes } = (await existing.json()) as { Purposes: CatalogPurpose[] }
    if (Purposes[0]) {
      return Purposes[0]
    }
  }
  const listPage = new PurposeListPage(page)
  await listPage.goto()
  await listPage.openCreateDialog()
  const dialog = new PurposeFormDialog(page)
  await dialog.fill(body)
  await dialog.submit()
  await expect(page).toHaveURL(/\/purposes\/[^/]+$/)
  return { id: idFromDetailUrl(page.url(), 'purposes'), name: body.name }
}

/**
 * One full, realistic user journey rather than an isolated unit of behavior: a business defines
 * the personal data it needs (Elements) and why it needs it (a Purpose) through the real admin
 * UI forms, records a customer's consent for it (Consent - the one thing here with no create UI
 * at all, so it goes through the admin API, exactly as a back-office integration would) - and
 * then the customer approves and later revokes that consent themselves, through the real
 * self-service UI. The element/purpose/service names are deliberately realistic (not
 * uniqueMarker()-style test IDs): this is the dataset meant to be shown in a demo, so it's kept
 * idempotent (see getOrCreateElement/getOrCreatePurpose) rather than growing a new near-identical
 * "Marketing Communications" purpose every time the flow is rehearsed.
 */
test.describe('Full consent lifecycle (demo dataset)', () => {
  test('99.02.01 - A customer consents to Marketing Communications, then approves and revokes it themselves', async ({
    consentAdminPage,
    consentAdminConsentApi,
    dataPrincipalPage,
  }) => {
    // --- 1. Elements: the actual personal data marketing communications need -----------------
    const fullName = await getOrCreateElement(consentAdminPage, consentAdminConsentApi, {
      name: 'full_name',
      displayName: 'Full Name',
      description: "The customer's full legal name, used to personalize communications.",
    })
    const email = await getOrCreateElement(consentAdminPage, consentAdminConsentApi, {
      name: 'email_address',
      displayName: 'Email Address',
      description: 'Email address used to send marketing communications.',
    })
    const phone = await getOrCreateElement(consentAdminPage, consentAdminConsentApi, {
      name: 'phone_number',
      displayName: 'Phone Number',
      description: 'Mobile number used for promotional SMS campaigns.',
    })

    // --- 2. Purpose: what the business is actually asking permission for --------------------
    const purpose = await getOrCreatePurpose(consentAdminPage, consentAdminConsentApi, {
      name: 'Marketing Communications',
      type: 'Marketing',
      version: 'v1',
      description:
        'Consent to receive marketing emails, SMS, and personalized offers about new products and promotions.',
    })

    // --- 3. Consent: the customer's own record, pending their approval ----------------------
    const serviceId = 'loyalty-rewards-app'
    const consentResponse = await consentAdminConsentApi.createConsent({
      subjectId: env.dataPrincipal.username,
      serviceId,
      // Required despite being optional in the schema - omitting it 500s (NOT NULL DB column,
      // no server-side default; see utils/consentSetup.ts for the same finding).
      language: 'en',
      purposes: [
        {
          id: purpose.id,
          elements: [{ id: fullName.id }, { id: email.id }, { id: phone.id }],
        },
      ],
      // Presence of `authorizations` is what puts the consent in PENDING - an explicit `state`
      // is rejected alongside it.
      authorizations: [{ userId: env.dataPrincipal.username, type: 'USER' }],
    })
    expect(consentResponse.status()).toBe(201)
    const consent = (await consentResponse.json()) as { id: string }

    // --- 4. The customer reviews and approves it, through the real self-service UI ----------
    const registryPage = new ConsentRegistryPage(dataPrincipalPage)
    await registryPage.goto()
    await registryPage.searchByService(serviceId)
    await expect(registryPage.rowByConsentId(consent.id)).toContainText('Pending')

    await registryPage.openByConsentId(consent.id)
    const detailPage = new ConsentDetailPage(dataPrincipalPage, 'self')
    await expect(dataPrincipalPage).toHaveURL(new RegExp(`/consents/${consent.id}$`))

    await detailPage.expandPurpose(purpose.name)
    await expect(detailPage.elementRow('Full Name')).toContainText('Full Name')
    await expect(detailPage.elementRow('Email Address')).toContainText('Email Address')
    await expect(detailPage.elementRow('Phone Number')).toContainText('Phone Number')

    await detailPage.openActionDialog('approve')
    await expect(detailPage.dialogTitle('approve')).toBeVisible()
    await detailPage.confirmAction('approve')
    await expect(dataPrincipalPage.getByText('Active', { exact: true })).toBeVisible()

    // --- 5. Later, the customer changes their mind and revokes it, still through the UI -----
    await detailPage.openActionDialog('revoke')
    await expect(detailPage.dialogTitle('revoke')).toBeVisible()
    await detailPage.confirmAction('revoke')

    // .first(): the metadata card's state chip and the authorizations table's own state chip
    // both render "Revoked" once the sole authorizer is moved to that state too.
    await expect(dataPrincipalPage.getByText('Revoked', { exact: true }).first()).toBeVisible()
    await expect(dataPrincipalPage.getByRole('button', { name: 'Revoke', exact: true })).toHaveCount(0)
  })
})
