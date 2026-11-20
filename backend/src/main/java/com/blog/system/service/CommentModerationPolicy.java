package com.blog.system.service;
import com.blog.system.domain.comment.ArticleCommentStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Locale;
import java.util.regex.Pattern;
@Service
public class CommentModerationPolicy {
    private final SystemManagementService settings;
    public CommentModerationPolicy(SystemManagementService settings) { this.settings = settings; }
    public ArticleCommentStatus initialStatus(String content) {
        if (spamScore(content) >= 4) return ArticleCommentStatus.SPAM;
        return aiEnabled() || settings.isCommentModerationEnabled()
                ? ArticleCommentStatus.PENDING : ArticleCommentStatus.APPROVED;
    }
    public boolean aiEnabled() { return settings.isCommentAiModerationEnabled(); }
    private int spamScore(String content) {
        if (!StringUtils.hasText(content)) return 0;
        String normalized = content.toLowerCase(Locale.ROOT);
        int score = settings.blockedCommentWords().stream().map(w -> w.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains) ? 4 : 0;
        long links = Pattern.compile("https?://|www\\.").matcher(normalized).results().count();
        if (links >= 3) score += 3; else if (links > 0) score += 1;
        if (normalized.matches("(?s).*(加微|微信|vx|v信|qq[:：]?\\s*\\d{5,}|代办|博彩|棋牌|发票|返利).*")) score += 3;
        if (Pattern.compile("(.)\\1{7,}").matcher(normalized).find()) score += 2;
        if (content.length() > 1200) score += 1;
        return score;
    }
}
