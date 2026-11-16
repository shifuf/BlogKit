export interface ArticleSummary {
  id: number;
  title: string;
  summary: string;
  coverImageUrl: string;
  coverColor?: string | null;
  categoryName: string;
  categorySlug?: string;
  tagNames: string[];
  authorName?: string;
  authorAvatarUrl?: string | null;
  sticky?: boolean;
  likeCount: number;
  favoriteCount: number;
  viewCount: number;
  commentCount: number;
  commentsEnabled: boolean;
  publishedAt: string;
  /** 搜索高亮标题 HTML（含 mark） */
  titleHighlight?: string | null;
  /** 搜索高亮摘要 HTML */
  summaryHighlight?: string | null;
}
export type ArticleSort = 'latest' | 'views' | 'likes' | 'comments';
export interface ArticleDetail extends ArticleSummary {
  seriesId?: number; seriesSlug?: string; seriesName?: string; seriesOrder?: number;
  content: string;
  // AI 摘要：仅在文章开启展示且已生成时后端才下发，前台据此渲染正文上方的摘要卡片。
  aiSummary?: string | null;
  sourceType?: string;
  sourceUrl?: string;
  location?: string | null;
  visibility?: 'PUBLIC' | 'LOGIN' | 'PASSWORD' | 'PRIVATE' | 'UNLISTED';
  locked?: boolean;
  requiresPassword?: boolean;
}
export interface CategorySummary {
  id: number;
  name: string;
  slug: string;
  parentId?: number | null;
  level?: number;
  displayOrder?: number;
  iconCode?: string | null;
  navVisible?: boolean;
  externalUrl?: string | null;
  articleCount?: number;
}
export interface TagSummary {
  id: number;
  name: string;
  slug?: string;
  articleCount: number;
}
export interface SiteSettings {
  profileGithub?: string;
  profileEmail?: string;
  siteName: string;
  siteSubtitle: string;
  siteDescription: string;
  siteKeywords: string;
  icp: string;
  footerText: string;
  footerInfo?: string;
  profileName: string;
  profileBio: string;
  profileAvatarUrl: string;
  allowRegister: boolean;
  commentsEnabled: boolean;
  oauthLoginEnabled: boolean;
  homeHeroEnabled: boolean;
  homeHeroImageUrl: string;
  homeHeroLinkUrl: string;
  homeHeroLinkType?: string;
  homeHeroArticleId?: string;
  homeHeroSlides?: string;
  homeSignEnabled: boolean;
  homeSignTitle: string;
  homeSignSubtitle: string;
  homeTabCategorySlugs?: string;
  homeTabLatestEnabled?: boolean;
  homeTabHotEnabled?: boolean;
  homeArticleListEnabled?: boolean;
  homeDynamicsBackground?: string;
  homeDynamicsSignature?: string;
  homeDynamicsNavVisible?: boolean;
  homeDynamicsNavOrder?: number;
  homeHomeNavVisible?: boolean;
  homeHomeNavOrder?: number;
  homeArchiveNavVisible?: boolean;
  homeArchiveNavOrder?: number;
  homeFriendsNavVisible?: boolean;
  homeFriendsNavOrder?: number;
  amapKey?: string;
  weatherEnabled?: boolean;
  /** 是否已对接消息通知平台（前台据此提示邮箱通知是否可用） */
  notificationPlatformEnabled?: boolean;
  /** 是否开放「有回复时邮件通知」勾选 */
  commentReplyEmailEnabled?: boolean;
  announcementEnabled?: boolean;
  announcementContent?: string;
  announcementLinkUrl?: string;
  friendExchangeName?: string;
  friendExchangeBio?: string;
  friendExchangeUrl?: string;
  friendExchangeLogo?: string;
  friendPageIntro?: string;
}
export interface DynamicPost {
  liked?: boolean;
  id: number;
  title: string;
  content: string;
  location?: string | null;
  visible: boolean;
  likeCount?: number;
  commentCount?: number;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface DynamicComment {
  id: number;
  authorName: string;
  content: string;
  createdAt: string;
  parentId?: number | null;
  replyTo?: string | null;
  region?: string | null;
  replyCount?: number;
  replies?: DynamicComment[];
}
export interface CommentSummary {
  replyToCommentId?: number | null;
  replyTo?: string | null;
  replyToContent?: string | null;
  replyToUnavailable?: boolean;
  id: number;
  articleId: number;
  parentId?: number | null;
  authorId: number;
  authorName: string;
  authorAvatarUrl?: string | null;
  authorRoleCode?: 'ADMIN' | 'USER' | string | null;
  content: string;
  status?: string;
  likeCount: number;
  replyCount: number;
  liked: boolean;
  createdAt: string;
  region?: string | null;
  pinned?: boolean;
  replies: CommentSummary[];
}
export interface UserCommentSummary {
  id: number;
  articleId: number;
  articleTitle: string;
  parentId?: number | null;
  parentAuthorName?: string | null;
  content: string;
  likeCount: number;
  replyCount: number;
  createdAt: string;
}
export interface LoginPayload {
  username: string;
  password: string;
}
export interface RegisterPayload extends LoginPayload {
  displayName: string;
}
export interface UserSession {
  userId: number;
  username: string;
  displayName: string;
  roleCode: 'ADMIN' | 'USER' | string;
  tokenName: string;
  tokenValue: string;
}
export interface OAuthAuthorizePayload {
  enabled: boolean;
  authorizationUrl: string;
}
export interface OAuthCallbackPayload {
  code: string;
  state: string;
}
export interface OAuthLoginPayload {
  session: UserSession;
  redirectPath: string;
}
export interface UserProfile {
  userId: number;
  username: string;
  displayName: string;
  roleCode: 'ADMIN' | 'USER' | string;
  avatarUrl?: string | null;
  createdAt: string;
  updatedAt: string;
  commentCount: number;
  articleLikeCount: number;
  favoriteCount: number;
  commentLikeCount: number;
}
export interface UserProfileUpdatePayload {
  displayName: string;
  avatarUrl?: string;
}
export interface PasswordChangePayload {
  oldPassword: string;
  newPassword: string;
}
export interface UserNotification {
  id: number;
  type: string;
  title: string;
  content: string;
  actionUrl?: string | null;
  sourceType: string;
  sourceId?: string | null;
  read: boolean;
  createdAt: string;
}
export interface EngagementState {
  articleId: number;
  likeCount: number;
  favoriteCount: number;
  liked: boolean;
  favorited: boolean;
}
export interface NavigationItem {
  id: number;
  label: string;
  name: string;
  routeSegment: string;
  iconCode: string;
  children?: NavigationChildItem[];
}
export interface NavigationChildItem {
  id: number;
  label: string;
  name: string;
  routeSegment: string;
}
export interface NavItem {
  label: string;
  to: string;
  icon: string;
  external?: boolean;
  children?: NavItem[];
}
export interface Category {
  slug: string;
  label: string;
  description: string;
  accent: string;
  icon?: string;
}
export interface Article {
  seriesId?: number; seriesSlug?: string; seriesName?: string; seriesOrder?: number;
  slug: string;
  title: string;
  excerpt: string;
  content: string[];
  rawContent?: string;
  category: string;
  categoryName?: string;
  tags: string[];
  cover: string;
  heroColor?: string;
  author: string;
  avatar: string;
  date: string;
  publishedAt?: string;
  views: string;
  comments: number;
  likes: number;
  favorites?: number;
  sticky?: boolean;
  sourceType?: string;
  sourceUrl?: string;
  location?: string | null;
  // AI 摘要（仅在后端开启展示且已生成时下发，否则为空）；前台详情正文上方展示。
  aiSummary?: string;
  commentsEnabled?: boolean;
  visibility?: 'PUBLIC' | 'LOGIN' | 'PASSWORD' | 'PRIVATE' | 'UNLISTED';
  locked?: boolean;
  requiresPassword?: boolean;
  titleHighlight?: string | null;
  summaryHighlight?: string | null;
}
export interface LinkItem {
  title: string;
  desc: string;
  url: string;
  logo: string;
}
export interface FriendLinkSummary {
  id: number;
  name: string;
  url: string;
  logoUrl?: string | null;
  description?: string | null;
  visible?: boolean;
  displayOrder: number;
  showInPage?: boolean;
  showInFooter?: boolean;
  createdAt?: string;
  updatedAt?: string;
}
export interface CommentItem {
  replyToCommentId?: number | null;
  replyToContent?: string | null;
  replyToUnavailable?: boolean;
  status?: string;
  id: number;
  author: string;
  content: string;
  date: string;
  createdAt?: string;
  avatar?: string;
  level?: string;
  badge?: string;
  verified?: boolean;
  location?: string;
  os?: string;
  browser?: string;
  replyTo?: string;
  replyCount?: number;
  replies?: CommentItem[];
}
export interface DownloadItem {
  platform: string;
  name?: string;
  url?: string;
  code?: string;
}
export interface ButtonConfig {
  text?: string;
  icon?: string;
  url?: string;
  color?: string;
}
export interface DownloadResource {
  kind?: string;
  gate?: string;
  articleId?: number | null;
  title?: string;
  description?: string;
  items?: DownloadItem[];
  button?: ButtonConfig;
}
