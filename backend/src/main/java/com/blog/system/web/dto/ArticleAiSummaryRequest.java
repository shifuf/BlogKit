package com.blog.system.web.dto;

/**
 * 文章 AI 摘要生成/润色请求。
 *
 * @param mode      GENERATE=依据正文重新生成；POLISH=在现有摘要基础上润色
 * @param content   正文 Markdown（编辑器当前内容，可能尚未落库，故由前端传入）
 * @param title     文章标题（作为生成上下文）
 * @param currentSummary 现有 AI 摘要（POLISH 时必填；GENERATE 时忽略）
 * @param instruction 可选的额外润色/生成要求（如「更口语化」「突出结论」）
 */
public record ArticleAiSummaryRequest(
        String mode,
        String content,
        String title,
        String currentSummary,
        String instruction
) {
}
