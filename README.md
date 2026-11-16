# BlogKit

个人博客系统。文章与动态、评论互动、站点配置与备份同仓维护；前台 Nuxt 4 SSR，管理端 Vue 3，后端 Spring Boot 3，数据层 PostgreSQL / Redis。可选微信小程序。

## 目录结构

| 目录 | 说明 |
|------|------|
| `frontend/` | 公开站点；生产环境同时托管后台静态资源 |
| `dashboard/` | 管理后台 |
| `backend/` | 业务 API |
| `miniprogram/` | 微信小程序（可选） |
| `docker/` | 镜像构建与 Nginx 示例 |
| `docker-compose.yml` | 一键部署 |
| `.env.example` | 环境变量模板 |

## 功能

- 文章：草稿 / 发布 / 定时发布 / 系列 / 公开·未列出·私密
- Markdown 扩展块（下载、图库、按钮等）
- 动态时间线、分类标签、友情链接
- 评论（登录用户与游客）、点赞、收藏
- 可选 AI 摘要
- 附件、备份导入导出、健康检查、操作日志
- 后台路径可配置；Sa-Token 会话、管理员 TOTP、下载密封、CSP

## 环境要求

| 场景 | 依赖 |
|------|------|
| Docker 部署 | Docker Engine、Compose v2 |
| 本地开发 | JDK 17+、Node.js 22+、PostgreSQL 17、Redis 7 |

约 2GB 内存即可用仓库内 Compose 配置构建（已限制 Node 堆，默认关闭 brotli 预压缩）。

## 快速部署

```bash
git clone <仓库地址> blogkit
cd blogkit
cp .env.example .env
```

编辑 `.env`，至少改这些：

```env
DB_PASSWORD=<强密码>
BLOG_ADMIN_PASSWORD=<强密码>
BLOG_DOWNLOAD_SECRET_KEY=<随机串>
BLOG_API_PAYLOAD_SECRET=<随机串>
BLOG_BACKUP_ENCRYPTION_KEY=<至少 32 位随机串>
BLOG_SITE_URL=
```

生成密钥：

```bash
openssl rand -hex 32
```

启动：

```bash
docker compose up -d --build
```

| 入口 | 默认 |
|------|------|
| 前台 | `http://<主机>:3256` |
| 后台 | `http://<主机>:3256/admin` |
| 账号 | `admin` / `BLOG_ADMIN_PASSWORD` 的值 |

首次登录后请改管理员密码。

```bash
docker compose logs -f backend frontend
docker compose down
```

反代示例见 `docker/nginx.example.conf`。

## 本地开发

1. 复制 `.env.example` 为 `.env`：

```env
BLOG_ENV=dev
DB_PASSWORD=blog
BLOG_ADMIN_PASSWORD=change-me
BLOG_DOWNLOAD_SECRET_KEY=dev-download-secret
BLOG_API_PAYLOAD_SECRET=dev-payload-secret
BLOG_BACKUP_ENCRYPTION_KEY=dev-backup-secret-at-least-32-chars
```

本地后端默认连 `localhost:5432` / `localhost:6379`，不用写 `DB_URL`。

2. 在 PostgreSQL 里建好库和用户。

3. 三个终端分别启动：

```bash
# API  http://localhost:8080
cd backend && ./mvnw spring-boot:run

# 管理端  http://localhost:8088（开发时由 Nuxt 代理到 /admin）
cd dashboard && npm install && npm run dev

# 前台  http://localhost:3256
cd frontend && npm install && npm run dev
```

Windows：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 微信小程序（可选）

用微信开发者工具打开 `miniprogram/`，在 `miniprogram/utils/config.js` 填 API 域名，并配置 `WECHAT_MINI_PROGRAM_APP_ID`、`WECHAT_MINI_PROGRAM_APP_SECRET`。

## 配置

完整列表见 [`.env.example`](./.env.example)。图床、OAuth 等在后台配置，不写进 `.env`。

### 基础

| 变量 | 说明 |
|------|------|
| `BLOG_ENV` | `dev` / `prod`（Compose 默认 `prod`） |
| `FRONTEND_PORT` | 对外端口，默认 `3256` |
| `BLOG_ADMIN_BASE_PATH` | 后台路径，默认 `/admin`（改后需重建） |
| `POSTGRES_DB` / `DB_USERNAME` / `DB_PASSWORD` | 数据库 |
| `BLOG_ADMIN_USERNAME` / `BLOG_ADMIN_PASSWORD` / `BLOG_ADMIN_DISPLAY_NAME` | 初始管理员 |
| `BLOG_SITE_URL` | 正式站点地址；留空则按访问 Host 识别 |

### 密钥（不要互相复用）

| 变量 | 用途 |
|------|------|
| `BLOG_DOWNLOAD_SECRET_KEY` | 下载密封（仅后端） |
| `BLOG_TOTP_SECRET_KEY` | 管理员 TOTP（仅后端） |
| `BLOG_API_PAYLOAD_SECRET` | 接口载荷混淆（前后端，会进浏览器） |
| `BLOG_BACKUP_ENCRYPTION_KEY` | 备份加密，≥32 字符（仅后端） |

### 可选

| 配置 | 用途 |
|------|------|
| `WECHAT_MINI_PROGRAM_*` | 小程序登录 |
| `SENIVERSE_WIDGET_TOKEN` | 天气 |
| `BLOG_MUSIC_API_KEY` | 音乐搜索 |
| 后台「上传设置」 | 图床 / 文件管理 |
| 后台「OAuth 配置」 | OAuth2 登录 |

### 改后台路径

```env
BLOG_ADMIN_BASE_PATH=/manage
```

会影响 Vite base、Nuxt 静态资源与 SPA 回退、`robots.txt`、PWA。改完必须重建 `dashboard` 和 `frontend`（或重建 Compose 镜像）。

## 上线前检查

### 文件服务连通但图片提示“接口不存在”

统一文件管理可能返回 `/api/public/files/<ID>` 相对地址。该地址直接保存到博客后，浏览器会访问博客域名，而博客 `/api/**` 路由原先只指向自己的后端，因而返回“接口不存在”。连通性测试只验证签名和文件列表接口，不能证明公开图片地址可用。

博客使用文件管理当前的 `/api/open/files` 上传和列表接口，将返回的相对访问地址转换为“上传设置”中配置的文件服务完整地址。浏览器直接访问文件服务的 `/api/public/files/<ID>`，保留缩略图、裁切和签名参数。请确保 `fileManagerBaseUrl` 是浏览器可访问的文件服务地址，而不是博客地址或仅容器内可访问的服务名。公开权限和来源域名白名单仍由文件管理服务校验。

部署本次后端修复：`docker compose up -d --build backend`。从附件列表重新选择文件并保存文章，访问地址应为文件服务的完整 URL。博客不提供旧链接兼容或重定向接口；已经保存的博客域名错误地址需要重新选择附件或更新为正确的完整地址。若正确地址返回 403，再检查文件公开权限或域名白名单。

1. 改掉 `.env` 里所有默认密码和密钥
2. 有域名就设 `BLOG_SITE_URL=https://your.domain`；没有可留空
3. 反代终止 TLS，并转发 `Host`、`X-Forwarded-*`
4. 备份 PostgreSQL 卷、uploads 卷和后台备份包
5. OAuth / 微信 / 图床 / 地图 / 天气等按需开

## 许可证

本项目采用 MIT 协议发布，完整文本见仓库根目录 [`LICENSE`](LICENSE) 文件。
