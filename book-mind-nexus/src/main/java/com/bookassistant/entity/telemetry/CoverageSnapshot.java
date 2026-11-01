package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a code coverage snapshot for a specific class.
 * <p>
 * <b>What:</b> Tracks line, branch, and method coverage percentages
 * for individual classes over time.
 * <p>
 * <b>Why:</b> Enables trend analysis of coverage and identification
 * of classes that consistently fall below thresholds.
 * <p>
 * <b>Where:</b> Coverage snapshot registry in the telemetry schema.
 */
@Entity
@Table(name = "coverage_snapshots", schema = "telemetry")
public class CoverageSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt = LocalDateTime.now();

    @Column(nullable = false, length = 200)
    private String className;

    @Column(name = "line_coverage_pct", precision = 5, scale = 2)
    private BigDecimal lineCoveragePct;

    @Column(name = "branch_coverage_pct", precision = 5, scale = 2)
    private BigDecimal branchCoveragePct;

    @Column(name = "method_coverage_pct", precision = 5, scale = 2)
    private BigDecimal methodCoveragePct;

    @Column(name = "lines_covered")
    private Integer linesCovered;

    @Column(name = "lines_total")
    private Integer linesTotal;

    @Column(nullable = false)
    private Boolean belowThreshold = false;

    public Long getId() { return id; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public BigDecimal getLineCoveragePct() { return lineCoveragePct; }
    public void setLineCoveragePct(BigDecimal lineCoveragePct) { this.lineCoveragePct = lineCoveragePct; }
    public BigDecimal getBranchCoveragePct() { return branchCoveragePct; }
    public void setBranchCoveragePct(BigDecimal branchCoveragePct) { this.branchCoveragePct = branchCoveragePct; }
    public BigDecimal getMethodCoveragePct() { return methodCoveragePct; }
    public void setMethodCoveragePct(BigDecimal methodCoveragePct) { this.methodCoveragePct = methodCoveragePct; }
    public Integer getLinesCovered() { return linesCovered; }
    public void setLinesCovered(Integer linesCovered) { this.linesCovered = linesCovered; }
    public Integer getLinesTotal() { return linesTotal; }
    public void setLinesTotal(Integer linesTotal) { this.linesTotal = linesTotal; }
    public Boolean getBelowThreshold() { return belowThreshold; }
    public void setBelowThreshold(Boolean belowThreshold) { this.belowThreshold = belowThreshold; }
}
