package com.blog.system.common.util;

import org.springframework.web.util.HtmlUtils;

/**
 * 用户生成内容的轻量消毒：剥离 HTML 标签并转义危险字符。
 * 评论/昵称等字段按纯文本或 Markdown 存，禁止直接落库可执行 HTML。
 */
public final class HtmlSanitizer {
    private HtmlSanitizer() {}

    /** 去掉 HTML 标签，保留可见文本。 */
    public static String stripTags(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        // 先剥标签，再解码实体；解码后可能复活 &lt;img&gt; 一类 payload，再剥一次。
        String value = stripTagsOnce(input);
        value = decodeBasicEntities(value);
        value = stripTagsOnce(value);
        return value.trim();
    }

    private static String stripTagsOnce(String input) {
        return input
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", "");
    }

    /** 转义后用于安全输出（邮件/站内通知等）。 */
    public static String escape(String input) {
        return HtmlUtils.htmlEscape(input == null ? "" : input);
    }

    /** 入库前：剥标签 + 限长。 */
    public static String cleanText(String input, int maxLen) {
        String cleaned = stripTags(input);
        if (cleaned.length() > maxLen) {
            return cleaned.substring(0, maxLen);
        }
        return cleaned;
    }

    private static String decodeBasicEntities(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }
}
