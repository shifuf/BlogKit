package com.blog.system.web.dto;
import java.util.List;
public record TagSummaryResponse(List<TagResponse> tags,long totalArticles) {}
