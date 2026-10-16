package com.bookassistant.entity.ui;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores a rendered HTML snapshot of a page for UI validation.
 */
@Entity
@Table(name = "ui_snapshots")
public class UiSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String pagePath;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 100)
    private String sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String htmlContent;

    @Column(nullable = false, length = 50)
    private String snapshotType = "current";

    private Integer viewportWidth = 1920;

    private Integer viewportHeight = 1080;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getPagePath() { return pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) {
        if (htmlContent != null) {
            this.htmlContent = htmlContent.replace("\0", "");
        } else {
            this.htmlContent = null;
        }
    }
    public String getSnapshotType() { return snapshotType; }
    public void setSnapshotType(String snapshotType) { this.snapshotType = snapshotType; }
    public Integer getViewportWidth() { return viewportWidth; }
    public void setViewportWidth(Integer viewportWidth) { this.viewportWidth = viewportWidth; }
    public Integer getViewportHeight() { return viewportHeight; }
    public void setViewportHeight(Integer viewportHeight) { this.viewportHeight = viewportHeight; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
