-- Older parser revisions could retain credential-bearing headers or an equal-fence
-- nested section's tail in public_content. Existing protected articles fail closed
-- in search until their next save rebuilds public_content with the current parser.
UPDATE article
SET public_content = '', summary = '', ai_summary = NULL
WHERE content LIKE '%bd-secret%';
