# BlogKit 全面审查报告

日期：2026-11-17（Asia/Shanghai）；代码基线：`8e8b2ca`。

本文件正文记录的是 `8e8b2ca` 初次审查快照，条目描述、旧依赖数量和“仅1条后端测试”等结果不代表整改后的当前状态。整改后数据库权限和后端依赖复核见 [数据库与后端依赖复核](2026-11-17-database-dependency-recheck.md)；各项实现和发布验收由最终整改记录另行确认，不能把初始发现直接当作仍未修复清单。

范围：Spring Boot 后端、Nuxt 前台、Vue 管理后台、微信小程序、数据库迁移、Docker 部署及依赖锁文件。本次未修改业务代码或生产数据。结论分为本地复现、代码链确认及条件风险；没有进行线上渗透、真实业务压测或外部 OAuth/邮件联调。性能上限推算不是实测吞吐量。

## 结论与优先级

系统已覆盖相当完整的博客功能。建议先修访问控制、灾备和账号安全，再补齐已有功能，最后按实测优化性能。

- **P1：尽快修复。** 隐藏正文过滤绕过、评论权限旁路、恢复后权限丢失、TOTP 重置绕过、公开 URL 抓取边界、备份不持久化、事务内邮件广播。
- **P2：随后处理。** 会话管理、固定重置密码、发布通知遗漏、回复计数/层级、动态删除、编辑丢字段、搜索/分页、SSR/404/PWA、小程序竞态。
- **P3：持续改善。** 监控准确性、保留策略、图标资源、代码可维护性和工程化。

P1/P2 是项目处理优先级，不是 CVSS 分数。依赖扫描等级另列。

## 已有能力

已有草稿、定时发布、回收站、版本差异与恢复、本地草稿自动保存、分类标签、搜索归档、RSS/sitemap、收藏、阅读进度条、目录、图库、音乐、文章访问限制、评论举报、动态互动、友链申请、邮件订阅、通知、AI 辅助、附件、备份、健康检查、日志、搜索统计、TOTP、设备会话和黑名单，不建议重复建设。

已有 BCrypt、后台角色拦截、改密撤销会话、图片检查、HTML 白名单、出站校验、限流、Redis 降级、主要计数原子 SQL、分页和索引。问题主要集中在入口间规则不一致、恢复和失效处理遗漏。

## 一、安全

### S01 / P1：隐藏内容合法语法可绕过服务端过滤

**本地最小复现。** 后端固定跳过三个冒号，只识别 `:::bd-secret`；前端接受三个及以上冒号。合法的四冒号容器不会被后端清空，前端却解析为隐藏块，并因正文非空而直接显示。未评论普通用户的三冒号样例被清空、四冒号样例保留；实际前端解析器也保留测试正文。

建议统一语法和解析规则；权限判断以服务端为准，覆盖嵌套、不同围栏长度、缩进和未闭合情况。

证据：[SecretBlockFilter.java:46](F:/博客系统/backend/src/main/java/com/blog/system/service/SecretBlockFilter.java:46)、[blog-markdown.ts:42](F:/博客系统/frontend/utils/blog-markdown.ts:42)、[SecretBlock.vue:14](F:/博客系统/frontend/components/blog/SecretBlock.vue:14)。

### S02 / P1：举报接口返回无权访问的文章评论

**离线服务层复现与 HTTP 代码链确认。** 公开 `POST /api/v1/comments/{id}/report` 只检查文章已发布、未删除、评论已审核，未复核文章可见性，最后返回完整 CommentResponse。测试中匿名 canViewContent=false，reportComment 仍返回测试私密评论正文。知道或猜到评论 ID 的游客可以借举报读取受限文章评论，同时污染举报数据。登录后的评论点赞也有类似遗漏。

建议读取、点赞、举报复用同一权限检查，举报只返回最小确认数据，补齐匿名/普通/授权用户/作者/管理员矩阵测试。

证据：[CommentService.java:711](F:/博客系统/backend/src/main/java/com/blog/system/service/CommentService.java:711)、[CommentController.java:123](F:/博客系统/backend/src/main/java/com/blog/system/web/controller/CommentController.java:123)、[WebConfig.java:89](F:/博客系统/backend/src/main/java/com/blog/system/common/config/WebConfig.java:89)。

### S03 / P1：重新初始化 TOTP 能绕过关闭验证

