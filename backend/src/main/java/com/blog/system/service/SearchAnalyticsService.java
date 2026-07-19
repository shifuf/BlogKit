package com.blog.system.service;

import com.blog.system.common.api.PageResponse;
import com.blog.system.domain.system.SearchKeywordStat;
import com.blog.system.domain.user.UserAccount;
import com.blog.system.repository.SearchKeywordStatRepository;
import com.blog.system.web.dto.SearchKeywordStatResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Service
public class SearchAnalyticsService {
    private final SearchKeywordStatRepository searchKeywordStatRepository;
    private final CurrentUserService currentUserService;

    public SearchAnalyticsService(SearchKeywordStatRepository searchKeywordStatRepository, CurrentUserService currentUserService) {
        this.searchKeywordStatRepository = searchKeywordStatRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void recordSearch(String keyword, long resultCount) {
        String safeKeyword = normalizeKeyword(keyword);
        if (!StringUtils.hasText(safeKeyword)) {
            return;
        }
        SearchKeywordStat stat = searchKeywordStatRepository.findByKeyword(safeKeyword)
                .orElseGet(SearchKeywordStat::new);
        stat.setKeyword(safeKeyword);
        stat.setTotalCount(stat.getTotalCount() + 1);
        stat.setLastResultCount(Math.max(resultCount, 0));
        if (resultCount <= 0) {
            stat.setNoResultCount(stat.getNoResultCount() + 1);
        }
        stat.setLastSearchedAt(OffsetDateTime.now());
        searchKeywordStatRepository.save(stat);
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchKeywordStatResponse> listStats(int page, int size, String keyword) {
        UserAccount currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin(currentUser);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("totalCount"), Sort.Order.desc("lastSearchedAt"), Sort.Order.desc("id"))
        );
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        var stats = StringUtils.hasText(safeKeyword)
                ? searchKeywordStatRepository.findByKeywordContainingIgnoreCase(safeKeyword, pageable)
                : searchKeywordStatRepository.findAll(pageable);
        return new PageResponse<>(
                stats.getContent().stream().map(this::toResponse).toList(),
                stats.getNumber(),
                stats.getSize(),
                stats.getTotalElements(),
                stats.getTotalPages()
        );
    }

    private String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
        if (value.length() > 120) {
            value = value.substring(0, 120);
        }
        return value;
    }

    private SearchKeywordStatResponse toResponse(SearchKeywordStat stat) {
        return new SearchKeywordStatResponse(
                stat.getId(),
                stat.getKeyword(),
                stat.getTotalCount(),
                stat.getNoResultCount(),
                stat.getLastResultCount(),
                stat.getLastSearchedAt(),
                stat.getCreatedAt(),
                stat.getUpdatedAt()
        );
    }
}
