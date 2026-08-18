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

import type { CreateElementBody } from '../clients/ConsentApiClient'

export interface NamedRecord {
  id: string
  name: string
}

/**
 * The realistic demo dataset tests/99-demo-data/99.01-seed-demo-data.spec.ts's "seed a rich demo dataset"
 * test creates.
 */
export const RICH_ELEMENTS: CreateElementBody[] = [
  { name: 'date_of_birth', displayName: 'Date of Birth', description: 'Used to verify age eligibility.' },
  {
    name: 'government_id_number',
    displayName: 'Government ID Number',
    description: 'National identity document number.',
  },
  {
    name: 'home_address',
    displayName: 'Home Address',
    description: 'Residential address for correspondence and delivery.',
  },
  {
    name: 'bank_account_number',
    displayName: 'Bank Account Number',
    description: 'Used for payment processing and refunds.',
  },
  {
    name: 'employment_status',
    displayName: 'Employment Status',
    description: 'Used for credit and eligibility assessments.',
  },
  {
    name: 'health_record_summary',
    displayName: 'Health Record Summary',
    description: 'Used for insurance claims processing.',
  },
  {
    name: 'device_id',
    displayName: 'Device ID',
    description: 'Used for fraud prevention and device recognition.',
  },
  {
    name: 'vehicle_registration_number',
    displayName: 'Vehicle Registration Number',
    description: 'Used for insurance and roadside assistance services.',
  },
  {
    name: 'educational_qualification',
    displayName: 'Educational Qualification',
    description: 'Used for employment and loan eligibility checks.',
  },
  {
    name: 'loyalty_card_number',
    displayName: 'Loyalty Card Number',
    description: 'Used to track and redeem loyalty program rewards.',
  },
  {
    name: 'marital_status',
    displayName: 'Marital Status',
    description: 'Used for insurance and tax eligibility assessments.',
  },
  {
    name: 'annual_income',
    displayName: 'Annual Income',
    description: 'Used for loan and credit eligibility assessments.',
  },
  {
    name: 'billing_address',
    displayName: 'Billing Address',
    description: 'Address used for invoicing and payment reconciliation.',
  },
  {
    name: 'emergency_contact_number',
    displayName: 'Emergency Contact Number',
    description: 'Used to reach a designated contact in case of an emergency.',
  },
  {
    name: 'passport_number',
    displayName: 'Passport Number',
    description: 'Used for travel booking and identity verification.',
  },
  {
    name: 'tax_identification_number',
    displayName: 'Tax Identification Number',
    description: 'Used for tax reporting and compliance obligations.',
  },
  {
    name: 'social_media_handle',
    displayName: 'Social Media Handle',
    description: 'Used for personalized social media engagement campaigns.',
  },
  {
    name: 'browsing_history',
    displayName: 'Browsing History',
    description: 'Used for behavioral analytics and ad targeting.',
  },
  {
    name: 'biometric_fingerprint_hash',
    displayName: 'Biometric Fingerprint Hash',
    description: 'Used for secure biometric authentication.',
  },
  {
    name: 'next_of_kin_details',
    displayName: 'Next of Kin Details',
    description: 'Used for insurance claims and beneficiary processing.',
  },
]

export type ConsentTargetState = 'ACTIVE' | 'PENDING' | 'REJECTED' | 'REVOKED'

export interface RichPurposeDefinition {
  name: string
  type: string
  description: string
  /** A realistic consent for this purpose is seeded against this service, in this end state. */
  serviceId: string
  consentState: ConsentTargetState
}

