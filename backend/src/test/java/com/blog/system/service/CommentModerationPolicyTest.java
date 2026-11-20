package com.blog.system.service;

import com.blog.system.domain.comment.ArticleCommentStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentModerationPolicyTest {
    SystemManagementService settings = mock(SystemManagementService.class);
    CommentModerationPolicy policy = new CommentModerationPolicy(settings);
    @Test void aiHoldsNewCommentsEvenWhenManualModerationIsDisabled() {
        when(settings.isCommentAiModerationEnabled()).thenReturn(true);
        assertEquals(ArticleCommentStatus.PENDING, policy.initialStatus("这篇文章很实用"));
    }
    @Test void manualSwitchIsRespectedOnlyWhenAiIsOff() {
        assertEquals(ArticleCommentStatus.APPROVED, policy.initialStatus("hello"));
        when(settings.isCommentModerationEnabled()).thenReturn(true);
        assertEquals(ArticleCommentStatus.PENDING, policy.initialStatus("hello"));
    }
    @Test void explicitSpamNeverEntersAutoApprovalQueue() {
        when(settings.isCommentAiModerationEnabled()).thenReturn(true);
        when(settings.blockedCommentWords()).thenReturn(List.of("blocked"));
        assertEquals(ArticleCommentStatus.SPAM, policy.initialStatus("BLOCKED"));
        assertEquals(ArticleCommentStatus.SPAM, policy.initialStatus("加微信 http://a.example http://b.example http://c.example"));
    }
}
