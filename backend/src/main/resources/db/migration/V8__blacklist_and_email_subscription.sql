-- 访问黑名单：支持拉黑用户 / IP / 游客 guestKey
CREATE TABLE IF NOT EXISTS access_blacklist (
    id bigserial PRIMARY KEY,
    target_type varchar(16) NOT NULL,
    target_value varchar(255) NOT NULL,
    reason varchar(500),
    enabled boolean NOT NULL DEFAULT true,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_access_blacklist_target UNIQUE (target_type, target_value)
);
CREATE INDEX IF NOT EXISTS idx_access_blacklist_enabled ON access_blacklist(enabled, target_type);

-- 邮件订阅：新文通知
CREATE TABLE IF NOT EXISTS email_subscription (
    id bigserial PRIMARY KEY,
    email varchar(255) NOT NULL UNIQUE,
    token varchar(64) NOT NULL UNIQUE,
    confirmed boolean NOT NULL DEFAULT true,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    unsubscribed_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_email_subscription_enabled ON email_subscription(enabled, confirmed);

-- 中文友好全文检索：simple 配置 + 权重（title > summary > content）
-- 不依赖 zhparser 扩展，配合已有 pg_trgm 索引做兜底。
ALTER TABLE IF EXISTS article ADD COLUMN IF NOT EXISTS search_vector tsvector;
UPDATE article SET search_vector =
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(content, '')), 'C')
WHERE search_vector IS NULL;

CREATE INDEX IF NOT EXISTS idx_article_search_vector ON article USING gin (search_vector);

CREATE OR REPLACE FUNCTION article_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(NEW.summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(NEW.content, '')), 'C');
  RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_article_search_vector ON article;
CREATE TRIGGER trg_article_search_vector
BEFORE INSERT OR UPDATE OF title, summary, content ON article
FOR EACH ROW EXECUTE FUNCTION article_search_vector_update();