**离线复现。** setup 对已登录管理员直接替换密钥并设 `totpEnabled=false`，不验证旧动态码；disable 却要求验证。真实 setup 调用前 verify(null) 拒绝，调用后 verify(null) 接受。前提是已有管理员会话，不是匿名登录绕过。

建议区分首次启用与更换设备，后者要求近期重新认证和旧 TOTP/恢复码；新密钥先待确认，成功后再替换。

证据：[TotpService.java:114](F:/博客系统/backend/src/main/java/com/blog/system/service/TotpService.java:114)。

### S04 / P1：公开友链抓取复用允许本机的出站策略

**代码链确认，依赖配置。** BLOG_SITE_URL 非空时，友链申请触发回链抓取，校验器默认允许 localhost/127.0.0.1/::1，使游客通过现有验证码和限流后能让后端向本机 HTTP 服务发 GET。属于盲 SSRF/内网探测，不是直接返回内网原始响应。站点地址为空时函数提前返回，不抓取。

fetchText 还先用 `BodyHandlers.ofString()` 完整收响应，之后才截断字符串，不能限制超大响应的堆占用。验证码是 SVG 算式，可程序读取计算，不能作为强自动化防护。

建议公开抓取独立禁止私网，可信集成独立白名单；固定解析结果、校验所有目标、流式限字节数和总耗时。

证据：[SystemManagementService.java:297](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemManagementService.java:297)、[347](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemManagementService.java:347)、[OutboundUrlValidator.java:23](F:/博客系统/backend/src/main/java/com/blog/system/common/util/OutboundUrlValidator.java:23)。

### S05 / P2：共享 token 使多设备撤销失效

**本地 Sa-Token 内存验证及代码链。** is-concurrent=true、is-share=true，默认登录无设备区分；同用户连续登录得到同一 token、活动 token 数为 1。会话表按 token hash 覆盖同一行，revokeOthers 又排除当前共享 token。因此其他浏览器无法独立撤销。

建议每设备独立 token/会话 ID，统一 OAuth/微信会话记录。项目使用 Redis 缓存，但未接入 Sa-Token Redis DAO，多实例和重启持久性还需补齐。

证据：[application.yml:64](F:/博客系统/backend/src/main/resources/application.yml:64)、[LoginSessionService.java:16](F:/博客系统/backend/src/main/java/com/blog/system/service/LoginSessionService.java:16)、[AuthService.java:326](F:/博客系统/backend/src/main/java/com/blog/system/service/AuthService.java:326)。

### S06 / P2：后台重置密码固定为 123456

后台默认按钮不传密码，服务端回退为 123456，且没有强制改密标志；提示用户自行修改不能消除窗口。已有重置后撤销会话，应保留。

建议一次性重置链接，或随机短期临时密码配合强制改密。

证据：[AdminUserService.java:98](F:/博客系统/backend/src/main/java/com/blog/system/service/AdminUserService.java:98)、[后台按钮:254](F:/博客系统/dashboard/src/views/User/index.vue:254)。

### S07 / P2：公开摘要、搜索和订阅通知未统一内容权限

未填摘要时从正文生成约 140 字摘要，列表无条件返回。受限文章勾选公开列表时，可能暴露受限正文片段。标准自定义隐藏块已在摘要清洗中移除，不将其泛报为自动摘要泄漏。全文搜索仍查询列出的受限正文，形成关键词存在性探测；发布通知未按 visibility/listedInPublic 筛选，受限/未列出文章也可能广播标题和链接。

建议把允许公开的导语与受限正文分开，摘要从公开内容生成；搜索、列表、推荐、feed、通知统一公开策略。刻意公开的导语不算泄漏。

证据：[摘要生成:963](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleCommandService.java:963)、[列表:319](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleQueryService.java:319)、[搜索:189](F:/博客系统/backend/src/main/java/com/blog/system/repository/ArticleRepository.java:189)、[通知:935](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleCommandService.java:935)。

### S08 / P2：需按环境确认的其他边界

