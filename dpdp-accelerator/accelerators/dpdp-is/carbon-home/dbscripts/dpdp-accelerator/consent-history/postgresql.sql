-- Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
--
-- WSO2 LLC. licenses this file to you under the Apache License,
-- Version 2.0 (the "License"); you may not use this file except
-- in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.

-- PostgreSQL DDL for the DPDP consent status-audit, history and expiry-tracker tables. Mirrors
-- h2.sql in this directory - keep both in sync by hand. The only dialect difference is
-- SNAPSHOT CLOB -> SNAPSHOT text.
--
-- text, deliberately, and not jsonb: jsonb rejects a plain string parameter (pgjdbc sends
-- setString as varchar and PostgreSQL will not implicitly cast it), so it would force a
-- CAST(? AS JSONB) override in ConsentHistoryDBQueries the way event-notification's EVENT.PAYLOAD
-- needs one. Keeping it a string column means setString/getString work unchanged on every dialect
-- and the snapshot is stored byte-for-byte, matching the H2 CLOB. The trade-off is no server-side
-- JSON validation or indexing here - the column is only ever read back whole.
--
-- Identifiers are deliberately left unquoted so PostgreSQL folds them to lower case; DAO reads
-- go through pgjdbc's case-insensitive column lookup, so the upper-case Java column constants
-- keep working. Do not add quoted identifiers here.

CREATE TABLE IF NOT EXISTS DPDP_CONSENT_STATUS_AUDIT (
  AUDIT_ID        varchar(36)  NOT NULL,
  CONSENT_ID      varchar(255) NOT NULL,
  ORG_ID          varchar(255) NOT NULL DEFAULT 'carbon.super',
  PREVIOUS_STATUS varchar(64),
  CURRENT_STATUS  varchar(64)  NOT NULL,
  ACTION_TYPE     varchar(64)  NOT NULL,
  ACTION_BY       varchar(255),
  ACTION_TIME     bigint       NOT NULL,
  PRIMARY KEY (AUDIT_ID)
);

CREATE INDEX IF NOT EXISTS IDX_DPDP_STATUS_AUDIT_CONSENT_TIME ON DPDP_CONSENT_STATUS_AUDIT (CONSENT_ID, ACTION_TIME);
CREATE INDEX IF NOT EXISTS IDX_DPDP_STATUS_AUDIT_ORG ON DPDP_CONSENT_STATUS_AUDIT (ORG_ID);

CREATE TABLE IF NOT EXISTS DPDP_CONSENT_HISTORY (
  HISTORY_ID  varchar(36)  NOT NULL,
  CONSENT_ID  varchar(255) NOT NULL,
  ORG_ID      varchar(255) NOT NULL DEFAULT 'carbon.super',
  ACTION_TYPE varchar(64)  NOT NULL,
  SNAPSHOT    text,
  ACTION_BY   varchar(255),
  ACTION_TIME bigint       NOT NULL,
  PRIMARY KEY (HISTORY_ID)
);

CREATE INDEX IF NOT EXISTS IDX_DPDP_HISTORY_CONSENT_TIME ON DPDP_CONSENT_HISTORY (CONSENT_ID, ACTION_TIME);
CREATE INDEX IF NOT EXISTS IDX_DPDP_HISTORY_ORG ON DPDP_CONSENT_HISTORY (ORG_ID);

CREATE TABLE IF NOT EXISTS DPDP_CONSENT_EXPIRY_TRACKER (
  CONSENT_ID  varchar(255) NOT NULL,
  ORG_ID      varchar(255) NOT NULL DEFAULT 'carbon.super',
  EXPIRY_TIME bigint       NOT NULL,
  PRIMARY KEY (CONSENT_ID)
);

CREATE INDEX IF NOT EXISTS IDX_DPDP_EXPIRY_TRACKER_TIME ON DPDP_CONSENT_EXPIRY_TRACKER (EXPIRY_TIME);
