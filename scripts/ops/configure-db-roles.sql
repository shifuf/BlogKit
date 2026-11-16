-- Executed by an existing database administrator; never edits Flyway history.
\set ON_ERROR_STOP on
\getenv app_user DB_USERNAME
\getenv app_password DB_PASSWORD
\getenv migration_user DB_MIGRATION_USERNAME
\getenv migration_password DB_MIGRATION_PASSWORD
\getenv backup_user BLOG_BACKUP_DB_USERNAME
\getenv backup_password BLOG_BACKUP_DB_PASSWORD
SELECT 'business, migration and backup roles must be distinct from administrator' AS error
 WHERE :'app_user'=current_user OR :'migration_user'=current_user OR :'backup_user'=current_user OR :'app_user'=:'migration_user' OR :'app_user'=:'backup_user' OR :'migration_user'=:'backup_user' \gset
\if :{?error}
 \echo Role separation validation failed
 \quit 3
\endif
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE', name)
 FROM (VALUES (:'app_user'),(:'migration_user'),(:'backup_user')) roles(name)
 WHERE NOT EXISTS(SELECT FROM pg_roles WHERE rolname=name) \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'app_user', :'app_password') \gexec
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'migration_user', :'migration_password') \gexec
SELECT format('ALTER ROLE %I LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'backup_user', :'backup_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I, %I, %I', current_database(), :'app_user', :'migration_user', :'backup_user') \gexec
SELECT format('GRANT CREATE ON DATABASE %I TO %I, %I', current_database(), :'migration_user', :'backup_user') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('ALTER SCHEMA public OWNER TO %I', :'migration_user') \gexec
SELECT format('ALTER %s %I.%I OWNER TO %I', CASE c.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END, n.nspname,c.relname,:'migration_user') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m') ORDER BY CASE c.relkind WHEN 'S' THEN 1 ELSE 0 END \gexec
SELECT format('ALTER %s %I.%I(%s) OWNER TO %I', CASE p.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END, n.nspname,p.proname,pg_get_function_identity_arguments(p.oid),:'migration_user')
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prokind IN ('f','p')
AND NOT EXISTS (SELECT FROM pg_depend d WHERE d.classid='pg_proc'::regclass AND d.objid=p.oid AND d.deptype='e') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('REVOKE INSERT,UPDATE,DELETE ON TABLE public.flyway_schema_history FROM %I', :'app_user') WHERE to_regclass('public.flyway_schema_history') IS NOT NULL \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO %I', :'migration_user', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO %I', :'migration_user', :'app_user') \gexec
SELECT format('GRANT %I TO %I', :'migration_user', :'backup_user') \gexec
-- CREATE EXTENSION is pre-provisioned by admin. Future migrations run as owner, not superuser.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
