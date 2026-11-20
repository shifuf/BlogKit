# Mobile and editor reliability

Continue the user's eight reported issues from chat 01a1f4b5-5301-7493-bd36-56db800e893b. Preserve existing Vue/Ant Design/Nuxt architecture and UI conventions.

- Follow the phone's system dark appearance by default; explicit theme selection wins. Adapt actual white surfaces and unreadable text, including controls.
- Admin music insertion and preview should present cover, song and artist; internal resource IDs remain implementation details.
- Music seeking and lyric navigation must recover from expired upstream URLs after hours, preserving requested playback position and paused intent.
- Markdown toolbar adds password and specified-user protected sections alongside existing login/comment visibility. Enforce access on server, never expose locked body/password in public API, feeds or summaries. Add current Beijing time insertion at cursor.
- Mobile sidebar must close reliably after navigation and not leave a floating article submenu. Re-selecting current route must not recreate editor. Tab overflow opens by click, remains usable, and closing an inactive tab preserves current route.
- Category and tag names must be visible at 320px without opening edit dialogs.
- Homepage author introduction uses the configured background image, without inventing an external asset.
- Footer registration/copyright text remains visible around floating controls and safe area.

Validation: regression tests, full backend/frontend/dashboard suites, production builds, browser checks at mobile/desktop widths and light/dark appearance. Continue previously authorized GitHub and production delivery only within /www/wwwroot/blog, preserving credentials, volumes and unrelated services.
