ALTER TABLE user_account ADD COLUMN IF NOT EXISTS totp_pending_secret_encrypted VARCHAR(512);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS totp_pending_expires_at TIMESTAMPTZ;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_verified_user_email ON user_account(lower(email)) WHERE email_verified=TRUE;
CREATE TABLE IF NOT EXISTS user_totp_recovery_code (
 user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
 code_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(user_id, code_hash)
);
