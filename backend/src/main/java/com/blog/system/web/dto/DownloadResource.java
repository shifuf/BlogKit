package com.blog.system.web.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 下载卡片 / 自定义按钮的明文配置。
 *
 * <p>既作为管理员密封接口的请求体，也作为公开换取接口在门控通过后的返回体。
 * 真实地址（{@code items[].url} / {@code button.url}）只在两端流转，密文形态随正文存储。</p>
 *
 * @param kind        download | button
 * @param gate        none | login | comment（可见门控）
 * @param articleId   归属文章 id（用于 comment 门控校验，可空）
 */
public record DownloadResource(
        String kind,
        String gate,
        Long articleId,
        @Size(max = 120) String title,
        @Size(max = 1000) String description,
        List<DownloadItem> items,
        ButtonConfig button
) {
    public record DownloadItem(
            @Size(max = 32) String platform,
            @Size(max = 120) String name,
            @Size(max = 2048) String url,
            @Size(max = 64) String code
    ) {
    }

    public record ButtonConfig(
            @Size(max = 60) String text,
            @Size(max = 64) String icon,
            @Size(max = 2048) String url,
            @Size(max = 32) String color
    ) {
    }
}
