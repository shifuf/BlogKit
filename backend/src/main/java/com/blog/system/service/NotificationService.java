package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.domain.user.UserNotification;
import com.blog.system.repository.UserNotificationRepository;
import com.blog.system.web.dto.NotificationResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

@Service
public class NotificationService {
    private final UserNotificationRepository repository;
    private final CurrentUserService currentUserService;
    public NotificationService(UserNotificationRepository repository, CurrentUserService currentUserService) {
        this.repository = repository; this.currentUserService = currentUserService;
    }
    @Transactional
    public void create(UserAccount user, String type, String title, String content, String actionUrl, String sourceType, Object sourceId) {
        if (user == null) return;
        UserNotification n = new UserNotification(); n.setUser(user); n.setType(trim(type,32)); n.setTitle(trim(title,160));
        n.setContent(trim(content,1000)); n.setActionUrl(trimNullable(actionUrl,512)); n.setSourceType(trim(sourceType,64));
        n.setSourceId(sourceId == null ? null : trim(String.valueOf(sourceId),64)); repository.save(n);
    }
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size) {
        Long userId = currentUserService.requireCurrentUser().getId();
        var result = repository.findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(0,page), Math.min(100,Math.max(1,size))));
        return new PageResponse<>(result.map(this::toResponse).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true) public long unreadCount(){ return repository.countByUser_IdAndReadAtIsNull(currentUserService.requireCurrentUser().getId()); }
    @Transactional public void markRead(Long id){ var user=currentUserService.requireCurrentUser(); var n=repository.findByIdAndUser_Id(id,user.getId()).orElseThrow(()->new BusinessException("通知不存在")); n.setReadAt(OffsetDateTime.now()); }
    @Transactional public void markAllRead(){ var page=repository.findByUser_IdOrderByCreatedAtDesc(currentUserService.requireCurrentUser().getId(),PageRequest.of(0,1000)); page.forEach(n->{if(n.getReadAt()==null)n.setReadAt(OffsetDateTime.now());}); }
    private NotificationResponse toResponse(UserNotification n){return new NotificationResponse(n.getId(),n.getType(),n.getTitle(),n.getContent(),n.getActionUrl(),n.getSourceType(),n.getSourceId(),n.getReadAt()!=null,n.getCreatedAt());}
    private String trim(String v,int max){String s=v==null?"":v.trim();return s.length()>max?s.substring(0,max):s;} private String trimNullable(String v,int max){return v==null||v.isBlank()?null:trim(v,max);}
}
