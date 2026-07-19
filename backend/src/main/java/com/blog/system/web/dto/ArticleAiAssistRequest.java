package com.blog.system.web.dto;

/**
 * 通用 AI 辅助请求。
 * @param task title | tags | seo | summary
 * @param mode generate | polish（summary 用）
 */
public record ArticleAiAssistRequest(
        String task,
        String mode,
        String content,
        String title,
        String currentSummary,
        String instruction
) {
}
