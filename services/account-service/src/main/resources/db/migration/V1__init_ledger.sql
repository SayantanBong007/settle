-- 1. ACCOUNTS: Each user's wallet (spendable available_balance + in-flight pending_balance, in paise)
CREATE TABLE accounts (
    id                VARCHAR(64) PRIMARY KEY,
    owner_id          VARCHAR(64) NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    -- SYSTEM_FUNDING account is allowed negative balance to double-entry fund user accounts (sum of all entries = 0)
    available_balance BIGINT      NOT NULL DEFAULT 0 CHECK (owner_id = 'SYSTEM' OR available_balance >= 0),
    pending_balance   BIGINT      NOT NULL DEFAULT 0 CHECK (pending_balance >= 0),
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. HOLDS: Tracks each in-flight transfer lock (PENDING -> COMPLETED or CANCELLED)
CREATE TABLE holds (
    id                     VARCHAR(64)  PRIMARY KEY,
    transfer_id            VARCHAR(64)  NOT NULL,
    source_account_id      VARCHAR(64)  NOT NULL REFERENCES accounts(id),
    destination_account_id VARCHAR(64)  NOT NULL REFERENCES accounts(id),
    amount                 BIGINT       NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)   NOT NULL,
    status                 VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    cancel_reason          VARCHAR(128),
    expires_at             TIMESTAMPTZ  NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 3. LEDGER_ENTRIES: Permanent passbook history (every account's balance = sum of its CREDIT minus DEBIT entries)
CREATE TABLE ledger_entries (
    id          BIGSERIAL   PRIMARY KEY,
    transfer_id VARCHAR(64) NOT NULL,
    hold_id     VARCHAR(64) REFERENCES holds(id),
    account_id  VARCHAR(64) NOT NULL REFERENCES accounts(id),
    entry_type  VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount      BIGINT      NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);

-- 4. IDEMPOTENCY_RECORDS: Prevents network retries from running the same request twice
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    response_json   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
