package com.blog.system.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOG_INTEGRATION_DB_URL",matches=".+")
class CommentModerationJobsPostgresTest {
    JdbcTemplate jdbc;
    CommentModerationJobs jobs;
    @BeforeEach void database() {
        var source = new DriverManagerDataSource(System.getenv("BLOG_INTEGRATION_DB_URL"),
                System.getenv().getOrDefault("BLOG_INTEGRATION_DB_USER","blog"),
                System.getenv().getOrDefault("BLOG_INTEGRATION_DB_PASSWORD",""));
        jdbc = new JdbcTemplate(source);
        String name = jdbc.queryForObject("select current_database()",String.class);
        if (name == null || !name.contains("test")) throw new IllegalStateException("Disposable test database required");
        Flyway.configure().dataSource(source).load().migrate();
        jdbc.update("delete from comment_ai_review");
        jobs = new CommentModerationJobs(jdbc);
    }
    @Test void queueIsDurableIdempotentAndRejectsExpiredOrCancelledLeases() {
        jobs.enqueue("ARTICLE",71); jobs.enqueue("ARTICLE",71);
        assertEquals(1,jdbc.queryForObject("select count(*) from comment_ai_review",Integer.class));
        var first = jobs.claim(); assertNotNull(first); assertEquals(1,first.attempts());
        assertNull(jobs.claim(),"active lease cannot be claimed twice");
        jdbc.update("update comment_ai_review set lease_until=now()-interval '1 second'");
        var second = jobs.claim(); assertNotNull(second); assertEquals(2,second.attempts());
        assertFalse(jobs.finish("ARTICLE",71,first.lease()));
        jobs.cancel("ARTICLE",71);
        assertFalse(jobs.finish("ARTICLE",71,second.lease()));
        assertNull(new CommentModerationJobs(jdbc).claim(),"restart must respect durable cancellation");
    }
    @Test void failuresBackOffAndStopAfterThreeAttempts() {
        jobs.enqueue("DYNAMIC",72);
        for (int attempt=1; attempt<=3; attempt++) {
            var job=jobs.claim(); assertNotNull(job); assertEquals(attempt,job.attempts()); jobs.fail(job);
            if (attempt<3) {
                int seconds=jdbc.queryForObject("select extract(epoch from next_attempt_at-now())::integer from comment_ai_review",Integer.class);
                assertTrue(attempt==1 ? seconds>=10 && seconds<=16 : seconds>=55 && seconds<=61);
                assertNull(jobs.claim());
                jdbc.update("update comment_ai_review set next_attempt_at=now()-interval '1 second'");
            }
        }
        assertEquals("FAILED",jdbc.queryForObject("select state from comment_ai_review",String.class));
        assertNull(jobs.claim());
    }
    @Test void onlyCurrentLeaseCompletesExactlyOnceAndCrashesAreBounded() {
        jobs.enqueue("ARTICLE",73); var job=jobs.claim(); assertNotNull(job);
        assertTrue(jobs.finish("ARTICLE",73,job.lease()));
        assertFalse(jobs.finish("ARTICLE",73,job.lease())); assertNull(jobs.claim());
        jobs.enqueue("DYNAMIC",74);
        for (int i=0;i<3;i++) {
            assertNotNull(jobs.claim());
            jdbc.update("update comment_ai_review set lease_until=now()-interval '1 second' where comment_id=74");
        }
        assertNull(jobs.claim());
        assertEquals("FAILED",jdbc.queryForObject("select state from comment_ai_review where comment_id=74",String.class));
    }
}
