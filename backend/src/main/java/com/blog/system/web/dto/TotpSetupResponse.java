package com.blog.system.web.dto;
public record TotpSetupResponse(String secret, String provisioningUri, boolean enabled) {}
