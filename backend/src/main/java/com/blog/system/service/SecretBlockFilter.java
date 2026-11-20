package com.blog.system.service;

import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Redacts bodies and all credentials before article markdown crosses a public boundary. */
@Service
public class SecretBlockFilter {
    private static final Pattern OPEN = Pattern.compile("^(:{3,})bd-secret(?:\\s+(\\S+))?(?:\\s+(.*))?$");
    private static final Pattern CONTAINER = Pattern.compile("^(:{3,})bd-(?:secret|gallery)(?:\\s+.*)?$");
    private static final Set<String> GATES = Set.of("login", "comment", "password", "users");
    private final ArticleCommentRepository comments;
    private final CurrentUserService current;
    @org.springframework.beans.factory.annotation.Autowired private SecretSectionService sections;

    public SecretBlockFilter(ArticleCommentRepository comments, CurrentUserService current) {
        this.comments = comments;
        this.current = current;
    }

    public String filter(String content, Long articleId, UserAccount viewer, Long authorId) {
        return filter(content, articleId, viewer, authorId, 0L);
    }

    public String filter(String content, Long articleId, UserAccount viewer, Long authorId, Long version) {
        if (!StringUtils.hasText(content) || !content.contains("bd-secret")) return content;
        boolean privileged = viewer != null && (current.isAdmin(viewer)
                || (authorId != null && authorId.equals(viewer.getId())));
        boolean commented = viewer != null && articleId != null && comments
                .existsByArticle_IdAndAuthor_IdAndStatus(articleId, viewer.getId(), ArticleCommentStatus.APPROVED);
        String[] lines = content.split("\n", -1);
        return filterLines(lines, 0, lines.length, section -> {
            if (!GATES.contains(section.gate())) return false;
            if (privileged) return true;
            return switch (section.gate()) {
                case "login" -> viewer != null;
                case "comment" -> viewer != null && commented;
                default -> sections != null && sections.canView(articleId, version, section, viewer);
            };
        });
    }

    public static String publicText(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n", -1);
        return filterLines(lines, 0, lines.length, section -> false);
    }

    public static String safeSummary(String content, String summary) {
        if (content == null || !content.contains("bd-secret")) return summary == null ? "" : summary;
        String text = publicText(content).replaceAll("(?m)^.*:{3,}bd-[^\\n]*$|^\\s*:{3,}\\s*$", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 140 ? text.substring(0, 140) + "…" : text;
    }

    private static String filterLines(String[] lines, int start, int end, java.util.function.Predicate<Section> allowed) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            Section section = parse(lines, i, end);
            if (section == null) out.append(lines[i]);
            else {
                out.append(section.publicHeader()).append('\n');
                if (allowed.test(section)) {
                    String body = filterLines(lines, i + 1, section.close(), allowed);
                    if (!body.isEmpty()) out.append(body).append('\n');
                }
                out.append(section.prefix()).append(":".repeat(section.fence()));
                i = section.close();
            }
            if (i + 1 < end) out.append('\n');
        }
        return out.toString();
    }

    static Section parse(String[] lines, int start, int end) {
        var opener = OPEN.matcher(normalize(lines[start]));
        if (!opener.matches()) return null;
        int fence = opener.group(1).length();
        String gate = opener.group(2) == null ? "login" : opener.group(2).toLowerCase(Locale.ROOT);
        String payload = opener.group(3) == null ? "" : opener.group(3).strip();
        String prefix = lines[start].substring(0, lines[start].indexOf(':'));
        // Unclosed/nested containers consume their whole tail instead of failing open.
        var stack = new ArrayDeque<Integer>();
        stack.push(fence);
        int close = start + 1;
        for (; close < end; close++) {
            String line = normalize(lines[close]);
            var nested = CONTAINER.matcher(line);
            if (nested.matches()) stack.push(nested.group(1).length());
            else if (line.matches(":{3,}") && line.length() >= stack.peek()) {
                stack.pop();
                if (stack.isEmpty()) break;
            }
        }
        return new Section(start, close, fence, prefix, GATES.contains(gate) ? gate : "invalid", payload);
    }

    record Section(int start, int close, int fence, String prefix, String gate, String payload) {
        String id() { return "section-" + start; }
        String publicHeader() { return prefix + ":".repeat(fence) + "bd-secret " + gate + " " + id(); }
    }

    private static String normalize(String line) {
        String text = line.strip();
        String previous;
        do {
            previous = text;
            text = text.replaceFirst("^(?:>|[-+*]|[0-9]+[.)])\\s*", "").stripLeading();
        } while (!text.equals(previous));
        return text;
    }
}
