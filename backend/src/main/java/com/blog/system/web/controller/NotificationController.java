package com.blog.system.web.controller;
import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.api.PageResponse;
import com.blog.system.service.NotificationService;
import com.blog.system.web.dto.NotificationResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service; public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public ApiResponse<PageResponse<NotificationResponse>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(service.list(page,size));}
    @GetMapping("/unread-count") public ApiResponse<Map<String,Long>> unread(){return ApiResponse.ok(Map.of("count",service.unreadCount()));}
    @PostMapping("/{id}/read") public ApiResponse<Void> read(@PathVariable Long id){service.markRead(id);return ApiResponse.ok(null);}
    @PostMapping("/read-all") public ApiResponse<Void> readAll(){service.markAllRead();return ApiResponse.ok(null);}
}
