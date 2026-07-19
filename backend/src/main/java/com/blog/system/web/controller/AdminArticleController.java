package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.service.AdminOperationLogService;
import com.blog.system.service.ArticleCommandService;
import com.blog.system.service.ArticlePreviewService;
import com.blog.system.web.dto.ArticleExportItem;
import com.blog.system.web.dto.ArticleLinkScanItem;
import com.blog.system.web.dto.ArticleManageResponse;
import com.blog.system.web.dto.ArticlePreviewLinkResponse;
import com.blog.system.web.dto.ArticleStickyOrderRequest;
import com.blog.system.web.dto.ArticleUpsertRequest;
import com.blog.system.web.dto.ArticleFlagsRequest;
import com.blog.system.web.dto.ArticleVersionResponse;
import com.blog.system.web.dto.ReviewArticleRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {
    private final ArticleCommandService articleCommandService;
    private final ArticlePreviewService articlePreviewService;
    private final AdminOperationLogService operationLogService;

    public AdminArticleController(
            ArticleCommandService articleCommandService,
            ArticlePreviewService articlePreviewService,
            AdminOperationLogService operationLogService
    ) {
        this.articleCommandService = articleCommandService;
        this.articlePreviewService = articlePreviewService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ArticleManageResponse>> listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) com.blog.system.domain.article.ArticleVisibility visibility,
            @RequestParam(required = false) Boolean sticky
    ) {
        return ApiResponse.ok(articleCommandService.findAdminArticles(
                page, size, status, keyword, deleted, categoryId, tagId, visibility, sticky));
    }

    /** 批量快捷修改状态/可见性/评论/置顶等（最多 50 篇）。 */
    @PatchMapping("/batch-flags")
    public ApiResponse<Map<String, Object>> batchUpdateFlags(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = body == null ? List.of() : (List<Object>) body.getOrDefault("ids", List.of());
        List<Long> ids = rawIds.stream()
                .map(v -> {
                    if (v instanceof Number n) return n.longValue();
                    try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
                })
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(50)
                .toList();
        ArticleFlagsRequest flags = parseFlags(body == null ? Map.of() : body);
        Map<String, Object> result = articleCommandService.batchUpdateArticleFlags(ids, flags);
        operationLogService.record("文章管理", "批量快捷修改", "Article", null,
                "更新 " + result.getOrDefault("updated", 0) + " 篇");
        return ApiResponse.ok("批量更新完成", result);
    }

    private static ArticleFlagsRequest parseFlags(Map<String, Object> body) {
        ArticleStatus status = null;
        com.blog.system.domain.article.ArticleVisibility visibility = null;
        Boolean commentsEnabled = body.get("commentsEnabled") instanceof Boolean b ? b : null;
        Boolean listedInPublic = body.get("listedInPublic") instanceof Boolean b ? b : null;
        Boolean sticky = body.get("sticky") instanceof Boolean b ? b : null;
        Long categoryId = body.get("categoryId") instanceof Number n ? n.longValue() : null;
        @SuppressWarnings("unchecked")
        List<Object> rawTagIds = body.get("tagIds") instanceof List<?> list ? (List<Object>) list : null;
        List<Long> tagIds = rawTagIds == null ? null : rawTagIds.stream()
                .map(v -> v instanceof Number n ? n.longValue() : null)
                .filter(id -> id != null && id > 0)
                .toList();
        if (body.get("status") != null) {
            try { status = ArticleStatus.valueOf(String.valueOf(body.get("status"))); } catch (Exception ignored) {}
        }
        if (body.get("visibility") != null) {
            try {
                visibility = com.blog.system.domain.article.ArticleVisibility.valueOf(String.valueOf(body.get("visibility")));
            } catch (Exception ignored) {}
        }
        java.time.OffsetDateTime scheduledPublishAt = null;
        if (body.get("scheduledPublishAt") != null) {
            try {
                scheduledPublishAt = java.time.OffsetDateTime.parse(String.valueOf(body.get("scheduledPublishAt")));
            } catch (Exception ignored) {}
        }
        java.time.OffsetDateTime scheduledUnpublishAt = null;
        if (body.get("scheduledUnpublishAt") != null) {
            try {
                scheduledUnpublishAt = java.time.OffsetDateTime.parse(String.valueOf(body.get("scheduledUnpublishAt")));
            } catch (Exception ignored) {}
        }
        Long seriesId = body.get("seriesId") instanceof Number n ? n.longValue() : null;
        Integer seriesOrder = body.get("seriesOrder") instanceof Number n ? n.intValue() : null;
        return new ArticleFlagsRequest(status, visibility, commentsEnabled, listedInPublic, sticky, categoryId, tagIds,
                scheduledPublishAt, scheduledUnpublishAt, seriesId, seriesOrder);
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleManageResponse> getArticle(@PathVariable Long id) {
        return ApiResponse.ok(articleCommandService.findAdminArticle(id));
    }

    @PostMapping
    public ApiResponse<ArticleManageResponse> createArticle(@Valid @RequestBody ArticleUpsertRequest request) {
        ArticleManageResponse response = articleCommandService.createArticle(request);
        operationLogService.record("文章管理", "创建文章", "Article", response.id(), response.title());
        return ApiResponse.ok("文章已创建", response);
    }

    @PutMapping("/{id}")
    public ApiResponse<ArticleManageResponse> updateArticle(@PathVariable Long id, @Valid @RequestBody ArticleUpsertRequest request) {
        ArticleManageResponse response = articleCommandService.updateArticle(id, request);
        operationLogService.record("文章管理", "更新文章", "Article", response.id(), response.title());
        return ApiResponse.ok("文章已更新", response);
    }

    // 列表内联快捷修改状态/可见性/评论开关/列表展示/置顶（仅改传入字段，不进编辑页）。
    @PatchMapping("/{id}/flags")
    public ApiResponse<ArticleManageResponse> updateArticleFlags(@PathVariable Long id, @RequestBody ArticleFlagsRequest request) {
        ArticleManageResponse response = articleCommandService.updateArticleFlags(id, request);
        operationLogService.record("文章管理", "快捷修改", "Article", response.id(), response.title());
        return ApiResponse.ok("已更新", response);
    }

    @PostMapping("/export")
    public ApiResponse<List<ArticleExportItem>> exportArticles(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body == null ? List.of() : body.getOrDefault("ids", List.of());
        List<ArticleExportItem> response = articleCommandService.exportArticles(ids);
        operationLogService.record("文章管理", "导出 Markdown", "Article", null, "导出 " + response.size() + " 篇");
        return ApiResponse.ok(response);
    }

    @PostMapping("/scan-links")
    public ApiResponse<List<ArticleLinkScanItem>> scanBrokenLinks(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body == null ? List.of() : body.getOrDefault("ids", List.of());
        List<ArticleLinkScanItem> response = articleCommandService.scanBrokenLinks(ids);
        operationLogService.record("文章管理", "扫描坏链", "Article", null, "扫描 " + ids.size() + " 篇，问题 " + response.size() + " 个");
        return ApiResponse.ok(response);
    }

    // 置顶文章拖拽排序：批量更新置顶序号（越小越靠前）。
    @PutMapping("/reorder-sticky")
    public ApiResponse<Void> reorderSticky(@RequestBody java.util.List<ArticleStickyOrderRequest> request) {
        articleCommandService.reorderStickyArticles(request);
        operationLogService.record("文章管理", "置顶排序", "Article", null, "更新 " + (request == null ? 0 : request.size()) + " 项");
        return ApiResponse.ok("置顶顺序已保存", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteArticle(@PathVariable Long id) {
        articleCommandService.deleteArticle(id);
        operationLogService.record("文章管理", "移入回收站", "Article", id, "文章移入回收站");
        return ApiResponse.ok("文章已移入回收站", null);
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<ArticleManageResponse> restoreArticle(@PathVariable Long id) {
        ArticleManageResponse response = articleCommandService.restoreArticle(id);
        operationLogService.record("文章管理", "恢复文章", "Article", response.id(), response.title());
        return ApiResponse.ok("文章已恢复", response);
    }

    @DeleteMapping("/{id}/purge")
    public ApiResponse<Void> purgeArticle(@PathVariable Long id) {
        articleCommandService.purgeArticle(id);
        operationLogService.record("文章管理", "彻底删除文章", "Article", id, "彻底删除文章");
        return ApiResponse.ok("文章已彻底删除", null);
    }

    @PostMapping("/{id}/review")
    public ApiResponse<ArticleManageResponse> reviewArticle(
            @PathVariable Long id,
            @Valid @RequestBody ReviewArticleRequest request
    ) {
        ArticleManageResponse response = articleCommandService.reviewArticle(id, request);
        operationLogService.record("文章管理", "审核文章", "Article", response.id(), response.status().name());
        return ApiResponse.ok("审核完成", response);
    }

    @PostMapping("/backfill-cover-colors")
    public ApiResponse<Integer> backfillCoverColors(
            @RequestParam(defaultValue = "false") boolean overwrite
    ) {
        int updated = articleCommandService.backfillCoverColors(overwrite);
        operationLogService.record("文章管理", "回填封面主色", "Article", null, "更新 " + updated + " 篇");
        return ApiResponse.ok("已回填 " + updated + " 篇文章的封面主色", updated);
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<java.util.List<ArticleVersionResponse>> listVersions(@PathVariable Long id) {
        return ApiResponse.ok(articleCommandService.listArticleVersions(id));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public ApiResponse<ArticleManageResponse> restoreVersion(@PathVariable Long id, @PathVariable Long versionId) {
        ArticleManageResponse response = articleCommandService.restoreArticleVersion(id, versionId);
        operationLogService.record("文章管理", "恢复文章版本", "Article", response.id(), "version=" + versionId);
        return ApiResponse.ok("版本已恢复", response);
    }

    @DeleteMapping("/{id}/versions/{versionId}")
    public ApiResponse<Void> deleteVersion(@PathVariable Long id, @PathVariable Long versionId) {
        articleCommandService.deleteArticleVersion(id, versionId);
        operationLogService.record("文章管理", "删除文章版本", "Article", id, "version=" + versionId);
        return ApiResponse.ok("历史版本已删除", null);
    }

    @PostMapping("/{id}/preview-link")
    public ApiResponse<ArticlePreviewLinkResponse> createPreviewLink(@PathVariable Long id) {
        ArticlePreviewLinkResponse response = articlePreviewService.createPreviewLink(id);
        operationLogService.record("文章管理", "生成预览链接", "Article", id, response.previewPath());
        return ApiResponse.ok("预览链接已生成", response);
    }

    // 生成/润色 AI 摘要：基于当前编辑器里的标题+正文（未保存也可用），返回文本，不落库。
    // 前端拿到后填入摘要框，随文章保存一起持久化。
    @PostMapping("/ai-summary")
    public ApiResponse<java.util.Map<String, String>> generateAiSummary(@RequestBody com.blog.system.web.dto.ArticleAiSummaryRequest request) {
        String summary = articleCommandService.generateAiSummary(request);
        return ApiResponse.ok("AI 摘要已生成", java.util.Map.of("summary", summary));
    }

    /** AI 辅助：task=title|tags|seo|summary，返回对应字段，不落库。 */
    @PostMapping("/ai-assist")
    public ApiResponse<java.util.Map<String, Object>> aiAssist(@RequestBody com.blog.system.web.dto.ArticleAiAssistRequest request) {
        return ApiResponse.ok("AI 已生成", articleCommandService.aiAssist(request));
    }
}
