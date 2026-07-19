package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.exception.ForbiddenException;
import com.blog.system.common.util.DownloadCipher;
import com.blog.system.domain.comment.ArticleCommentStatus;
import com.blog.system.domain.system.DownloadAccessLog;
import com.blog.system.repository.ArticleCommentRepository;
import com.blog.system.repository.DownloadAccessLogRepository;
import com.blog.system.web.dto.DownloadResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 下载/按钮信息的密封与按门控换取。
 *
 * <ul>
 *   <li>密封（管理员）：把明文配置序列化后用 {@link DownloadCipher} 加密成 token。</li>
 *   <li>换取（公开）：解密 token 后按 gate 校验可见性，再返回真实地址。</li>
 * </ul>
 *
 * 门控分级：none 直接放行；login 强制登录；comment 对登录用户强校验是否已评论，
 * 游客走前端软门控（后端无稳定身份，直接放行，与产品决策一致）。
 */
@Service
public class DownloadService {
    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    private final DownloadCipher downloadCipher;
    private final ArticleCommentRepository commentRepository;
    private final DownloadAccessLogRepository accessLogRepository;
    private final ObjectMapper objectMapper;

    public DownloadService(
            DownloadCipher downloadCipher,
            ArticleCommentRepository commentRepository,
            DownloadAccessLogRepository accessLogRepository,
            ObjectMapper objectMapper
    ) {
        this.downloadCipher = downloadCipher;
        this.commentRepository = commentRepository;
        this.accessLogRepository = accessLogRepository;
        this.objectMapper = objectMapper;
    }

    public String seal(DownloadResource resource) {
        try {
            return downloadCipher.seal(objectMapper.writeValueAsString(resource));
        } catch (JsonProcessingException e) {
            throw new BusinessException("下载配置序列化失败");
        }
    }

    public DownloadResource open(String token, String clientIp) {
        DownloadResource resource = decrypt(token);
        enforceGate(resource);
        recordAccess(resource, clientIp);
        return resource;
    }

    /**
     * 管理员二次编辑时按 token 取回明文配置，<b>不做门控校验</b>（调用方已校验管理员身份）。
     * 用于后台编辑器把已插入的下载/按钮卡片回填到弹窗修改。
     */
    public DownloadResource openRaw(String token) {
        return decrypt(token);
    }

    private void recordAccess(DownloadResource resource, String clientIp) {
        try {
            DownloadAccessLog logRow = new DownloadAccessLog();
            logRow.setArticleId(resource.articleId());
            logRow.setKind(StringUtils.hasText(resource.kind()) ? resource.kind() : "download");
            logRow.setGate(StringUtils.hasText(resource.gate()) ? resource.gate() : "none");
            logRow.setTitle(resource.title());
            logRow.setClientIp(clientIp);
            accessLogRepository.save(logRow);
        } catch (Exception ex) {
            // 统计失败不影响主流程。
            log.debug("记录下载访问失败: {}", ex.getMessage());
        }
    }

    private DownloadResource decrypt(String token) {
        try {
            return objectMapper.readValue(downloadCipher.open(token), DownloadResource.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("下载信息无效或已损坏");
        }
    }

    private void enforceGate(DownloadResource resource) {
        String gate = StringUtils.hasText(resource.gate()) ? resource.gate() : "none";
        switch (gate) {
            case "login" -> StpUtil.checkLogin();
            case "comment" -> {
                // 登录与评论门控均强制校验；游客不再后端放行（前端软门控可被伪造）。
                StpUtil.checkLogin();
                Long userId = StpUtil.getLoginIdAsLong();
                boolean commented = resource.articleId() != null
                        && commentRepository.existsByArticle_IdAndAuthor_IdAndStatus(
                        resource.articleId(), userId, ArticleCommentStatus.APPROVED);
                if (!commented) {
                    throw new ForbiddenException("评论后可见");
                }
            }
            default -> {
                // none：仅靠 token 防爬，直接放行。
            }
        }
    }
}