- **OAuth state：** 有签名和有效期，但未绑定浏览器、未单次消费；绑定回调按 state 用户 ID 绑定并发会话，未复核当前登录。应补会话绑定、原子消费及再认证。登录 CSRF/绑定流程需隔离联调，未声称任意账号已可接管。[AuthService.java:182](F:/博客系统/backend/src/main/java/com/blog/system/service/AuthService.java:182)、[560](F:/博客系统/backend/src/main/java/com/blog/system/service/AuthService.java:560)。
- **下载撤权：** 已有密封 token 只检查 login/comment 门槛，未复核文章下架、私密、删除，也无有效期。建议资源 ID+到期时间+权限版本，每次兑换复核；无法撤回已交出的第三方直链。[DownloadService.java:58](F:/博客系统/backend/src/main/java/com/blog/system/service/DownloadService.java:58)。
- **IP 信任：** 仅按 XFF 跳数、不校验来源代理，且最少一跳；Compose 3256 绑定所有网卡。若公网可绕 Nginx 直连，伪造头可能规避 IP 限流。限制入口并统一可信代理 CIDR；会话日志另直接取首项也需统一。[ClientIpResolver.java:30](F:/博客系统/backend/src/main/java/com/blog/system/common/util/ClientIpResolver.java:30)、[docker-compose.yml:116](F:/博客系统/docker-compose.yml:116)。
- **Actuator：** metrics/prometheus/caches 无对应 Sa-Token 拦截。Compose 默认未公开后端端口，不能写成默认公网暴露；独立部署或内网可达时需认证/管理端口/网络限制。[application.yml:133](F:/博客系统/backend/src/main/resources/application.yml:133)、[WebConfig.java:68](F:/博客系统/backend/src/main/java/com/blog/system/common/config/WebConfig.java:68)。
- **异常详情：** 数据完整性异常原 message 截取后发客户端，可能含 SQL、字段和冲突数据。返回错误码与追踪 ID，详情留日志。[GlobalExceptionHandler.java:60](F:/博客系统/backend/src/main/java/com/blog/system/common/exception/GlobalExceptionHandler.java:60)。
- **纵深防御：** 前台持久化实际为 JS 可读 cookie，后台 token 为 localStorage；CSP 允许内联脚本、后台还允许 eval。可渐进改服务端会话/HttpOnly cookie，并同步 CSRF 与 nonce CSP。没有将这些配置直接判定为已存在 XSS。

## 二、灾备和功能正确性

### R01 / P1：恢复受限文章会丢失权限

**离线复现。** 导出有正文和发布状态，没有 visibility、密码哈希、允许用户和 listedInPublic。调用真实导出/导入方法、使用内存模拟仓库后，测试已发布 PRIVATE 文章变为 PUBLIC、listedInPublic=true，允许用户数从1变0、密码哈希丢失而正文保留。新库恢复会公开此类受限文章。

建议版本化备份，完整恢复权限和关联；旧包缺权限字段默认受限，必须验证“导出→空库恢复→匿名不可读取”。

证据：[导出:576](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemBackupService.java:576)、[导入:442](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemBackupService.java:442)、[默认值:126](F:/博客系统/backend/src/main/java/com/blog/system/domain/article/Article.java:126)。

### R02 / P1：现有备份不能当完整灾备

- 写入 `/app/backups`，Compose 仅挂 uploads 卷，重建后端容器会丢容器内备份。
- JSON 只含设置、分类、标签、文章、动态、友链，缺用户、评论、订阅者、系列、版本和互动关系；上传文件另行备份已有说明。
- 演练仅向临时 schema 通用 JSONB 表写入并数条数，不验证真实迁移、外键、权限和实际导入路径。
- 全量实体、Map/List、JSON 字节、密文字节同时占堆；每日生成没有保留清理。

建议数据库一致性备份+uploads+独立保管密钥，备份持久卷及异地副本、保留期、恢复目标；隔离数据库运行真实恢复。当前 JSON 更适合作内容迁移包。

证据：[内容:132](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemBackupService.java:132)、[演练:224](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemBackupService.java:224)、[卷:84](F:/博客系统/docker-compose.yml:84)。

### R03 / P1：事务内串行邮件广播阻塞发布和下架

事务内全量查订阅者逐个发邮件，单次外部调用可等 20 秒，失败还有模板回退。规模和故障叠加会长期占连接/锁；定时任务先广播后下架，通知故障拖延下架。邮件可能先发出而数据库随后回滚。

建议同事务写待发送事件，提交后有界队列分页投递，幂等、重试、失败记录；发布/下架事务只做数据库操作。

