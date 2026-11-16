-- IP 访问统计：供黑名单决策参考
CREATE TABLE IF NOT EXISTS visitor_ip_stat (
    id bigserial PRIMARY KEY,
    ip_address varchar(64) NOT NULL UNIQUE,
    visit_count bigint NOT NULL DEFAULT 0,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_visitor_ip_stat_count ON visitor_ip_stat(visit_count DESC);
CREATE INDEX IF NOT EXISTS idx_visitor_ip_stat_last_seen ON visitor_ip_stat(last_seen_at DESC);
