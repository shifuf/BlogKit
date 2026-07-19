package com.blog.system.web.dto;

/** 公开友链：不暴露申请人邮箱/审核状态等后台字段。 */
public record PublicFriendLinkResponse(
        Long id,
        String name,
        String url,
        String logoUrl,
        String description,
        Integer displayOrder,
        boolean showInPage,
        boolean showInFooter
) {
}
