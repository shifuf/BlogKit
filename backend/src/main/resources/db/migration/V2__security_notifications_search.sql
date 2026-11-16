-- Existing installations are baselined as version 1. All statements are idempotent for databases
-- that previously received the same columns through Hibernate ddl-auto=update.
ALTER TABLE IF EXISTS user_account ADD COLUMN IF NOT EXISTS totp_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE IF EXISTS user_account ADD COLUMN IF NOT EXISTS totp_secret_encrypted varchar(512);

CREATE TABLE IF NOT EXISTS user_login_session (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    device_name varchar(128),
    ip_address varchar(64),
    user_agent varchar(512),
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_login_session_user_active ON user_login_session(user_id, revoked_at, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS user_notification (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    type varchar(32) NOT NULL,
    title varchar(160) NOT NULL,
    content varchar(1000) NOT NULL,
    action_url varchar(512),
    source_type varchar(64) NOT NULL,
    source_id varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    read_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_notification_user_read_created ON user_notification(user_id, read_at, created_at DESC);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_article_title_trgm ON article USING gin (lower(title) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_article_summary_trgm ON article USING gin (lower(summary) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_article_content_trgm ON article USING gin (lower(content) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_article_public_feed ON article(status, listed_in_public, deleted_at, sticky DESC, sticky_order, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_comment_article_status_created ON article_comment(article_id, status, parent_id, created_at DESC);
