CREATE TABLE IF NOT EXISTS reading_progress (
    user_id bigint NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    article_id bigint NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    progress double precision NOT NULL DEFAULT 0 CHECK(progress >= 0 AND progress <= 100),
    completed boolean NOT NULL DEFAULT false,
    later boolean NOT NULL DEFAULT false,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,article_id)
);
CREATE INDEX IF NOT EXISTS idx_reading_progress_user_updated ON reading_progress(user_id,updated_at DESC);
