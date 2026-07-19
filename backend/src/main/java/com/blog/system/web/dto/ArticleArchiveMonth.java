package com.blog.system.web.dto;

import java.util.List;

public record ArticleArchiveMonth(
        int month,
        List<ArticleArchiveItem> articles
) {
}
