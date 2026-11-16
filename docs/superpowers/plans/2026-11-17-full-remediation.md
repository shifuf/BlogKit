# BlogKit 与统一文件管理全面整改 Implementation Plan

> For agentic workers: use test-driven-development and systematic-debugging for implementation, dispatching-parallel-agents only for disjoint owned files, verification-before-completion and independent review before publication.

**Goal:** 修复评论回复上下文并完成审查所有条目和建议功能，统一新版文件接口，验证后推送两个GitHub仓库并分别部署指定服务器目录。
**Architecture:** 保留 Spring Boot/Nuxt/Vue 与 NestJS/Prisma/Vue；数据库使用增量迁移，保留业务数据，权限决策统一后端。异步通知使用数据库持久任务，内容备份与灾备分开。文件接口仅新HMAC v2契约。
**Spec:** docs/audits/2026-11-17-full-review.md + 用户本轮明确授权全面实现、推送、部署、安装文件存储插件。

## Global Constraints
- 远程文件写入仅 /www/wwwroot/blog 与 /www/wwwroot/unified-file-manager；保留.env、数据库、存储文件、密钥及现有插件配置。
- 不将用户提供的服务器密码或任何真实密钥写入源码、日志、提交；凭据仅运行时输入。
- 先备份后迁移；不清数据库、不删除卷；旧接口不兼容，但已有数据需迁移和保留。
- 本地隔离工作区：blog-full-remediation/博客系统，ufm-blog-integration/统一文件管理。原目录未提交内容不覆盖。
- 使用新分支，最后审核并推送/合入main（用户已授权），服务器只更新这两个项目。
- 对关键业务行为先写失败测试再修复；每组完成定向及全套测试，最后构建、接口和浏览器验收。

## Review Focus
1. 多级回复在审核、删除、权限变化后仍有正确上下文和数量。
2. 受限正文/评论/下载/摘要/搜索/订阅在所有入口权限一致。
3. 新库恢复与现有生产数据库升级保留权限、关联、文件及密钥。
4. 多标签/设备并发和网络失败不覆盖数据、不串用户、不阻塞发布。
5. 新文件接口签名、相对地址、插件安装、重启和回滚在真实环境一致。

## Task 1 评论链与互动正确性
Owner: comments worker. Files: CommentService, ArticleCommentRepository, ArticleComment+DTO, frontend CommentSection+types, dashboard Message, miniprogram detail comment render, dedicated migration V2__comment_reply_context.sql.
- [ ] 复现当前回复上下文丢失/计数/第三层不可见/举报越权；为状态变化、管理员回复、删除写回归。
- [ ] 用root parent保持平铺，另保存replyTo上下文；迁移已有深层回复；计数数据库原子更新或按聚合结果返回；所有读取/举报/点赞复用访问权限；回复分页。
- [ ] 前后台小程序显示回复给谁/引用片段，待审核与缺失父评论有明确呈现；定向测试+全套mvn测试。

## Task 2 身份安全与账号恢复
Owner: identity worker. Files: AuthService/Controller, TotpService, LoginSessionService, AdminUserService, user entities/repos/dtos, related frontend security UI, migration V3__account_security.sql.
- [ ] 为重置TOTP、会话复用、OAuth绑定state重放、密码恢复与恢复码写失败测试。
- [ ] 单设备独立token、Redis会话、OAuth state绑定会话和一次消费、绑定再认证；TOTP pending secret/旧码保护/恢复码；替换固定重置密码；邮箱验证和找回密码、前台安全页。
- [ ] 所有登录方式记录会话，失效状态准确；回归验证安全矩阵。

## Task 3 文件管理新版契约与插件
Owner: UFM worker; 独占文件管理worktree，博客仅UnifiedFileManagerClient/FileStorageService及测试协商。
- [ ] 检查HMAC v2、上传列表删除/访问URL/公开授权/缩略图，保留原文本编辑提交。
- [ ] 添加契约测试，修正双方对接，只支持新API；插件构建安装脚本幂等和安全；检查文件管理构建测试及依赖风险。
- [ ] 给root部署命令和接口验收脚本；不自行推送/部署，不调用真实邮件/云删除。

## Task 4 内容权限与后端性能
Owner: root then dedicated worker. Files: SecretBlockFilter, ArticleQuery/Command, DownloadService, SystemManagementService, repositories, OutboundUrlValidator, search/visitor/ops, migration V4__content_integrity.sql.
- [ ] 隐藏块解析、公开摘要/搜索/通知、下载撤权、公开抓取禁止私网+限流限字节数先测试。
- [ ] 动态真分页+批量互动、动态删除；收藏真分页、搜索索引对齐/upsert统计隔离、访客异步缓存、计数与业务字段分离、条件缓存失效。
- [ ] HTTP管理面权限、异常追踪ID、可信代理、准确指标、资源上限及依赖升级。

## Task 5 灾备与可靠通知
Owner: root. Files: SystemBackupService, Backup DTO/controller/dashboard ImportExport, notification outbox, EmailSubscriptionService/controller, ScheduledArticlePublisher, docker config, migration V5__notification_outbox.sql.
- [ ] 权限恢复与备份完整性失败测试；完整版本化备份、恢复真实schema演练、持久卷、流式加密、保留策略、异地副本可配置。
- [ ] 发布事务写outbox，提交后有界投递/重试/幂等；首次直接发布和定时发布一致，邮件所有权双确认、通知偏好、失败状态。
- [ ] 数据库原生备份+uploads+安全密钥备份及回滚操作手册，备份失败通知。

## Task 6 前端闭环和全部增量功能
Owner: frontend worker (later). Files: Nuxt/PWA/markdown article, dashboard article editor, series pages, notification/profile/read-progress, miniprogram pages.
- [ ] SSR安全正文、准确404/5xx、局部loading、PWA公开缓存白名单/私有no-store/图标；升级依赖、合理拆包。
- [ ] 系列选择排序+系列目录导航；编辑版本冲突+diff；保留定时下架；发布前质量检查。
- [ ] 跨设备进度/已读稍后读（后端V6__reading_progress.sql），通知分页偏好；三端能力状态与隐藏块刷新。
- [ ] 小程序分类搜索版本号竞态和配置加载修复；浏览器端到端验证。

## Task 7 总体验证、审查、发布
Owner: root+fresh reviewer.
- [ ] 汇总每个审查ID及功能的代码/测试/部署状态，不遗漏条件风险；全套测试+lint/typecheck+构建+依赖扫描。
- [ ] 新旧生产schema升级演练、数据库/文件备份、服务器容量及进程盘点（仅两个目录业务操作）。
- [ ] 审查diff、无凭据扫描、commit、GitHub推送并同步main，保留原目录用户改动。
- [ ] 指定目录上传部署、保留.env和卷、应用增量迁移、安装五个存储插件，验收评论上下文/上传列表公开访问/下载/权限/重启健康。
- [ ] 报告准确提交SHA、URL、测试数量、备份路径、部署状态和任何外部配置限制。
