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
- Local verification: frontend 41/41 tests, dashboard 38/38 tests, Maven 97 tests with 0 failures and 9 environment-skipped integrations, both frontend/dashboard production builds, type checks and Vue lint passed. Existing chunk, CSS/PostCSS and import warnings remain.
- A current encrypted production backup was created at `/www/wwwroot/blog/backups/physical/backup-20261120T112209Z`; its manifest authentication and file hashes verified. `offsiteVerified` is false.
