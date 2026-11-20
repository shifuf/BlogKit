package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.GuestCommentIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Establish the HttpOnly credential before the browser starts parallel comment requests. */
@RestController
public class CommentIdentityController {
    private final GuestCommentIdentity identity;
    public CommentIdentityController(GuestCommentIdentity identity) { this.identity = identity; }

    @GetMapping("/api/v1/comments/identity")
    public ApiResponse<Void> initialize() {
        identity.currentHash();
        return ApiResponse.ok(null);
    }
}
