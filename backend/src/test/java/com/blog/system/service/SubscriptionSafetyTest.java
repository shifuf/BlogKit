package com.blog.system.service;
import com.blog.system.domain.system.EmailSubscription;
import com.blog.system.repository.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
class SubscriptionSafetyTest {
 @Test void subscribeMustRemainPendingUntilEmailOwnershipConfirmed(){
  var repo=mock(EmailSubscriptionRepository.class);when(repo.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());when(repo.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
  var service=new EmailSubscriptionService(repo,mock(ExternalNotificationService.class),mock(CurrentUserService.class),mock(SiteSettingRepository.class),"https://blog.test");
  org.springframework.test.util.ReflectionTestUtils.setField(service,"outbox",mock(NotificationOutboxService.class));
  EmailSubscription row=service.subscribe("Reader@example.test");assertFalse(row.isConfirmed());assertFalse(row.isEnabled());
 }
}