export const RICH_PURPOSES: RichPurposeDefinition[] = [
  {
    name: 'Promotional Offers',
    type: 'Marketing',
    description: 'Consent to receive promotional offers and discounts via email and SMS.',
    serviceId: 'loyalty-rewards-app',
    consentState: 'ACTIVE',
  },
  {
    name: 'Identity Verification',
    type: 'Compliance',
    description: "Consent to verify the customer's identity documents during onboarding.",
    serviceId: 'identity-verification-service',
    consentState: 'ACTIVE',
  },
  {
    name: 'Payment Processing',
    type: 'Financial',
    description: 'Consent to process payments and issue refunds for purchases made on the platform.',
    serviceId: 'payments-gateway',
    consentState: 'ACTIVE',
  },
  {
    name: 'Fraud Prevention',
    type: 'Security',
    description: 'Consent to analyze account activity and device signals to detect fraudulent transactions.',
    serviceId: 'fraud-detection-service',
    consentState: 'PENDING',
  },
  {
    name: 'Customer Support',
    type: 'Operational',
    description: 'Consent to access account details when providing customer support assistance.',
    serviceId: 'customer-support-portal',
    consentState: 'ACTIVE',
  },
  {
    name: 'Insurance Underwriting',
    type: 'Insurance',
    description: 'Consent to assess health and financial information for insurance policy underwriting.',
    serviceId: 'insurance-portal',
    consentState: 'PENDING',
  },
  {
    name: 'Employment Verification',
    type: 'HR',
    description: 'Consent to verify employment status and history for HR record-keeping.',
    serviceId: 'hr-management-system',
    consentState: 'ACTIVE',
  },
  {
    name: 'Loyalty Program Enrollment',
    type: 'Marketing',
    description: 'Consent to enroll in the loyalty rewards program and track reward points earned.',
    serviceId: 'loyalty-rewards-app',
    consentState: 'ACTIVE',
  },
  {
    name: 'Health Monitoring',
    type: 'Healthcare',
    description: 'Consent to collect and monitor health data through the wellness tracking app.',
    serviceId: 'health-tracker-app',
    consentState: 'REVOKED',
  },
  {
    name: 'Credit Check',
    type: 'Financial',
    description: "Consent to check the customer's credit history for loan eligibility assessment.",
    serviceId: 'credit-scoring-service',
    consentState: 'REJECTED',
  },
  {
    name: 'Personalized Recommendations',
    type: 'Marketing',
    description: 'Consent to use purchase history to generate personalized product recommendations.',
    serviceId: 'recommendation-engine',
    consentState: 'ACTIVE',
  },
  {
    name: 'Tax Compliance Reporting',
    type: 'Compliance',
    description: 'Consent to use financial records for tax filing and regulatory compliance reporting.',
    serviceId: 'tax-filing-service',
    consentState: 'ACTIVE',
  },
  {
    name: 'Travel Booking Assistance',
    type: 'Operational',
    description: 'Consent to use travel document details when booking flights and accommodation.',
    serviceId: 'travel-booking-app',
    consentState: 'PENDING',
  },
  {
    name: 'Social Media Engagement',
    type: 'Marketing',
    description: 'Consent to run personalized engagement campaigns on connected social media accounts.',
    serviceId: 'social-engagement-platform',
    consentState: 'ACTIVE',
  },
  {
    name: 'Behavioral Analytics',
    type: 'Analytics',
    description: 'Consent to analyze browsing behavior for product usage analytics and ad targeting.',
    serviceId: 'analytics-platform',
    consentState: 'PENDING',
  },
  {
    name: 'Biometric Authentication',
    type: 'Security',
    description: 'Consent to use biometric data for secure, passwordless account authentication.',
    serviceId: 'secure-auth-service',
    consentState: 'ACTIVE',
  },
  {
    name: 'Beneficiary Management',
    type: 'Insurance',
    description: "Consent to record next-of-kin details for insurance claim beneficiary processing.",
    serviceId: 'insurance-portal',
    consentState: 'ACTIVE',
  },
  {
    name: 'Loan Eligibility Assessment',
    type: 'Financial',
    description: 'Consent to assess income and employment status for loan eligibility decisions.',
    serviceId: 'lending-platform',
    consentState: 'PENDING',
  },
  {
    name: 'Emergency Notification Service',
    type: 'Operational',
    description: 'Consent to contact a designated emergency contact in the event of a safety incident.',
    serviceId: 'safety-notification-service',
    consentState: 'REVOKED',
  },
  {
    name: 'Billing & Invoicing',
    type: 'Financial',
    description: 'Consent to use billing address details for generating and sending invoices.',
    serviceId: 'billing-service',
    consentState: 'REJECTED',
  },
]