证据：[发布:929](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleCommandService.java:929)、[广播:109](F:/博客系统/backend/src/main/java/com/blog/system/service/EmailSubscriptionService.java:109)、[定时任务:40](F:/博客系统/backend/src/main/java/com/blog/system/service/ScheduledArticlePublisher.java:40)。

### 其他已确认缺陷

| 编号/优先级 | 问题与影响 | 证据 | 建议 |
|---|---|---|---|
| R04 / P2 | 新建直接发布先通知后保存，ID 为空提前返回，订阅邮件遗漏 | [ArticleCommandService:434](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleCommandService.java:434)、[936](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleCommandService.java:936) | 统一提交后的发布事件 |
| R05 / P2 | 原子更新清持久化上下文，随后修改脱管 parent.replyCount 未保存 | [CommentService:193](F:/博客系统/backend/src/main/java/com/blog/system/service/CommentService.java:193)、[ArticleRepository:569](F:/博客系统/backend/src/main/java/com/blog/system/repository/ArticleRepository.java:569) | 父回复数原子更新，验证增删审核路径 |
| R06 / P2 | 管理员回复二级评论产生前台未查询的三级评论，单层删除也会外键失败 | [CommentService:645](F:/博客系统/backend/src/main/java/com/blog/system/service/CommentService.java:645)、[普通回复归一:778](F:/博客系统/backend/src/main/java/com/blog/system/service/CommentService.java:778) | 两入口共用根评论解析，修已有数据 |
| R07 / P2 | 有点赞/评论的动态直接删除，被无级联外键阻止 | [SystemManagementService:165](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemManagementService.java:165)、[迁移:483](F:/博客系统/backend/src/main/resources/db/migration/V1__init.sql:483) | 明确级联语义并事务内处理子数据 |
| R08 / P2 | 编辑器不传 seriesId/seriesOrder/scheduledUnpublishAt，后端无条件覆盖，普通保存会清已有设置 | [edit.vue:1018](F:/博客系统/dashboard/src/views/Article/edit.vue:1018)、[ArticleCommandService:815](F:/博客系统/backend/src/main/java/com/blog/system/service/ArticleCommandService.java:815) | UI 完整载入回传，或区分未提供和清空 |
| R09 / P2 | 登录/评论后只改本地标志，未重新获取服务端已过滤的隐藏正文，出现空白 | [文章页:321](F:/博客系统/frontend/pages/article/[slug].vue:321)、[448](F:/博客系统/frontend/pages/article/[slug].vue:448) | 返回能力状态，登录/审核后重取正文，区分待审 |
| R10 / P2 | 搜索统计查后插入、读后加一，并发冲突/丢计数可使成功搜索返回失败 | [SearchAnalyticsService:32](F:/博客系统/backend/src/main/java/com/blog/system/service/SearchAnalyticsService.java:32)、[ArticleController:66](F:/博客系统/backend/src/main/java/com/blog/system/web/controller/ArticleController.java:66) | 原子 upsert，统计失败不阻断搜索 |

并发一致性还需集成验证：Article 无乐观版本，普通实体保存可能以旧值覆盖同时发生的原子浏览/点赞计数；建议业务版本保护，计数独立更新或拆表。

## 三、性能

