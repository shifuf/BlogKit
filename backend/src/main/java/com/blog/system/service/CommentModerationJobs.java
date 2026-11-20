package com.blog.system.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class CommentModerationJobs {
    public record Job(String kind, long commentId, String lease, int attempts) { }
    private final JdbcTemplate jdbc;
    public CommentModerationJobs(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    public void enqueue(String kind, long id) {
        jdbc.update("insert into comment_ai_review(kind,comment_id) values(?,?) on conflict(kind,comment_id) do nothing",kind,id);
    }
    @Transactional
    public void cancel(String kind, long id) {
        jdbc.update("""
                update comment_ai_review set state='CANCELLED',lease_token=null,lease_until=null,updated_at=now()
                where kind=? and comment_id=? and state in ('PENDING','RUNNING')
                """,kind,id);
    }
    @Transactional
    public boolean finish(String kind, long id, String lease) {
        return jdbc.update("""
                update comment_ai_review set state='DONE',lease_until=null,updated_at=now()
                where kind=? and comment_id=? and state='RUNNING' and lease_token=? and lease_until>now()
                """,kind,id,lease) == 1;
    }
    @Transactional
    public Job claim() {
        jdbc.update("""
                update comment_ai_review set state='FAILED',lease_token=null,lease_until=null,updated_at=now()
                where state='RUNNING' and lease_until<=now() and attempts>=3
                """);
        String token = UUID.randomUUID().toString();
        var jobs = jdbc.query("""
                update comment_ai_review set state='RUNNING',attempts=attempts+1,lease_token=?,
                    lease_until=now()+interval '2 minutes',updated_at=now()
                where id=(select id from comment_ai_review
                    where attempts<3 and ((state='PENDING' and next_attempt_at<=now())
                        or (state='RUNNING' and lease_until<=now()))
                    order by next_attempt_at,id for update skip locked limit 1)
                returning kind,comment_id,lease_token,attempts
                """,(rs,row) -> new Job(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getInt(4)),token);
        return jobs.isEmpty() ? null : jobs.get(0);
    }
    @Transactional
    public void fail(Job job) {
        jdbc.update("""
                update comment_ai_review set state=case when attempts>=3 then 'FAILED' else 'PENDING' end,
                    next_attempt_at=now()+case when attempts=1 then interval '15 seconds' else interval '60 seconds' end,
                    lease_token=null,lease_until=null,updated_at=now()
                where kind=? and comment_id=? and state='RUNNING' and lease_token=? and lease_until>now()
                """,job.kind(),job.commentId(),job.lease());
    }
}
