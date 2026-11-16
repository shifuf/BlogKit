# UI regression fixes

Goal: fix the four reported regressions without changing stored content or configuration.

Approach: retain the immersive article header and original logo geometry, derive white glyph transparency through an SVG display filter (no replacement bitmap); show the series navigation only when the public series API returns data; restore the missing admin reply state/action and protect pagination against stale requests; move article filters and batch actions from the constrained card-title slot into wrapping body toolbars.

- Reproduce missing reply bindings and submission behavior in a regression test; cover switching threads and pagination failures before implementing the fix.
- Fix the header/logo and conditional series navigation; verify empty and populated series responses.
- Make filters/actions wrap within card width and keep the data table horizontally scrollable. Check desktop, tablet and narrow mobile widths with real Ant Design components.
- Run both frontend test suites, lint/typecheck/build and browser acceptance, then commit/push and rebuild only the blog frontend container in /www/wwwroot/blog. Preserve current production data, configuration and file-manager services.

Existing isolated worktree is reused. Push/deployment authorization carries forward from the user's original request. No backend migration is required.
