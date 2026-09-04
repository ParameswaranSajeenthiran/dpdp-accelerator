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

-- Database DDL for Complaint Server (PostgreSQL). Mirrors h2.sql in this directory - keep both
-- in sync by hand. Differences from h2.sql are purely dialect:
--   * BLOB -> bytea. The DAO already reads and writes this column with setBytes/getBytes, which
--     is the only portable pairing for bytea (pgjdbc's getBlob expects a large-object OID), so
--     no Java change goes with this.
--   * LENGTH(FILE_DATA) stays LENGTH() - PostgreSQL defines length(bytea) as the byte count,
--     matching what H2 and MySQL return for the same expression.
-- Identifiers are deliberately left unquoted so PostgreSQL folds them to lower case; every DAO
-- read goes through pgjdbc's case-insensitive column lookup, so the Java-side upper-case column
-- constants keep working. Do not add quoted identifiers here - that would fold-mismatch the DDL
-- against the queries and break every read.
--
-- UPDATED_TIME needs no trigger the way event-notification's UPDATED_AT does: it is an
-- application-managed BIGINT epoch that every UPDATE sets explicitly.

-- COMPLAINT definition
CREATE TABLE IF NOT EXISTS COMPLAINT (
  COMPLAINT_ID char(36) NOT NULL,
  ORG_ID varchar(255) NOT NULL,
  USER_ID varchar(255) NOT NULL,
  USER_NAME varchar(255) DEFAULT NULL,
  REFERENCE_ID varchar(32) NOT NULL,
  CATEGORY varchar(64) NOT NULL,
  PRIORITY varchar(16) NOT NULL,
  STATUS varchar(32) NOT NULL DEFAULT 'OPEN',
  DESCRIPTION text NOT NULL,
  CREATED_TIME bigint NOT NULL,
  UPDATED_TIME bigint NOT NULL,
  STATUTORY_DUE_TIME bigint NOT NULL,
  PRIMARY KEY (COMPLAINT_ID, ORG_ID),
  CONSTRAINT UQ_COMPLAINT_REFERENCE UNIQUE (ORG_ID, REFERENCE_ID),
  -- Primary enforcement is the ComplaintPriority/ComplaintStatus enums at the app layer (see
  -- StatusTransitionValidator, PriorityMapper) - adding a new value there is a code change, not a
  -- DB migration. The CHECK here is only a backstop against direct writes that bypass that layer.
  CONSTRAINT CHK_COMPLAINT_PRIORITY CHECK (PRIORITY IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
  CONSTRAINT CHK_COMPLAINT_STATUS CHECK (STATUS IN
      ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CLIENT', 'AWAITING_INTERNAL_REVIEW', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS IDX_COMPLAINT_ORG_STATUS ON COMPLAINT (ORG_ID, STATUS);
CREATE INDEX IF NOT EXISTS IDX_COMPLAINT_ORG_USER ON COMPLAINT (ORG_ID, USER_ID);

-- COMPLAINT_EVENT definition (timeline: status changes, comments, internal notes)
CREATE TABLE IF NOT EXISTS COMPLAINT_EVENT (
  COMPLAINT_EVENT_ID char(36) NOT NULL,
  ORG_ID varchar(255) NOT NULL,
  COMPLAINT_ID char(36) NOT NULL,
  ACTOR_USER_ID varchar(255) DEFAULT NULL,
  ACTOR_USER_NAME varchar(255) DEFAULT NULL,
  ACTOR_ROLE varchar(32) NOT NULL,
  IS_PUBLIC boolean NOT NULL DEFAULT TRUE,
  COMMENT text DEFAULT NULL,
  FROM_STATUS varchar(32) DEFAULT NULL,
  TO_STATUS varchar(32) DEFAULT NULL,
  ACTION_TIME bigint NOT NULL,
  PRIMARY KEY (COMPLAINT_EVENT_ID, ORG_ID),
  CONSTRAINT FK_CE_COMPLAINT FOREIGN KEY (COMPLAINT_ID, ORG_ID) REFERENCES COMPLAINT (COMPLAINT_ID, ORG_ID),
  -- Primary enforcement is the ComplaintActorRole enum in ComplaintEventServiceImpl; this CHECK
  -- is only a backstop against direct writes (migrations, manual fixes) that bypass that layer.
  CONSTRAINT CHK_CE_ACTOR_ROLE CHECK (ACTOR_ROLE IN ('USER', 'COMPLAINT_OFFICER', 'SYSTEM'))
);

CREATE INDEX IF NOT EXISTS IDX_CE_COMPLAINT_TIME ON COMPLAINT_EVENT (COMPLAINT_ID, ORG_ID, ACTION_TIME);

-- COMPLAINT_ATTACHMENT definition
CREATE TABLE IF NOT EXISTS COMPLAINT_ATTACHMENT (
  ATTACHMENT_ID char(36) NOT NULL,
  ORG_ID varchar(255) NOT NULL,
  COMPLAINT_ID char(36) NOT NULL,
  COMPLAINT_EVENT_ID char(36) DEFAULT NULL,
  FILE_NAME varchar(255) NOT NULL,
  FILE_CONTENT_TYPE varchar(128) NOT NULL,
  FILE_DATA bytea NOT NULL,
  IS_PUBLIC boolean NOT NULL DEFAULT TRUE,
  CREATED_TIME bigint NOT NULL,
  PRIMARY KEY (ATTACHMENT_ID, ORG_ID),
  CONSTRAINT FK_CA_COMPLAINT FOREIGN KEY (COMPLAINT_ID, ORG_ID) REFERENCES COMPLAINT (COMPLAINT_ID, ORG_ID),
  CONSTRAINT FK_CA_EVENT FOREIGN KEY (COMPLAINT_EVENT_ID, ORG_ID)
      REFERENCES COMPLAINT_EVENT (COMPLAINT_EVENT_ID, ORG_ID)
);

CREATE INDEX IF NOT EXISTS IDX_CA_COMPLAINT ON COMPLAINT_ATTACHMENT (COMPLAINT_ID, ORG_ID);
CREATE INDEX IF NOT EXISTS IDX_CA_EVENT ON COMPLAINT_ATTACHMENT (COMPLAINT_EVENT_ID, ORG_ID);
