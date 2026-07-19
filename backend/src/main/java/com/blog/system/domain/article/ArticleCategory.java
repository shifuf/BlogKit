package com.blog.system.domain.article;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "article_category")
public class ArticleCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(length = 64)
    private String iconCode;

    @Column(name = "nav_visible")
    private Boolean navVisible = true;

    /** 外链地址：填写后导航点击会以新标签打开该链接，前台不再生成 /category/{slug} 路由。 */
    @Column(name = "external_url", length = 512)
    private String externalUrl;

    protected ArticleCategory() {
    }

    public ArticleCategory(String name, String slug, Integer displayOrder) {
        this.name = name;
        this.slug = slug;
        this.displayOrder = displayOrder;
    }

    public ArticleCategory(String name, String slug, Integer displayOrder, String iconCode) {
        this(name, slug, displayOrder);
        this.iconCode = iconCode;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getIconCode() {
        return iconCode;
    }

    public void setIconCode(String iconCode) {
        this.iconCode = iconCode;
    }

    public Boolean getNavVisible() {
        return navVisible;
    }

    public void setNavVisible(Boolean navVisible) {
        this.navVisible = navVisible;
    }

    public boolean isNavVisible() {
        // 历史分类的 nav_visible 列可能为 NULL（列新增前已存在的数据），默认视为「显示」，
        // 与实体默认值(=true)及后台新建默认一致；只有被显式设为 false 才隐藏。
        // 否则旧分类会因 NULL 被当成 false，导致前台导航只剩「主页」。
        return !Boolean.FALSE.equals(navVisible);
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }
}
