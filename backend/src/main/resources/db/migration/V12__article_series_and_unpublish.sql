-- 文章系列 + 定时下架
CREATE TABLE IF NOT EXISTS article_series (
    id bigserial PRIMARY KEY,
    name varchar(80) NOT NULL,
    slug varchar(80) NOT NULL UNIQUE,
    description varchar(500),
    cover_image_url varchar(512),
    display_order integer NOT NULL DEFAULT 0,
    visible boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE article ADD COLUMN IF NOT EXISTS series_id bigint;
ALTER TABLE article ADD COLUMN IF NOT EXISTS series_order integer NOT NULL DEFAULT 0;
ALTER TABLE article ADD COLUMN IF NOT EXISTS scheduled_unpublish_at timestamptz;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_article_series'
  ) THEN
    ALTER TABLE article
      ADD CONSTRAINT fk_article_series
      FOREIGN KEY (series_id) REFERENCES article_series(id) ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_article_series_order
    ON article (series_id, series_order)
    WHERE series_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_article_scheduled_unpublish
    ON article (status, scheduled_unpublish_at)
    WHERE deleted_at IS NULL AND scheduled_unpublish_at IS NOT NULL;
