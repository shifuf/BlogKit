package com.blog.system.domain.article;

/**
 * 文章可见性（访问控制）。
 *
 * <ul>
 *   <li>{@link #PUBLIC} 所有人可见（默认）。</li>
 *   <li>{@link #LOGIN} 仅登录用户可见正文。</li>
 *   <li>{@link #PASSWORD} 需输入正确访问密码才能查看正文。</li>
 *   <li>{@link #PRIVATE} 仅作者、管理员及指定用户可见。</li>
 *   <li>{@link #UNLISTED} 隐藏：任何人可经直链访问正文，但不出现在任何列表/搜索/sitemap/RSS。</li>
 * </ul>
 *
 * <p>非 {@code PUBLIC} 文章默认不出现在公开列表/搜索/相关/sitemap/RSS 中（管理员列表除外），
 * 只能通过直链访问并在详情页按上述规则门控。</p>
 */
public enum ArticleVisibility {
    PUBLIC,
    LOGIN,
    PASSWORD,
    PRIVATE,
    UNLISTED
}
