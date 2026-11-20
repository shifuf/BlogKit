# Mobile and editor reliability implementation plan

> **For agentic workers:** Use test-driven-development and systematic-debugging. Independent domains use dispatching-parallel-agents; integrate and review before delivery.

**Goal:** Complete the eight mobile/editor/music issues in the linked chat.
**Architecture:** Targeted repairs to existing components and services, sharing current API and server-side permission filtering.
**Tech Stack:** Vue 3, Ant Design Vue, Nuxt, Spring Boot, Node test runner, Maven, Playwright.
**Spec:** ../specs/2026-11-20-mobile-editor-reliability.md

## Global Constraints
- Do not change unrelated layout, historical migrations or production data.
- Keep secrets out of git and logs; use existing protected connection configuration.
- Work in the existing requested checkout; preserve existing work.

## Review Focus
- Inactive tab close and repeated current-route selection cannot lose unsaved editor state.
- Denied/nested secret sections cannot leak in markdown, summaries, RSS, or search.
- Expired audio recovery preserves desired position and paused state, without retry loops.
- System appearance changes and saved manual override remain deterministic.
- At 320px names, footer, menus and password actions remain reachable.

## Tasks
- [x] 1. Admin navigation/taxonomy: reproduce touch menus and narrow tables, add regressions, fix Layout/tabs/store and Category/Tag, verify dashboard tests.
- [x] 2. Appearance: test initial/system theme; repair useTheme, bootstrap and scoped surfaces; use configured introduction background; reserve footer space; verify browser at 320/390/desktop.
- [x] 3. Protected editor blocks/time: write authorization/leak tests, extend toolbar/parser/filter/unlock endpoint and frontend interaction; verify backend and markdown tests.
- [x] 4. Music: reproduce expired link/cache and pending seek; add failure tests, refresh expiring resolution without optional lyric dependency; show compact editor music preview; verify music regressions.
- [x] 5. Integration: inspect all diffs, run full test suites/typecheck/lint/build and browser smoke; fix findings and record evidence.
- [ ] 6. Delivery: commit/push authorized changes, back up and deploy only blog app services, verify healthy containers and public/mobile flows.

## Progress / decisions
- Baseline: clean main at 61c5b1b; previous chat completed music-provider delivery but made no changes for this issue batch.
- Continue in the existing checkout as requested, without introducing another workspace or pausing for an already authorized continuation.
- Screenshot evidence recovered under ignored .runtime/handoff-{1..4}.png.
- Independent review caught mobile media seeking losing its last position when an expired Range stream reset `currentTime`; a regression test now covers and fixes that sequence.
- Local verification: frontend 43/43 tests, dashboard 38/38 tests, Maven 97 tests with 0 failures and 9 environment-skipped integrations, both frontend/dashboard production builds, type checks and Vue lint passed. Existing chunk, CSS/PostCSS and import warnings remain.
- A current encrypted production backup was created at `/www/wwwroot/blog/backups/physical/backup-20261120T112209Z`; its manifest authentication and file hashes verified. `offsiteVerified` is false.
- Main implementation was committed and pushed as `a4f9f4b`. Only blog backend/frontend were replaced; PostgreSQL/Redis container IDs and all existing mounts were verified unchanged, and all four services are healthy. V18 applied with Flyway validation intact (19 history entries).
- Production has no home banner configured. The mobile author introduction now falls back to the configured author image; regressions cover the fallback and explicit-banner precedence. Frontend was rebuilt on the server with production configuration and deployed successfully. Build contexts now exclude release staging, backup secrets and the secrets directory.
- Public browser verification passed at 320/390/1280px in both light and dark appearance, including system-theme defaults, manual override, introduction background, mobile menu, single article body/title, visible footer and no horizontal overflow or runtime errors. Admin login is usable at 320px. Public article APIs, feed, sitemap and music search/resolve passed.
- Delivery remains open: public music Range requests return 200 although backend and Nuxt each return 206. Read-only inspection found global `/www/server/nginx/conf/proxy.conf` enables `proxy_cache cache_one`, inherited by the blog vhost. An isolated nginx instance with all writable paths inside the blog directory reproduced 200 with inherited cache and 206 with `proxy_cache off;`; it was then stopped. The two-line site-only patch is staged under `.codex-release/a4f9f4b/nginx/`. Applying it to `/www/server/panel/vhost/nginx/blog.shifuf.com.conf` and reloading the active nginx requires explicit authorization because the user limited writes to `/www/wwwroot/blog`. No external configuration has been changed.
