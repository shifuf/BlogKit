package com.blog.system.web.dto;

/** 置顶文章批量排序：每项给出文章 id 与新的置顶序号（越小越靠前）。 */
public record ArticleStickyOrderRequest(
        Long id,
        Integer stickyOrder
) {
}
