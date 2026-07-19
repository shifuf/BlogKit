package com.blog.system.web.dto;

public record ArticleNeighborsResponse(
        ArticleNeighborItem previous,
        ArticleNeighborItem next
) {
}