| 优先级 | 具体瓶颈 | 处理方式与验收 |
|---|---|---|
| P1 | 事务内邮件广播及公开抓取无限收响应 | 见 R03/S04；控制连接占用、队列和字节上限 |
| P2 | 动态接口全量返回，前台仅切片展示5条，却对全部动态请求点赞状态/位置查询 | 服务端游标分页+批量互动状态，可见条目才补位置，限制并发；[后端:141](F:/博客系统/backend/src/main/java/com/blog/system/service/SystemManagementService.java:141)、[前台:639](F:/博客系统/frontend/pages/dynamics.vue:639) |
| P2 | 收藏分页 EntityGraph 包含 article.tags 集合，Hibernate 会退到内存分页 | 先分页 ID 或只抓 to-one，标签批量取；启用 fail_on_pagination_over_collection_fetch 检查；[ArticleFavoriteRepository:23](F:/博客系统/backend/src/main/java/com/blog/system/repository/ArticleFavoriteRepository.java:23) |
| P2 | trigram 索引建 lower(column)，实际查询原列 ILIKE，表达式不匹配；正文/分类/标签 OR 增加扫描 | 对齐查询和索引，用真实中文、短词、无结果执行 EXPLAIN ANALYZE；[索引:593](F:/博客系统/backend/src/main/resources/db/migration/V1__init.sql:593)、[查询:191](F:/博客系统/backend/src/main/java/com/blog/system/repository/ArticleRepository.java:191) |
| P2 | IP 列表只读事务内逐个2秒外查归属地、逐个查黑名单，失败无负缓存；100条超时可累计约200秒 | 短事务取数据、批量查黑名单、事务外限并发补位置、失败短TTL；[VisitorIpStatService:57](F:/博客系统/backend/src/main/java/com/blog/system/service/VisitorIpStatService.java:57)、[VisitorLocationService:73](F:/博客系统/backend/src/main/java/com/blog/system/service/VisitorLocationService.java:73) |
| P2 | 评论只分页根节点，回复全部加载；一个根评论可返回大量回复 | 回复独立分页/预览数量；[CommentService:99](F:/博客系统/backend/src/main/java/com/blog/system/service/CommentService.java:99) |
| P2 | 定时任务每分钟无条件清文章缓存，即使无发布/下架 | 有状态变更再失效；[ScheduledArticlePublisher:34](F:/博客系统/backend/src/main/java/com/blog/system/service/ScheduledArticlePublisher.java:34) |
| P2 | Redis 容器128MB但无 maxmemory，增长可能触发容器OOM；后端/前台又无容器内存限制 | 明确全机预算、Redis保留余量和淘汰/隔离策略。限流安全数据不应被随意淘汰；[Compose:32](F:/博客系统/docker-compose.yml:32) |

普通文章列表已有 to-one 抓取、标签批量读取，搜索也有先分页 ID 的处理，不应泛称全部列表都有 N+1。容量目标需通过真实数据规模下的 P50/P95、查询数、响应大小、堆、连接池等待和缓存命中率确定。

## 四、前端、SEO、PWA 和小程序

### F01 / P2：正文没有 SSR，加载遮罩进一步推迟可读内容

文章正文包在 ClientOnly，服务端仅骨架；动态 Markdown 也是 ClientOnly。已有 metadata/JSON-LD 不等于正文 SEO 完成。全屏 AppLoading 默认显示，移除和兜底依赖 JS mounted，脚本失败可能一直遮挡。

建议服务端渲染统一清洗后的 Markdown，播放器/图库作客户端增强；骨架只覆盖未就绪区域。

证据：[文章页:646](F:/博客系统/frontend/pages/article/[slug].vue:646)、[MarkdownPreview:46](F:/博客系统/frontend/components/blog/MarkdownPreview.vue:46)、[AppLoading:4](F:/博客系统/frontend/components/AppLoading.vue:4)。

### F02 / P2：404和接口故障被转成200空文章

详情请求经 Promise.allSettled 拒绝后变 null，最终“文章不存在或未发布”，没有正确页面错误状态。应区分404与网络/5xx，前者返回真正404，后者重试；推荐失败可独立降级。

证据：[文章页:88](F:/博客系统/frontend/pages/article/[slug].vue:88)。

### F03 / P2：PWA 模块初始化和运行时规则有两处错误

**生产产物及真实 Workbox bundle 的离线复现。** 174条 precache 没有 `/`、没有HTML，但产物先执行 `createHandlerBoundToURL("/")`，抛出 `non-precached-url :: [{"url":"/"}]`。模块初始化中断，导航回退与后续运行时策略均未注册。适合当前SSR设计的修正是显式 `navigateFallback: null`。因AMD loader将模块放Promise执行，且此前预缓存监听已注册，本轮不据此断言浏览器整个SW安装必然失败，最终注册状态仍需浏览器验证。

修正上述初始化后，还存在第二处错误：urlPattern 函数捕获 adminBasePath，Workbox 将函数源码独立序列化，不带外层变量；在 SW 独立环境匹配 /profile 得到 `ReferenceError: adminBasePath is not defined`。生成产物也确实保留此变量。

建议构建时生成正则或使用自包含 matcher，检查最终 SW 并测试更新/离线。

**关联的敏感缓存配置风险：** 后续 NetworkFirst pages 规则允许 profile/preview 等导航，未隔离用户和清理退出前缓存，且 SSR Pinia payload 含 token。当前上述初始化和 matcher 错误阻断这些策略，不能声称本轮已利用缓存泄漏。修复时必须同步将私有/预览导航设 NetworkOnly、公开页面严格白名单，并清理历史缓存。

