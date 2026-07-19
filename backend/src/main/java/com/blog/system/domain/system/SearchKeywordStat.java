package com.blog.system.domain.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "search_keyword_stat", indexes = {
        @Index(name = "idx_search_keyword_stat_keyword", columnList = "keyword", unique = true),
        @Index(name = "idx_search_keyword_stat_total", columnList = "total_count")
})
public class SearchKeywordStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String keyword;

    @Column(name = "total_count", nullable = false, columnDefinition = "bigint default 0")
    private long totalCount = 0;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long noResultCount = 0;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long lastResultCount = 0;

    @Column(nullable = false)
    private OffsetDateTime lastSearchedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (lastSearchedAt == null) {
            lastSearchedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getNoResultCount() {
        return noResultCount;
    }

    public void setNoResultCount(long noResultCount) {
        this.noResultCount = noResultCount;
    }

    public long getLastResultCount() {
        return lastResultCount;
    }

    public void setLastResultCount(long lastResultCount) {
        this.lastResultCount = lastResultCount;
    }

    public OffsetDateTime getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(OffsetDateTime lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
