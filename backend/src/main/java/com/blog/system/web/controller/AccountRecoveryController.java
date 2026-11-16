package com.blog.system.web.controller;
import com.blog.system.service.AccountRecoveryService;
import com.blog.system.common.api.ApiResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.SimpleRateLimiter;
import com.blog.system.common.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/auth/recovery")
public class AccountRecoveryController {
 private final AccountRecoveryService recovery;private final SimpleRateLimiter rate;private final ClientIpResolver ips;
 public AccountRecoveryController(AccountRecoveryService recovery,SimpleRateLimiter rate,ClientIpResolver ips){this.recovery=recovery;this.rate=rate;this.ips=ips;}
 @GetMapping("/status") public ApiResponse<Map<String,Object>> status(){return ApiResponse.ok(recovery.status());}
 @PostMapping("/email") public ApiResponse<Void> email(@RequestBody Map<String,String> b,HttpServletRequest r){limit(r,"email");recovery.requestEmail(b.get("email"),b.get("password"),b.get("totpCode"));return ApiResponse.ok("验证邮件已发送",null);}
 @PostMapping("/verify-email") public ApiResponse<Void> verify(@RequestBody Map<String,String> b,HttpServletRequest r){limit(r,"verify");recovery.verifyEmail(b.get("token"));return ApiResponse.ok("邮箱已验证",null);}
 @PostMapping("/forgot-password") public ApiResponse<Void> forgot(@RequestBody Map<String,String> b,HttpServletRequest r){limit(r,"forgot");recovery.forgotPassword(b.get("email"));return ApiResponse.ok("如果邮箱已验证，恢复邮件将发送到邮箱",null);}
 @PostMapping("/reset-password") public ApiResponse<Void> reset(@RequestBody Map<String,String> b,HttpServletRequest r){limit(r,"reset");recovery.resetPassword(b.get("token"),b.get("password"));return ApiResponse.ok("密码已修改，请重新登录",null);}
 private void limit(HttpServletRequest r,String action){if(!rate.tryAcquire("recovery:"+action+":"+ips.resolve(r),5,900_000))throw new BusinessException("请求频繁，请稍后重试");}
}
