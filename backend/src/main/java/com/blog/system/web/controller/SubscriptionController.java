package com.blog.system.web.controller;

import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.ClientIpResolver;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.domain.system.EmailSubscription;
import com.blog.system.service.EmailSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SubscriptionController {
    private final EmailSubscriptionService subscriptionService;
    private final SimpleRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public SubscriptionController(
            EmailSubscriptionService subscriptionService,
            SimpleRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.subscriptionService = subscriptionService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/api/v1/subscriptions/email")
    public ApiResponse<Map<String, String>> subscribe(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryAcquire("email-sub:" + ip, 5, 600_000)) {
            throw new BusinessException("订阅太频繁，请稍后再试");
        }
        String address=body==null?null:body.get("email");
        if(address!=null&&!rateLimiter.tryAcquire("email-sub-target:"+Integer.toHexString(address.trim().toLowerCase(java.util.Locale.ROOT).hashCode()),3,3_600_000))throw new BusinessException("该邮箱申请频繁，请检查收件箱或稍后再试");
        EmailSubscription row = subscriptionService.subscribe(address);
        return ApiResponse.ok("确认邮件已加入发送队列，请前往邮箱确认", Map.of("email", row.getEmail()));
    }

    @PostMapping("/api/v1/subscriptions/email/confirm")
    public ApiResponse<String> confirm(@RequestBody Map<String,String> body,HttpServletRequest request){
        if(!rateLimiter.tryAcquire("email-confirm:"+clientIpResolver.resolve(request),10,600_000))throw new BusinessException("操作频繁");
        subscriptionService.confirm(body.get("token"));return ApiResponse.ok("订阅已确认","订阅已确认");
    }

    @PostMapping("/api/v1/subscriptions/email/unsubscribe")
    public ApiResponse<String> unsubscribe(@RequestBody Map<String, String> body) {
        subscriptionService.unsubscribe(body == null ? null : body.get("token"));
        return ApiResponse.ok("已退订", "已退订邮件通知");
    }

    /** 邮件正文中的退订链接（GET，浏览器可直接点）。 */
    @GetMapping("/api/v1/subscriptions/email/unsubscribe")
    public ApiResponse<String> unsubscribeByQuery(@RequestParam(required = false) String token) {
        subscriptionService.unsubscribe(token);
        return ApiResponse.ok("已退订", "已退订邮件通知");
    }

    @GetMapping("/api/v1/subscriptions/email/preferences")
    public ApiResponse<Map<String,Object>> preferences() { return ApiResponse.ok(subscriptionService.preferences()); }

    @PutMapping("/api/v1/subscriptions/email/preferences")
    public ApiResponse<Map<String,Object>> updatePreferences(@RequestBody Map<String,Boolean> body) {
        if(body==null || !body.containsKey("articleUpdates"))throw new BusinessException("缺少通知偏好");
        return ApiResponse.ok(subscriptionService.updatePreferences(Boolean.TRUE.equals(body.get("articleUpdates"))));
    }

    @GetMapping("/api/v1/admin/subscriptions/email")
    public ApiResponse<List<EmailSubscription>> listAdmin() {
        return ApiResponse.ok(subscriptionService.listAll());
    }

    @PutMapping("/api/v1/admin/subscriptions/email/{id}/disable")
    public ApiResponse<String> disable(@PathVariable Long id) {
        subscriptionService.disableById(id);
        return ApiResponse.ok("已停用", "已停用");
    }

    @DeleteMapping("/api/v1/admin/subscriptions/email/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        subscriptionService.deleteById(id);
        return ApiResponse.ok("已删除", "已删除");
    }
}
