package com.blog.system.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaxonomyUpsertRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 64) String slug,
        Long parentId,
        @Size(max = 64) String iconCode,
        Boolean navVisible,
        Integer displayOrder,
        @Size(max = 512) String externalUrl
) {
}
