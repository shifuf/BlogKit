package com.blog.system.service;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentModerationWorkerTest {
    CommentModerationJobs jobs=mock(CommentModerationJobs.class);
    CommentModerationPolicy policy=mock(CommentModerationPolicy.class);
    AiService ai=mock(AiService.class);
    CommentService articles=mock(CommentService.class);
    DynamicEngagementService dynamics=mock(DynamicEngagementService.class);
    CommentModerationWorker worker=new CommentModerationWorker(jobs,policy,new CommentAiReviewer(ai,new ObjectMapper()),articles,dynamics);
    @Test void workerProcessesOnlyOneClaimAndRoutesOnlyExplicitApprovalToTheCorrectService() {
        when(policy.aiEnabled()).thenReturn(true); when(ai.isConfigured()).thenReturn(true);
        when(jobs.claim()).thenReturn(new CommentModerationJobs.Job("DYNAMIC",7,"lease",1));
        when(dynamics.aiReviewContent(7)).thenReturn("hello");
        when(ai.chat(anyString(),eq("hello"),anyInt())).thenReturn("{\"approved\":true}");
        List<Boolean> publications=new ArrayList<>();
        doAnswer(call->{publications.add(call.getArgument(2));return null;}).when(dynamics).applyAiReview(eq(7L),eq("lease"),anyBoolean());
        worker.runOnce(); assertEquals(List.of(true),publications); verify(jobs,times(1)).claim();
        verifyNoInteractions(articles);
    }
    @Test void malformedAndUnavailableResponsesRetryWithoutPublishing() {
        var job=new CommentModerationJobs.Job("ARTICLE",8,"lease",1);
        when(policy.aiEnabled()).thenReturn(true); when(ai.isConfigured()).thenReturn(true);
        when(jobs.claim()).thenReturn(job); when(articles.aiReviewContent(8)).thenReturn("hello");
        when(ai.chat(anyString(),anyString(),anyInt())).thenReturn("true");
        worker.runOnce(); verify(jobs).fail(job); verify(articles,never()).applyAiReview(anyLong(),anyString(),anyBoolean());
    }
    @Test void disabledAndIneligibleCommentsNeverCallTheProvider() {
        worker.runOnce(); verifyNoInteractions(jobs,ai);
        when(policy.aiEnabled()).thenReturn(true);
        when(jobs.claim()).thenReturn(new CommentModerationJobs.Job("ARTICLE",9,"lease",1));
        worker.runOnce(); verify(jobs).cancel("ARTICLE",9); verifyNoInteractions(ai);
    }
}
