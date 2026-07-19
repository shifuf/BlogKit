package com.blog.system.web.dto;

import java.util.List;

public record ArticleArchiveYear(
        int year,
        List<ArticleArchiveMonth> months
) {
}
