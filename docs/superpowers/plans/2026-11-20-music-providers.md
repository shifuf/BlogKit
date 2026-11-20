# Music Provider Update Implementation Plan

> **For agentic workers:** Use executing-plans for sequential implementation in this session.

**Goal:** Replace Mitu with ChKSz and configure all provider keys in the dashboard while retaining automatic playback.

**Architecture:** MusicSettingsService seals credentials in site_setting; MusicApiClient serializes and caches business calls and isolates provider failures; MusicService adapts and merges five sources. Existing playback and media proxies remain.

**Tech Stack:** Spring Boot / Java 17, JUnit HTTP fixtures, Vue dashboard, Python local test proxy.

**Spec:** docs/superpowers/specs/2026-11-20-music-providers-design.md

## Global Constraints

- No keys in source, logs or public responses.
- Remove all Mitu runtime paths; keep automatic playback.
- No automatic retries on authentication, quota or rate limit failures.

## Review Focus

- A failed provider must not suppress healthy results.
- Changing a saved key must take effect immediately.
- Repeated seeks must not repeat paid resolution calls.
- ChKSz nested search/parse payloads and missing lyrics must be handled.
- Masked settings saves and generic exports must never expose plaintext keys.

## Tasks

1. Add fixture tests for merging five sources, no Mitu calls, ChKSz stable IDs, repeated resolution cache hits, credential rotation, and blocked provider isolation. Run tests to establish failures.
2. Implement MusicSettingsService and admin endpoints with masked responses and sealed values. Add the dashboard music tab and password fields; save retains masked keys and clears empty keys.
3. Implement MusicApiClient with bounded success caches, serialized ChKSz calls, 20/minute budget and provider cooldowns. Adapt MusicService for QQ/网易云/酷狗, normalize metadata and preserve proxy validation.
4. Remove obsolete test proxy code/styles and update standalone test page to exercise the deployed backend without handling credentials.
5. Run Maven suite, JavaScript tests, dashboard build and diff checks. Review changes, commit and push main, upload tracked source to /www/wwwroot/blog, build services and verify health/search/resolve/cover/Range plus masked admin settings.

## Execution record

- Backend suite: 89 tests, zero failures/errors. Includes five-source merge, both NetEase response shapes, quota/rate-limit isolation, key rotation, masked credentials, optional lyric omission during audio resolution, and independent provider locks.
- Player fallback tests: 2/2 passed.
- Production Docker builds: dashboard and Nuxt completed. Container frontend tests 27/27; dashboard tests 16/16. The frontend tests require the repository's miniprogram directory mounted into the test container; this directory is intentionally absent from the production image.
- Local npm dependency directories were already incomplete; local full npm tests could not start. Validation used the complete production build dependencies in temporary containers.
- Independent review identified response-shape support, endpoint isolation, locking, Retry-After and diagnostic exposure issues. All were addressed with regression coverage; backend suite reran green.
