package com.blog.system.web.dto;

public record AiConnectionTestResponse(
        boolean ok,
        String message,
        long latencyMs
) {
}