证据：[规则:293](F:/博客系统/frontend/nuxt.config.ts:293)、[页面缓存:319](F:/博客系统/frontend/nuxt.config.ts:319)、[个人页SSR:521](F:/博客系统/frontend/pages/profile.vue:521)。

另：生产产物中三个 manifest 引用的 pwa-*.png 均不存在，应补图标资源。Nuxt 配置还重复声明两次 app 属性，后者覆盖前者，建议合并。

### F04–F06：交互缺口

- **P2 通知仅20条：** page 永为0，total未用于分页，没有加载更多；应补分页、未读过滤、失败重试。[notifications.vue:3](F:/博客系统/frontend/pages/notifications.vue:3)。
- **P2 小程序分类/搜索竞态（已本地模拟复现）：** 切换到B时旧A请求仍loading，新请求被guard丢弃，A响应写进B页面。应请求序号校验/取消旧请求；Web分类页已有可借鉴模式。[category.js:33](F:/博客系统/miniprogram/pages/category/category.js:33)、[search.js:32](F:/博客系统/miniprogram/pages/search/search.js:32)。
- **P3 小程序个人页首次评论缺失（已本地模拟复现）：** 配置和dashboard并发，初始commentsEnabled=false导致跳过，设置到达后不补拉；应等待配置或监听能力变化。[me.js:20](F:/博客系统/miniprogram/pages/me/me.js:20)。

已排除疑点：当前 `/dynamics` Nitro SWR 没有传 Cookie 给缓存渲染，不能将其报告为用户 token 串号。没有把所有 v-html 当成 XSS；正文、评论和页脚已存在白名单清洗。

## 五、依赖与运维

### 依赖扫描

执行 `npm audit --package-lock-only --omit=dev --ignore-scripts --json`，初次结果：

| 包 | 依赖项告警总数 | critical | high | moderate | low |
|---|---:|---:|---:|---:|---:|
| frontend | 39 | 6 | 16 | 13 | 4 |
| dashboard | 12 | 0 | 11 | 1 | 0 |

这是被标记依赖项数量，含传递影响，不是相同数量的独立可利用漏洞。omit=dev 也会保留以生产依赖引入的构建/开发工具；不能将所有 critical 当生产远程执行。当前锁定 Nuxt4.4.6、Vue3.5.34、md-editor-v3 6.5.1、markdown-it14.2.0、后台axios1.16.1。

应优先核对生产 Nuxt/Nitro、Markdown 渲染及 Vue SSR；DevTools/Vite 告警需与开发服务暴露面区分，后台静态SPA中的 Node axios/SSR 告警也需判断可达性。升级锁文件、回归构建与权限后重扫，不建议盲目 audit fix --force。

