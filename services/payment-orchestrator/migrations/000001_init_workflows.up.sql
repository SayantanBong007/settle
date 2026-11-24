-- Phase 2 Schema for payment-orchestrator (runs inside the 'orchestrator' Postgres database)

-- 1. WORKFLOWS: Durable checklist for every money transfer (1 row = 1 transfer saga)
CREATE TABLE IF NOT EXISTS workflows (
    id                     VARCHAR(64)  PRIMARY KEY,
    idempotency_key        VARCHAR(128) NOT NULL UNIQUE,
    source_account_id      VARCHAR(64)  NOT NULL,
    destination_account_id VARCHAR(64)  NOT NULL,
    amount                 BIGINT       NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)   NOT NULL,
    hold_id                VARCHAR(64),
    state                  VARCHAR(32)  NOT NULL CHECK (state IN (
                               'CREATED',
                               'RISK_CHECK',
                               'PAUSED_FOR_REVIEW',
                               'HOLD_PLACED',
                               'WAITING_FOR_BANK',
                               'BANK_SUCCEEDED',
                               'BANK_FAILED',
                               'COMPLETED',
                               'DECLINED',
                               'FAILED'
                           )),
    failure_reason         VARCHAR(256),
    attempts               INT          NOT NULL DEFAULT 0,
    next_run_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Worker Lease & Fencing Token columns for crash recovery and zombie worker protection:
    lease_owner            VARCHAR(64),
    lease_until            TIMESTAMPTZ,
    fence_token            BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflows_poll
    ON workflows (next_run_at)
    WHERE state IN ('CREATED', 'RISK_CHECK', 'HOLD_PLACED', 'BANK_SUCCEEDED', 'BANK_FAILED');

-- 2. WORKFLOW_EVENTS: Append-only history of every state transition for auditing & replay
CREATE TABLE IF NOT EXISTS workflow_events (
    id          BIGSERIAL    PRIMARY KEY,
    workflow_id VARCHAR(64)  NOT NULL REFERENCES workflows(id),
    from_state  VARCHAR(32),
    to_state    VARCHAR(32)  NOT NULL,
    fence_token BIGINT       NOT NULL,
    detail      VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_events_workflow_id ON workflow_events(workflow_id);
