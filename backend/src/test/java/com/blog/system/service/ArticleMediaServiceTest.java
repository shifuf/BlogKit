package com.blog.system.service;

import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.article.Article;
import com.blog.system.domain.article.ArticleCategory;
import com.blog.system.domain.article.ArticleStatus;
import com.blog.system.domain.article.ArticleVisibility;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.ArticleCommentRepository;
import com.blog.system.repository.ArticleRepository;
import com.blog.system.repository.SiteSettingRepository;
import com.blog.system.web.controller.AdminArticleMediaController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real AES/GCM and canonical URL parsing, with only database/network boundaries mocked. */
class ArticleMediaServiceTest {
    private static final String FILE_URL = "https://files.example/api/public/files/file_123";
    private static final String TEMPORARY_URL = "https://files.example/api/public/files/file_123?expires=300&signature=issued";
    private final DownloadCipher cipher = new DownloadCipher("article-media-test-secret-at-least-32-characters");
    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final SiteSettingRepository settings = mock(SiteSettingRepository.class);
    private UnifiedFileManagerClient files;
    private ArticleMediaService media;
    private Article stored;
    private boolean transactionActive;
    private boolean authorizationObservedActiveTransaction;

    @BeforeEach
    void setup() {
        when(settings.findAllBySettingGroupOrderBySettingKeyAsc("upload")).thenReturn(List.of());
        files = spy(new UnifiedFileManagerClient(new ObjectMapper(), settings, new OutboundUrlValidator(""),
                "https://files.example", "/api", "app-test", "secret", "blog", "reuse", 5));
        doAnswer(invocation -> {
            assertFalse(transactionActive, "UFM network request must follow the database transaction");
            assertEquals("file_123", invocation.getArgument(0));
            return TEMPORARY_URL;
        }).when(files).getTemporaryAccessUrl(anyString());
        var transactions = new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> callback) {
                transactionActive = true;
                try { return callback.doInTransaction(new SimpleTransactionStatus()); }
                finally { transactionActive = false; }
            }
        };
        media = new ArticleMediaService(files, cipher, articles, transactions);
        stored = article(17L, 4L, ArticleStatus.PUBLISHED);
        when(articles.findByIdAndStatusAndDeletedAtIsNull(anyLong(), any())).thenAnswer(invocation -> {
            authorizationObservedActiveTransaction = transactionActive;
            return stored != null && stored.getId().equals(invocation.getArgument(0))
                    && stored.getStatus() == invocation.getArgument(1) && stored.getDeletedAt() == null
                    ? Optional.of(stored) : Optional.empty();
        });
    }

    @Test
    void authorizedBodyGetsOpaqueArticleAndFileBoundCapabilityAndTenMinuteExpiry() {
        long before = System.currentTimeMillis();
        String body = "![first](" + FILE_URL + ")\n<img src=\"" + FILE_URL + "?old=signature\">";
        List<String> caps = capabilities(media.decorate(stored, body));
        assertEquals(2, caps.size());
        assertEquals(caps.get(0), caps.get(1), "Duplicate file URLs should share a capability within one body");
        assertFalse(caps.get(0).contains("file_123"));
        String[] payload = cipher.open(caps.get(0)).split(":");
        assertEquals(List.of("article-media", "17", "4", "file_123"), List.of(payload).subList(0, 4));
        assertEquals("PUBLISHED", payload[5]);
        long expires = Long.parseLong(payload[4]);
        assertTrue(expires >= before + 600_000 && expires <= System.currentTimeMillis() + 600_000);
        verify(files, never()).getTemporaryAccessUrl(anyString());
        assertEquals(TEMPORARY_URL, media.open(caps.get(0)));
        assertTrue(authorizationObservedActiveTransaction, "Article authorization must run in a database transaction");
        verify(articles).findByIdAndStatusAndDeletedAtIsNull(17L, ArticleStatus.PUBLISHED);
        verify(files).getTemporaryAccessUrl("file_123");
    }

    @Test
    void changingArticleVersionRevokesAnAlreadyIssuedCapabilityBeforeUfmAccess() {
        String cap = capability();
        ReflectionTestUtils.setField(stored, "version", 5L);
        assertThrows(ForbiddenException.class, () -> media.open(cap));
        verify(files, never()).getTemporaryAccessUrl(anyString());
    }

    @Test
    void missingDeletedOrDifferentArticleCannotRedeemAnAlreadyIssuedCapability() {
        String cap = capability();
        stored.setDeletedAt(OffsetDateTime.now());
        assertThrows(ForbiddenException.class, () -> media.open(cap));
        stored = article(18L, 4L, ArticleStatus.PUBLISHED);
        assertThrows(ForbiddenException.class, () -> media.open(cap));
        stored = null;
        assertThrows(ForbiddenException.class, () -> media.open(cap));
        verify(files, never()).getTemporaryAccessUrl(anyString());
    }

    @Test
    void draftPreviewCapabilityWorksOnlyWhileItsSealedStatusAndVersionStillMatch() {
        stored.setStatus(ArticleStatus.DRAFT);
        String cap = capability();
        assertEquals("DRAFT", cipher.open(cap).split(":")[5]);
        assertEquals(TEMPORARY_URL, media.open(cap));
        clearInvocations(files);
        stored.setStatus(ArticleStatus.PUBLISHED);
        assertThrows(ForbiddenException.class, () -> media.open(cap));
        verify(files, never()).getTemporaryAccessUrl(anyString());
    }

    @Test
    void tamperedExpiredAndOtherPurposeTokensNeverReachDatabaseOrUfm() {
        byte[] bytes = Base64.getUrlDecoder().decode(capability());
        bytes[bytes.length / 2] ^= 1;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String expired = cipher.seal("article-media:17:4:file_123:" + (System.currentTimeMillis() - 1) + ":PUBLISHED");
        String otherPurpose = cipher.seal("download:17:4:file_123:" + (System.currentTimeMillis() + 60_000) + ":PUBLISHED");
        for (String cap : List.of(tampered, expired, otherPurpose, "not-a-valid-capability"))
            assertThrows(ForbiddenException.class, () -> media.open(cap));
        verifyNoInteractions(articles);
        verify(files, never()).getTemporaryAccessUrl(anyString());
    }

    @Test
    void foreignOriginsAndConfusablePathsRemainUnchangedInTheBody() {
        String body = String.join("\n", List.of(
                "![foreign](https://evil.example/api/public/files/file_123)",
                "![scheme](http://files.example/api/public/files/file_123)",
                "![port](https://files.example:444/api/public/files/file_123)",
                "![userinfo](https://user:pass@files.example/api/public/files/file_123)",
                "![encoded](https://files.example/api/public/files/%66ile_123)",
                "![nested](https://files.example/api/public/files/file_123/extra)",
                "![fragment](" + FILE_URL + "#fragment)",
                "![relative](//files.example/api/public/files/file_123)"));
        assertEquals(body, media.decorate(stored, body));
        assertTrue(capabilities(body).isEmpty());
        verifyNoInteractions(articles);
        verify(files, never()).getTemporaryAccessUrl(anyString());
    }

    @Test
    void lockedArticleAndFilteredSecretBlocksDoNotMintMediaCapabilities() {
        var current = mock(CurrentUserService.class);
        var author = new UserAccount();
        ReflectionTestUtils.setField(author, "id", 1L);
        author.setUsername("author");
        stored.setAuthor(author);
        stored.setCategory(new ArticleCategory("tech", "tech", 0));
        stored.setContent("![visible](" + FILE_URL + ")\n:::bd-secret login\n![hidden](https://files.example/api/public/files/hidden_456)\n:::");
        var query = new ArticleQueryService(articles, settings, current, new BCryptPasswordEncoder(),
                new SecretBlockFilter(mock(ArticleCommentRepository.class), current));
        ReflectionTestUtils.setField(query, "media", media);
        // Query authorization and the real secret filter run before decoration.
        stored.setVisibility(ArticleVisibility.PRIVATE);
        assertEquals("", query.findPublishedArticle(17L).content());
        verify(files, never()).resolveManagedFileId(anyString());
        verify(settings, never()).findAllBySettingGroupOrderBySettingKeyAsc("upload");
        stored.setVisibility(ArticleVisibility.PUBLIC);
        String publicBody = query.findPublishedArticle(17L).content();
        assertFalse(publicBody.contains("hidden_456"));
        List<String> caps = capabilities(publicBody);
        assertEquals(1, caps.size());
        assertEquals("file_123", cipher.open(caps.get(0)).split(":")[3]);
        verify(files, never()).getTemporaryAccessUrl(anyString());
    }

    @Test
    void administratorPreviewEndpointRejectsAReaderBeforeLookingUpAnyArticleOrFile() {
        var current = mock(CurrentUserService.class);
        var reader = new UserAccount();
        reader.setRoleCode("USER");
        when(current.requireCurrentUser()).thenReturn(reader);
        doThrow(new ForbiddenException("admin required")).when(current).requireAdmin(reader);
        var controller = new AdminArticleMediaController(articles, current, files);
        assertThrows(ForbiddenException.class, () -> controller.previews(17L));
        verifyNoInteractions(articles, files);
    }

    private String capability() {
        return capabilities(media.decorate(stored, "![image](" + FILE_URL + ")")).get(0);
    }

    private static List<String> capabilities(String content) {
        var matcher = Pattern.compile("/api/v1/article-media/open\\?cap=([A-Za-z0-9_-]+)").matcher(content);
        var result = new ArrayList<String>();
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private static Article article(Long id, Long version, ArticleStatus status) {
        var article = new Article();
        ReflectionTestUtils.setField(article, "id", id);
        ReflectionTestUtils.setField(article, "version", version);
        article.setStatus(status);
        return article;
    }
}