参考：[md-editor-v3 XSS公告](https://github.com/advisories/GHSA-3rm2-h79c-8qw6)、[Vue SSR公告](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6)、[Nuxt DevTools公告](https://github.com/advisories/GHSA-279x-mwfv-vcqv)。完整本次扫描保存在各包 `audit-dependencies.log`（Git忽略）。未完成 Maven CVE 或容器镜像扫描，不据此称后端依赖安全。

### 部署和可观测性

- Compose 健康探测指向私网 frontend，但 OutboundUrlValidator 默认白名单不含 frontend，默认容器网络下 RSS/sitemap 体检可能被自身策略拒绝。专用可信探针应配置独立允许目标，不要扩大游客抓取白名单。[SiteHealthService:111](F:/博客系统/backend/src/main/java/com/blog/system/service/SiteHealthService.java:111)、[Compose:78](F:/博客系统/docker-compose.yml:78)。
- Ops metrics 对多标签 timer/gauge 只取一个，HTTP总量/均值和JVM内存可能不代表总量；应按标签聚合、以总耗时/总次数计算，补P95和连接池等待。[AdminOpsController:42](F:/博客系统/backend/src/main/java/com/blog/system/web/controller/AdminOpsController.java:42)。
- PostgreSQL 默认关闭 seccomp/AppArmor 应只作为个别宿主兼容选项；镜像改非root运行，固定可追溯版本/摘要，补 backend/frontend/redis 健康与就绪检查。[Compose:14](F:/博客系统/docker-compose.yml:14)。
- 运维数据库账号使用 POSTGRES_USER，官方镜像创建的是高权限初始用户。建议迁移账号与业务账号拆分，业务仅必要权限；备份演练DDL另用受控权限。
- V1 单一初始化迁移且注释说明已折叠历史迁移，适合新安装；已经上线实例的升级需保留已执行迁移并新增V2+，避免 checksum/baseline 不匹配。不能只用 ddl-auto=update 掩盖迁移缺口。
- 统一 npm/pnpm 锁文件和CI；补测试、类型检查、lint、依赖/镜像扫描。当前只有1条后端测试，Docker构建跳过测试，关键权限/恢复/并发缺少回归保护。

## 六、值得补充的功能

| 顺序 | 功能增量 | 现有基础与价值 |
|---|---|---|
| 1 | 系列阅读完整闭环 | 已有系列CRUD/API；补编辑器系列选择排序、系列页目录和导航，同时修R08 |
| 2 | 邮箱验证、密码找回、TOTP恢复码 | 补账号恢复链路；普通用户个人页复用已有设备管理API，先修S05/S06 |
| 3 | 订阅双重确认、退信/失败重试、通知偏好 | 当前subscribe直接confirmed=true，缺邮箱所有权验证；已有订阅和通知系统可扩展 |
| 4 | 并发编辑冲突提示 | 已有版本与diff；加乐观版本号和409提示，防多标签最后写覆盖 |
| 5 | 跨设备继续阅读、已读/稍后读状态 | 已有收藏及阅读条；补进度持久化、完成时间和筛选 |
| 6 | 内容发布前质量检查 | 已有链接检查/SEO基础；聚合封面、公开摘要、alt、失效链接、系列和下架检查 |
| 7 | 可靠运维闭环 | 备份失败通知、异地副本、真实恢复演练、投递任务状态；先让既有健康数据可信 |

不建议当前优先引入微服务、Elasticsearch或大规模AI推荐。先修现有PostgreSQL搜索、分页和缓存，再以实际规模决定。

## 七、验收建议与本次验证

第一批验收：四冒号隐藏块未授权不返回正文；举报/点赞不能读取受限评论；恢复后权限保持；TOTP更换要求旧认证；游客抓取禁止私网；备份容器重建仍存在。

第二批验收：两个浏览器有独立会话并可撤销；新建/草稿/定时发布各投递一次；订阅服务不可用不阻塞发布；回复数与层级正确、有互动动态可删；编辑标题不清系列/下架时间。

第三批验收：页面首屏请求量随page size而非总量增长；收藏SQL真分页、搜索实际命中索引；正文SSR可读、404正确；SW无ReferenceError且私有页不缓存；小程序快速切换不串结果。

本次验证结果：

- 后端 `mvn -B -ntp test`：通过，1条测试、0失败。
- 前台 `npm run build`：退出0，Nuxt生产构建完成。
- 管理后台 `npm run build`：退出0，Vite生产构建完成。
- 两个前端独立构建通过，不等于已验证 Docker 联合打包/线上端到端；本轮未运行容器。
- 产物存在大块：前台约861/992 kB，后台 Markdown约833 kB、Ant Design约1060 kB、vendor约1100 kB（均为压缩前文件大小，gzip显著更小）。需分析具体路由加载路径，不能把全部产物总量直接称首屏下载量。日志在各包 build-audit.log。
- 依赖锁文件扫描：frontend39项、dashboard12项，等级如上。
- 离线执行实际方法复现备份权限丢失、TOTP重新初始化绕过、受限评论举报旁路；源码与输出位于 [OfflineAuditProbe.java](F:/博客系统/backend/target/audit-offline/OfflineAuditProbe.java)、[output.txt](F:/博客系统/backend/target/audit-offline/output.txt)，仓库/当前用户替身，没有数据库或网络请求。
- 其他最小复现覆盖隐藏块解析、Sa-Token会话复用、Workbox规则、小程序分类/搜索竞态和首次个人页评论遗漏。
- 最终 SW 产物的首页回退触发 non-precached-url，并保留未定义 adminBasePath；三个 PWA 图标也未生成。

未启动连接真实数据库的业务服务，因此没有线上端到端、SQL执行计划、压测或真实恢复演练结果。前端类型检查不等于构建，本项目没有独立 typecheck/lint 流水线，本轮未声称这些通过。
