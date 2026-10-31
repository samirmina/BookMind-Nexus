package com.bookassistant.entity.ui;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores baseline screenshots for visual regression testing.
 */
@Entity
@Table(name = "ui_screenshot_baselines")
public class UiScreenshotBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String pagePath;

    @Column(nullable = false, length = 50)
    private String userState = "anonymous";

    @Column(nullable = false, length = 1000)
    private String screenshotPath;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column
    private Integer diffThreshold = 5;

    @Column
    private Boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getPagePath() { return pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    public String getUserState() { return userState; }
    public void setUserState(String userState) { this.userState = userState; }
    public String getScreenshotPath() { return screenshotPath; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Integer getDiffThreshold() { return diffThreshold; }
    public void setDiffThreshold(Integer diffThreshold) { this.diffThreshold = diffThreshold; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
