-- ReinChat server schema. Every statement is safe to run on every startup.
CREATE TABLE IF NOT EXISTS users (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    username       TEXT    NOT NULL,
    username_lower TEXT    NOT NULL UNIQUE,
    password_hash  TEXT    NOT NULL,
    created_at     INTEGER NOT NULL,
    level          INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS tokens (
    token_hash BLOB    NOT NULL PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tokens_user_id ON tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_tokens_expires_at ON tokens(expires_at);

CREATE TABLE IF NOT EXISTS providers (
    user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id             TEXT    NOT NULL,
    name           TEXT    NOT NULL,
    protocol       TEXT    NOT NULL,
    base_url       TEXT    NOT NULL,
    api_key_enc    BLOB    NOT NULL,
    model          TEXT    NOT NULL,
    system_prompt  TEXT    NOT NULL,
    temperature    REAL,
    max_tokens     INTEGER,
    updated_at     INTEGER NOT NULL,
    PRIMARY KEY (user_id, id)
);

CREATE TABLE IF NOT EXISTS files (
    id         TEXT    NOT NULL PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mime_type  TEXT    NOT NULL,
    size_bytes INTEGER NOT NULL,
    file_name  TEXT    NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_files_user_id ON files(user_id);

CREATE TABLE IF NOT EXISTS conversations (
    user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id             TEXT    NOT NULL,
    title          TEXT    NOT NULL,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    version        INTEGER NOT NULL,
    messages_json  TEXT    NOT NULL,
    message_count  INTEGER NOT NULL,
    preview        TEXT    NOT NULL,
    settings_json  TEXT    NOT NULL DEFAULT '{"reasoningEffort":"high","search":true}',
    PRIMARY KEY (user_id, id)
);
CREATE INDEX IF NOT EXISTS idx_conversations_user_updated
    ON conversations(user_id, updated_at DESC, id);

CREATE TABLE IF NOT EXISTS upgrade_requests (
    id            TEXT    NOT NULL PRIMARY KEY,
    user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    from_level    INTEGER NOT NULL,
    to_level      INTEGER NOT NULL,
    reason        TEXT    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'pending',
    reject_reason TEXT,
    admin_note    TEXT,
    reviewed_by   TEXT,
    created_at    INTEGER NOT NULL,
    reviewed_at   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_upgrade_requests_status
    ON upgrade_requests(status, created_at);
CREATE INDEX IF NOT EXISTS idx_upgrade_requests_user
    ON upgrade_requests(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS relay_jobs (
    id              TEXT    NOT NULL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id TEXT,
    provider_id     TEXT    NOT NULL,
    status          TEXT    NOT NULL,
    text            TEXT    NOT NULL DEFAULT '',
    reasoning       TEXT    NOT NULL DEFAULT '',
    error           TEXT,
    artifacts_json  TEXT    NOT NULL DEFAULT '{}',
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_relay_jobs_user
    ON relay_jobs(user_id, created_at DESC);
