-- 友链展示位置与审核通知
ALTER TABLE IF EXISTS friend_link ADD COLUMN IF NOT EXISTS show_in_page boolean NOT NULL DEFAULT true;
ALTER TABLE IF EXISTS friend_link ADD COLUMN IF NOT EXISTS show_in_footer boolean NOT NULL DEFAULT false;
ALTER TABLE IF EXISTS friend_link ADD COLUMN IF NOT EXISTS notify_on_review boolean NOT NULL DEFAULT false;

-- 历史已通过友链：默认同时在友链页与页脚展示（兼容旧行为「只在页脚」升级为双开）
UPDATE friend_link
SET show_in_page = true,
    show_in_footer = true
WHERE status = 'APPROVED' AND visible = true;
