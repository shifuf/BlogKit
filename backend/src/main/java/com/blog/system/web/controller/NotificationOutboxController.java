package com.blog.system.web.controller;
import org.springframework.web.bind.annotation.*;
import com.blog.system.common.api.ApiResponse;
import com.blog.system.service.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/admin/notifications/outbox")
public class NotificationOutboxController {
 private final NotificationOutboxService outbox;private final CurrentUserService current;
 public NotificationOutboxController(NotificationOutboxService outbox,CurrentUserService current){this.outbox=outbox;this.current=current;}
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(){admin();return ApiResponse.ok(outbox.list());}
 @PostMapping("/{id}/retry") public ApiResponse<Void> retry(@PathVariable long id){admin();outbox.retry(id);return ApiResponse.ok("已加入重试队列",null);}
 private void admin(){current.requireAdmin(current.requireCurrentUser());}
}
