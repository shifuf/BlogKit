-- Schema changes belong to the migration account; the runtime account is DML only.
ALTER TABLE article DROP CONSTRAINT IF EXISTS article_visibility_check;
ALTER TABLE article ADD CONSTRAINT article_visibility_check CHECK (visibility IN ('PUBLIC','LOGIN','PASSWORD','PRIVATE','UNLISTED'));
ALTER TABLE dynamic_post ALTER COLUMN title DROP NOT NULL;

ALTER TABLE article ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE article ADD COLUMN IF NOT EXISTS public_content text;

-- Existing gated documents are excluded from body search until saved through the parser.
-- This deliberately fails closed instead of trying to duplicate Markdown with a SQL regex.
UPDATE article SET public_content = CASE WHEN content LIKE '%bd-secret%' THEN '' ELSE content END
WHERE public_content IS NULL;
-- Old auto/AI summaries may have been generated from unfiltered container text; keep private backups, fail closed publicly.
UPDATE article SET summary='',ai_summary=NULL WHERE content LIKE '%bd-secret%';

CREATE OR REPLACE FUNCTION article_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector := setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
    CASE WHEN NEW.visibility = 'PUBLIC' THEN
      setweight(to_tsvector('simple', coalesce(NEW.summary, '')), 'B') ||
      setweight(to_tsvector('simple', coalesce(NEW.public_content, '')), 'C')
    ELSE ''::tsvector END;
  RETURN NEW;
END
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_article_search_vector ON article;
CREATE TRIGGER trg_article_search_vector BEFORE INSERT OR UPDATE OF title, summary, public_content, visibility ON article
FOR EACH ROW EXECUTE FUNCTION article_search_vector_update();
UPDATE article SET public_content = public_content;
CREATE INDEX IF NOT EXISTS idx_article_public_content_trgm ON article USING gin (lower(public_content) gin_trgm_ops);

-- Dynamic interactions belong to their parent; deleting the parent deletes its replies and likes.
DO $$ DECLARE constraint_row record; BEGIN
  FOR constraint_row IN
    SELECT conname, conrelid::regclass AS table_name, pg_get_constraintdef(oid) AS definition
    FROM pg_constraint WHERE contype = 'f' AND confrelid = 'dynamic_post'::regclass
  LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_name, constraint_row.conname);
    EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s ON DELETE CASCADE', constraint_row.table_name,
      constraint_row.conname, regexp_replace(constraint_row.definition, ' ON DELETE (NO ACTION|RESTRICT|CASCADE|SET NULL|SET DEFAULT)', ''));
  END LOOP;
END $$;
