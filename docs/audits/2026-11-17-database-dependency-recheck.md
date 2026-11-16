# 数据库与后端依赖复核

日期：2026-11-17，Asia/Shanghai。本记录补充初次审查，范围仅数据库角色脚本、低权限应用/JDBC 备份的权限可行性及后端 Maven 依赖。复核者未运行 Maven、未接触 SSH、未变更生产数据库；依赖扫描读取主任务生成的最终 jar。构建测试和部署结果由主任务验证。

## 数据库角色和备份

在已有 V17 数据的隔离 PostgreSQL 17.10 数据库 `runtime_test` 中，通过本地测试隧道执行了角色脚本的等价 SQL，并在一个事务内用 `SET ROLE` 实际验证：

- 业务账号能够读取文章和 Flyway 历史表，具有业务 UPDATE 权限，不能在 public 创建表，不能 INSERT/UPDATE/DELETE 已存在的 Flyway 历史表。
- 迁移账号拥有 public 表、序列、视图，能创建含 identity 序列的新表；默认权限将新业务表 DML 授予业务账号。
- 备份账号继承迁移 owner 后能创建隔离 schema、`LIKE public.article INCLUDING ALL` 克隆、添加并修改外键、写入并验证约束、TRUNCATE 克隆表以及 ALTER owner 所有的业务表。这覆盖 `JdbcBackupSnapshotService` 的核心 DDL 权限要求。
- 验证事务全部回滚，复查无测试角色和 schema 残留。

上述结果证明脚本权限满足现有升级路径和 JDBC 备份所需 SQL，不等同于使用低权限账号完成全部 Spring 启动或执行整库正式恢复。此前真实 Spring/JPA 测试验证了 28 个 repository、18 项 Flyway 校验、V17 schema 和六项业务集成行为；它使用隔离测试账号，不应被描述为低权限生产启动验证。

发现并记录了全新安装的权限时序缺口：init hook 在 Flyway 历史表创建前设置默认业务表 DML，后来创建的历史表也会继承写权限。运维手册已要求首次迁移后、开放流量前停止 backend 并再次执行角色配置，随后验证历史表只读。已有历史表的 V12→V17 升级在第一次角色配置时即可撤销 DML。没有修改 Flyway 历史记录，也未新增依赖动态用户名的迁移 callback。

## 后端依赖

对真实成功的 Spring Boot 3.5.16 JPA 测试 classpath 中 145 个 Maven 坐标在线查询 OSV，包含测试依赖。查询发现三个包、十项公告：Tomcat embedded core 10.1.55 七项、PostgreSQL JDBC 42.7.11 一项、Spring MVC 6.2.19 两项；不能将旧扫描或升级 POM 本身称为“零漏洞”。

主任务已在 POM 追加 `tomcat.version=10.1.59` 和 `postgresql.version=42.7.12`。两个版本均经官方 Maven Central 返回 HTTP 200 确认存在，分别覆盖上述 Tomcat 和 JDBC 公告的修复范围。其他明确覆盖为 Jackson 2.21.8、Netty 4.1.141.Final、Logback 1.5.38、Log4j 2.25.5、HttpClient 5.6.5、HttpCore 5.4.4。Boot 3.5.16 默认 Spring Data 为 3.5.13。

最终 jar 于 2026-11-17 03:17 +08:00 再次在线查询 OSV：108 个 `BOOT-INF/lib` jar 共识别 116 个 Maven 坐标（含依赖内部 Maven 元数据列出的组件），所有 jar 均有坐标映射；仅 `org.springframework:spring-webmvc:6.2.19` 命中下表两项公告。实际产物已包含 Tomcat 10.1.59 和 PostgreSQL JDBC 42.7.12，两者此次未命中 OSV。被扫描 jar 的 SHA-256 为 `6db2f517891971b2e75c8b0963e7e70331e08a84bfd7d0f1fdd2cccd4d917a41`；后续重新构建的不同产物需要重扫，不能直接沿用此结果。原始证据保存在本地 `.runtime/maven-osv-final-jar.json`，该文件不作为已提交的公开报告附件。

目前 Spring MVC 6.2.19 的残余公告：

| 公告 | 前置条件 | 本项目检查结果 |
|---|---|---|
| [GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8) / CVE-2026-47890 | SSE 使用 view fragments 时可能发生流内容污染 | 未发现 SseEmitter、ServerSentEvent、FragmentsRendering 或 text/event-stream 入口 |
| [GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg) / CVE-2026-47884 | XsltView，且存在未指定 view name 的 `/**` 渲染映射，可导致 SSRF/RCE | 项目使用 REST 控制器，未发现 XsltView、ViewResolver 或上述渲染映射 |

OSV 将两项标为 CRITICAL；对当前功能的源码检索未发现必要前置条件，不等同于依赖已修复。官方 Maven Central 的 6.2 系列最高为 6.2.19，6.2.20 返回 404；公告列出的 7.0.9 修复涉及 Spring 主版本升级，不能直接在 Boot 3.5 中盲目覆盖。保留两项残余记录，并在未来加入 SSE fragment 或 XSLT 渲染前重新评估。此核查不包含 JDK、容器 OS 包和最终镜像漏洞扫描。
