-- 评论「有回复时邮件通知」：勾选后写入，回复时通过消息通知平台 /api/email 发信。
ALTER TABLE IF EXISTS article_comment
    ADD COLUMN IF NOT EXISTS notify_on_reply boolean NOT NULL DEFAULT false;
