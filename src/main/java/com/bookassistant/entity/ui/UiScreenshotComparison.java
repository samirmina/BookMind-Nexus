package com.bookassistant.entity.ui;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores the result of comparing a current screenshot against a baseline.
 */
@Entity
@Table(name = "ui_screenshot_comparisons")
public class UiScreenshotComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baseline_id")
    private Long baselineId;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column
    private Double diffPercentage;

    @Column
    private Integer diffPixels;

    @Column
    private Integer threshold = 5;

    @Column
    private Boolean passed;

    @Column(length = 1000)
    private String diffImagePath;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getBaselineId() { return baselineId; }
    public void setBaselineId(Long baselineId) { this.baselineId = baselineId; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public Double getDiffPercentage() { return diffPercentage; }
    public void setDiffPercentage(Double diffPercentage) { this.diffPercentage = diffPercentage; }
    public Integer getDiffPixels() { return diffPixels; }
    public void setDiffPixels(Integer diffPixels) { this.diffPixels = diffPixels; }
    public Integer getThreshold() { return threshold; }
    public void setThreshold(Integer threshold) { this.threshold = threshold; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public String getDiffImagePath() { return diffImagePath; }
    public void setDiffImagePath(String diffImagePath) { this.diffImagePath = diffImagePath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
