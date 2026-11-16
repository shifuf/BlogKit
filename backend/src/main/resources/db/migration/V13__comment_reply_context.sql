-- Preserve the actual reply target independently from the flattened thread parent.
ALTER TABLE article_comment ADD COLUMN IF NOT EXISTS reply_to_comment_id bigint;
ALTER TABLE article_comment ADD COLUMN IF NOT EXISTS reply_to_name varchar(64);
ALTER TABLE article_comment ADD COLUMN IF NOT EXISTS reply_to_content varchar(200);

UPDATE article_comment c SET reply_to_comment_id = p.id,
    reply_to_name = left(coalesce(nullif(p.guest_name, ''), u.display_name, '已删除用户'), 64),
    reply_to_content = left(regexp_replace(p.content, '<[^>]*>', '', 'g'), 200)
FROM article_comment p JOIN user_account u ON u.id = p.author_id
WHERE c.parent_id = p.id AND c.reply_to_comment_id IS NULL AND c.article_id = p.article_id;

-- Refuse malformed chains rather than silently losing comments on an upgrade.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM article_comment c JOIN article_comment p ON p.id = c.parent_id
               WHERE c.article_id <> p.article_id) THEN
        RAISE EXCEPTION 'Cross-article comment chain must be repaired before migration';
    END IF;
    IF EXISTS (
        WITH RECURSIVE threads AS (
            SELECT id, id AS root_id FROM article_comment WHERE parent_id IS NULL
            UNION ALL
            SELECT c.id, t.root_id FROM article_comment c JOIN threads t ON c.parent_id = t.id
        ) SELECT 1 FROM article_comment c WHERE NOT EXISTS (SELECT 1 FROM threads t WHERE t.id = c.id)
    ) THEN
        RAISE EXCEPTION 'Cyclic or orphaned comment chain must be repaired before migration';
    END IF;
END $$;

WITH RECURSIVE threads AS (
    SELECT id, id AS root_id FROM article_comment WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, t.root_id FROM article_comment c JOIN threads t ON c.parent_id = t.id
)
UPDATE article_comment c SET parent_id = t.root_id FROM threads t
WHERE c.id = t.id AND c.id <> t.root_id AND c.parent_id IS DISTINCT FROM t.root_id;

UPDATE article_comment root SET reply_count = (
    SELECT count(*) FROM article_comment r WHERE r.parent_id = root.id
    AND (r.status IS NULL OR r.status = 'APPROVED')
);
UPDATE article a SET comment_count = (
    SELECT count(*) FROM article_comment c WHERE c.article_id = a.id
    AND (c.status IS NULL OR c.status = 'APPROVED')
);
CREATE INDEX IF NOT EXISTS idx_comment_thread_status_created ON article_comment(parent_id, status, created_at, id);
CREATE INDEX IF NOT EXISTS idx_comment_reply_target ON article_comment(reply_to_comment_id);
