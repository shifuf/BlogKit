package com.blog.system.service;

import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.regex.Pattern;

/** Removes unauthorized container bodies before Markdown leaves the server. */
@Service
public class SecretBlockFilter {
    private static final Pattern OPEN = Pattern.compile("^([:]{3,})bd-secret(?:\\s+(\\S+))?.*$");
    private final ArticleCommentRepository comments;
    private final CurrentUserService current;

    public SecretBlockFilter(ArticleCommentRepository comments, CurrentUserService current) {
        this.comments=comments; this.current=current;
    }

    public String filter(String content, Long articleId, UserAccount viewer, Long authorId) {
        if (!StringUtils.hasText(content) || !content.contains("bd-secret")) return content;
        boolean privileged = viewer != null && (current.isAdmin(viewer)
                || (authorId != null && authorId.equals(viewer.getId())));
        boolean commented = viewer != null && articleId != null && comments
                .existsByArticle_IdAndAuthor_IdAndStatus(articleId, viewer.getId(), ArticleCommentStatus.APPROVED);
        String[] lines = content.split("\n", -1);
        return filterLines(lines,0,lines.length,privileged,viewer!=null,commented);
    }

    /** Public previews must never contain gated body text, regardless of the current session. */
    public static String publicText(String content) {
        if(content==null)return "";
        String[] lines=content.split("\n",-1);
        return filterLines(lines,0,lines.length,false,false,false);
    }

    public static String safeSummary(String content,String summary) {
        if(content==null || !content.contains("bd-secret"))return summary==null?"":summary;
        String text=publicText(content).replaceAll("(?m)^.*:{3,}bd-[^\\n]*$|^\\s*:{3,}\\s*$", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return text.length()>140?text.substring(0,140)+"…":text;
    }

    private static String filterLines(String[] lines,int start,int end,boolean privileged,boolean loggedIn,boolean commented) {
        StringBuilder out=new StringBuilder();
        for(int i=start;i<end;i++) {
            String normalized=normalize(lines[i]);
            var opener=OPEN.matcher(normalized);
            if(!opener.matches()) {out.append(lines[i]);if(i+1<end)out.append('\n');continue;}
            int length=opener.group(1).length();
            String gate=opener.group(2)==null?"login":opener.group(2);
            int close=i+1;
            // A container closes on a pure fence at least as long as its opener (markdown-it contract).
            for(;close<end;close++) {
                String candidate=normalize(lines[close]);
                if(candidate.matches(":{3,}")&&candidate.length()>=length)break;
            }
            boolean allowed=privileged || ("comment".equalsIgnoreCase(gate)?loggedIn&&commented:loggedIn);
            out.append(lines[i]).append('\n');
            if(allowed) {
                String body=filterLines(lines,i+1,close,privileged,loggedIn,commented);
                out.append(body); if(!body.isEmpty())out.append('\n');
            }
            if(close<end)out.append(lines[close]);else out.append(":".repeat(length));
            i=close;
            if(i+1<end)out.append('\n');
        }
        return out.toString();
    }

    private static String normalize(String line) {
        // Containers may be parsed recursively inside blockquotes and list items by markdown-it.
        String text=line.strip();
        while(text.startsWith(">"))text=text.substring(1).stripLeading();
        return text.replaceFirst("^(?:[-+*]|[0-9]+[.)])\\s+", "").strip();
    }
}
