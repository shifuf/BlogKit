package com.blog.system.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CommentResponse(
        Long id,
        Long articleId,
        Long parentId,
        Long authorId,
        String authorName,
        String authorAvatarUrl,
        String authorRoleCode,
        String content,
        String status,
        long likeCount,
        long replyCount,
        boolean liked,
        OffsetDateTime createdAt,
        // 评论者 IP 归属地（省/市，内网/解析失败为空串），前台作为标签展示。
        String region,
        boolean pinned,
        List<CommentResponse> replies
) {
}
