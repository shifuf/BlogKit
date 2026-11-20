# Comment AI moderation and platform music search implementation plan

> **For agentic workers:** Use test-driven-development and systematic-debugging. Independently owned music and dynamic-comment changes may run in parallel; integrate against the interfaces below and review the complete change before deployment.

**Goal:** Deliver the approved AI comment review, durable own-comment visibility/status badges, and platform-selectable music search.
**Architecture:** A server-issued HttpOnly guest capability identifies ownership; service/repository queries enforce visibility. A durable SQL moderation queue invokes the existing AI service outside transactions, then conditionally publishes through comment services. Music filters providers before merging.
**Tech Stack:** Java 17/Spring Boot/PostgreSQL, Nuxt/Vue 3, Ant Design Vue, Node test runner, Maven, Playwright.
**Spec:** ../specs/2026-11-20-comment-ai-and-music-platform-design.md (approved by user: 按这份方案实施).

## Global constraints
- Continue in the existing requested checkout `F:\博客系统`; preserve prior authorization to commit/push main and deploy blog services. No new worktree or repeated approval is needed.
- Remote writes stay under `/www/wwwroot/blog`; the existing nginx exception is not needed for this change.
- Never print credentials or AI configuration values; reuse existing provider/model configuration.
- Keep historical migrations unchanged, database/Redis containers and volumes intact; back up before deployment.
- AI calls run outside database transactions; only explicit structured approval publishes. Failures remain pending, limited retries, durable leases.

## Review focus
- Same-name guests, missing/invalid cookies, logout and article navigation cannot expose somebody else's pending text.
- Reply preview/pagination and page refresh retain own pending comments without inflating public counts or leaking a hidden root/quote.
- Deleted/hidden/manually reviewed comments cannot be republished by stale AI jobs; retry and lease expiry cannot duplicate counts/notifications.
- Malformed or instruction-bearing AI responses never mean approval; only comment body/context goes to AI.
- Selected music platform is respected before the 30-item cap; late search or preview responses cannot restore results for an obsolete platform.

## Shared interfaces / file ownership
- Root owns `GuestCommentIdentity`, `CommentModerationPolicy`, `CommentModerationJobs`, `CommentAiReviewer`, `CommentModerationWorker`, migration V19, settings/bootstrap, ArticleComment/CommentService/repository/DTO, frontend types/adapters and article CommentSection.
- Dynamic implementer owns DynamicComment/repository/DTO/service, dynamic frontend page and dynamic admin controls/API/tests. Do not edit shared settings, migration, types or PublicSystemController; request any changes from root.
- Music implementer owns MusicService, MusicToolbar, music API wrapper/tests and the music search endpoint in PublicSystemController only.
- `GuestCommentIdentity.currentHash(): String` creates/reuses a 180-day HttpOnly SameSite=Lax cookie with 32 random bytes, returns SHA-256 hex; no request context returns null. `owns(String hash, Long userId): boolean` compares real logged-in user ID when present, otherwise nonempty hash; system guest user never matches by account ID.
- `CommentModerationPolicy.initialStatus(String content): ArticleCommentStatus` runs existing spam rules then chooses pending when AI or manual moderation enabled, approved otherwise. `aiEnabled(): boolean` reads new default-true setting.
- `CommentModerationJobs.enqueue(String kind,long id)`, `cancel(String kind,long id)`, `finish(String kind,long id,String lease): boolean`; kinds ARTICLE/DYNAMIC. Enqueue new pending comments only when AI enabled. Claim/retry is owned by worker; finish returns true only for current RUNNING lease and is used inside service's publish transaction after locking target.
- Services expose `String aiReviewContent(long id)` (null for absent/ineligible; read-only transaction) and `void applyAiReview(long id,String lease,boolean approved)` (lock target, verify pending and parent/resource visibility, call jobs.finish, update status/counts once). AI never needs request-user access. Manual status/delete paths cancel jobs.
- DynamicComment gets `guestIdentityHash` (64), `ownerUserId` (nullable bigint), `status` (APPROVED/PENDING/REJECTED/SPAM), existing visible remains administrative visibility. Existing dynamic rows backfill APPROVED, hidden rows stay hidden. Public dynamic DTO adds status and mine; admin adds status and visible. Root adds matching frontend type fields.
- ArticleComment gets `guestIdentityHash`; CommentResponse adds `mine`. Public reply preview returns at most 2 visible-to-viewer records and pagination total reflects visible-to-viewer records, while replyCount/commentCount remain public-only.

