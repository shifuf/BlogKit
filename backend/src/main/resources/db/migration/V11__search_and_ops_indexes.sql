-- 列表/搜索常用组合索引 + 确保 search_vector 回填
CREATE INDEX IF NOT EXISTS idx_article_listed_status_published
    ON article (listed_in_public, status, published_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_article_visibility_status
    ON article (visibility, status)
    WHERE deleted_at IS NULL;

-- 触发器在 V8 已建；这里仅兜底回填空向量
UPDATE article SET search_vector =
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(content, '')), 'C')
WHERE search_vector IS NULL;

-- 评论置顶
ALTER TABLE IF EXISTS article_comment ADD COLUMN IF NOT EXISTS pinned boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_article_comment_pinned
    ON article_comment (article_id, pinned DESC, created_at DESC)
    WHERE status = 'APPROVED';
