package com.blog.system.service;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name="blog.comment-ai.worker-enabled",havingValue="true",matchIfMissing=true)
public class CommentModerationWorker {
    private final CommentModerationJobs jobs;
    private final CommentModerationPolicy policy;
    private final CommentAiReviewer reviewer;
    private final CommentService articles;
    private final DynamicEngagementService dynamics;
    private ScheduledExecutorService scheduler;
    public CommentModerationWorker(CommentModerationJobs jobs, CommentModerationPolicy policy, CommentAiReviewer reviewer,
            CommentService articles, DynamicEngagementService dynamics) {
        this.jobs=jobs; this.policy=policy; this.reviewer=reviewer; this.articles=articles; this.dynamics=dynamics;
    }
    @PostConstruct
    void start() {
        // This executor is independent of Spring's scheduler and has exactly one recurring task.
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task,"blog-comment-ai-review"); thread.setDaemon(true); return thread;
        });
        scheduler.scheduleWithFixedDelay(this::runOnce,2,2,TimeUnit.SECONDS);
    }
    @PreDestroy
    void stop() { if (scheduler != null) scheduler.shutdownNow(); }

    /** No transaction spans this method. The service read transaction ends before provider I/O. */
    public void runOnce() {
        CommentModerationJobs.Job job=null;
        try {
            if (!policy.aiEnabled()) return;
            job=jobs.claim();
            if (job==null) return;
            String content = "ARTICLE".equals(job.kind()) ? articles.aiReviewContent(job.commentId())
                    : dynamics.aiReviewContent(job.commentId());
            if (content==null) { jobs.cancel(job.kind(),job.commentId()); return; }
            Boolean approved=reviewer.review(content);
            if (approved==null || !policy.aiEnabled()) { jobs.fail(job); return; }
            if ("ARTICLE".equals(job.kind())) articles.applyAiReview(job.commentId(),job.lease(),approved);
            else dynamics.applyAiReview(job.commentId(),job.lease(),approved);
        } catch (Exception failure) {
            // Never log comment text, model output or provider exception messages.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Comment AI review deferred ({})",failure.getClass().getSimpleName());
            if (job!=null) {
                try { jobs.fail(job); } catch (Exception ignored) { /* Durable lease recovers on restart. */ }
            }
        }
    }
}