## Task 1: Article identity, durable visibility and shared review infrastructure
- [x] Write failing tests for cookie persistence/isolation; article root/reply ownership, anonymous guest shared-account exclusion, correct mine flag, nonapproved reply counts and private quotes.
- [x] Run targeted Maven tests and confirm expected missing-behavior failures.
- [x] Implement GuestCommentIdentity; V19 guest/dynamic fields, moderation job table/indexes; shared moderation policy and settings UI/default-true setting.
- [x] Update article queries/DTOs/service, lock target before status changes; cancel stale tasks on human actions and deletion. Only approved comments notify recipients.
- [x] Add AI review tests: explicit allow/deny, malformed/unavailable results, retry exhaustion, stale lease/manual decision cannot publish, counters update exactly once.
- [x] Implement reviewer, queue and dedicated scheduled worker; fetch at most one job at a time, three attempts with 15/60-second backoff, two-minute leases. Use strict JSON `{ "approved": true|false }` with no additional payload accepted. Do not log model output or comment body on failure.
- [x] Run targeted/full Maven test suite and record results; commit after integration with dynamic service compiles.

## Task 2: Dynamic comments and frontend
- [x] Add failing dynamic service tests for pending persistence, owner isolation, root restrictions, AI publication and pending delete counts; run and confirm red.
- [x] Implement shared interfaces above, preserve admin hidden state; add per-comment admin approve/reject actions with typed endpoints in a separate controller if needed.
- [x] Update dynamic frontend mine/status badges, public count changes only for approved, reply paging merge and bounded cleanup polling. Discard stale requests on close/unload/auth changes.
- [x] Add regression tests for stale response/pagination/poll cleanup using executable component logic; run relevant tests and send integration evidence.

## Task 3: Music platforms
- [x] Add failing tests for qq/netease/kugou provider selection, validation and before-limit filtering; run red.
- [x] Implement `search(String keyword,String platform)` preserving `search(String)` default; whitelist all/qq/netease/kugou. Selected platform sources only; result labels use platform names; preserve existing encrypted rid/resolve behavior.
- [x] Add platform selector and API param, current-search generation guard and preview invalidation. Add executable component tests that an older platform result/preview never overwrites newer state.
- [x] Run relevant Maven/Node tests, dashboard lint/typecheck and report owned files; root runs final builds/deployment.

## Task 4: Article frontend
- [x] Write failing tests for backend mine mapping, status badge labels, replacing duplicate IDs, preserving pending replies during pagination, stale article/auth responses and polling cleanup.
- [x] Implement shared compact CommentStatusBadge, adapter/types, article CommentSection ownership/merge/async guards, bounded refresh and accurate public counts; make dynamic page reuse badge.
- [x] Run frontend tests/lint/typecheck; verify 320px and dark appearance.

## Task 5: Integration and delivery
- [x] Review all diffs; fresh independent review against approved spec; fix important findings with regressions.
- [x] Run full frontend/dashboard/Maven tests, lint/typecheck and production builds. Serialize backend/frontend builds on constrained server.
- [ ] Commit/push changes, encrypted production backup, capture app/data container baseline, stage source and tested jar under blog; build production frontend with Compose configuration.
- [ ] Replace only backend/frontend; wait V19+Flyway validation and healthy services; verify DB/Redis IDs and mounts unchanged.
- [ ] Verify real AI pass -> public, blocked/pending owner persists after reload, other same-name visitor cannot read, article/dynamic replies and platform searches/preview. Remove only controlled test records.
- [ ] Sync docs/evidence, final commit/push, close SSH; report test and deployment results with material limitations.

## Integrated verification

- Backend: 156 tests passed, zero failures/errors/skips, using isolated PostgreSQL17 for migrations, SQL jobs and JPA publication.
- Frontend: 78 tests, lint (100 Vue components), typecheck and final Nuxt production build passed.
- Dashboard: 49 tests, lint, typecheck and Vite production build passed.
- Independent review reported four Important findings. Regressions first reproduced stale reply cursors, lost private preview replies, concurrent identity initialization and precommit email delivery; all now pass after coordinated refresh, browser bootstrap/Web Locks and after-commit idempotent email changes. No additional review pass requested.
- Production encrypted backup prepared at `/www/wwwroot/blog/backups/physical/backup-20261120T155217Z`; local-only (`offsiteVerified=false`). Disposable test PostgreSQL stopped and tunnel closed before production build.
