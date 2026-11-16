-- 历史库可能由旧版 Hibernate 建出不含 SPAM 的 check；标记垃圾评论会违反约束。
-- Flyway V1 baseline 已含 SPAM，本迁移只修已存在的错误约束。
ALTER TABLE article_comment DROP CONSTRAINT IF EXISTS article_comment_status_check;
ALTER TABLE article_comment
    ADD CONSTRAINT article_comment_status_check
    CHECK (status IS NULL OR status IN ('PENDING', 'APPROVED', 'REJECTED', 'SPAM'));
