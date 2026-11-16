-- 移除文章专题/系列：删 FK、索引、列、表
alter table if exists article drop constraint if exists FK9u0wyf6ciosbjhytq4bd4md3f;
drop index if exists idx_article_series;
alter table if exists article drop column if exists series_id;
alter table if exists article drop column if exists series_order;
drop table if exists article_series;
