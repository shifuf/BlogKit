package com.blog.system.web.dto;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        Long parentId,
        Integer level,
        Integer displayOrder,
        String iconCode,
        boolean navVisible,
        String externalUrl,
        long articleCount
) {
}
