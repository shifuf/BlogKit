package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArticleSeriesUpsertRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 80) String slug,
        @Size(max = 500) String description,
        @Size(max = 512) String coverImageUrl,
        Integer displayOrder,
        Boolean visible
) {
}
