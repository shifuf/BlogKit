package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.service.CurrentUserService;
import com.blog.system.service.MusicService;
import com.blog.system.service.MusicSettingsService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/music")
public class AdminMusicController {
    private final MusicSettingsService settings;
    private final MusicService music;
    private final CurrentUserService users;
    public AdminMusicController(MusicSettingsService settings, MusicService music, CurrentUserService users) {
        this.settings = settings; this.music = music; this.users = users;
    }
    private void requireAdmin() {
        users.requireAdmin(users.requireCurrentUser());
    }
    @GetMapping("/settings") public ApiResponse<List<SiteSetting>> get() { requireAdmin(); return ApiResponse.ok(settings.list()); }
    @PutMapping("/settings") public ApiResponse<List<SiteSetting>> save(@RequestBody Map<String, String> values) { requireAdmin(); return ApiResponse.ok(settings.save(values)); }
    @GetMapping("/status") public ApiResponse<Map<String, String>> status() { requireAdmin(); return ApiResponse.ok(music.providerStatus()); }
}
