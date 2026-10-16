package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a test run execution record.
 * <p>
 * <b>What:</b> Tracks every mvn test/verify execution with pass/fail counts,
 * duration, and git context.
 * <p>
 * <b>Why:</b> Enables trend analysis of test health and identification
 * of when regressions were introduced.
 * <p>
 * <b>Where:</b> Test run registry in the telemetry schema.
 */
@Entity
@Table(name = "test_runs", schema = "telemetry")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt = LocalDateTime.now();

    @Column(nullable = false)
    private Integer totalTests;

    @Column(nullable = false)
    private Integer failures;

    @Column(nullable = false)
    private Integer errors;

    @Column(nullable = false)
    private Integer skipped;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    @Column(nullable = false, length = 100)
    private String mavenGoal;

    @Column(name = "git_commit", length = 40)
    private String gitCommit;

    @Column(length = 100)
    private String branch;

    @Column(name = "java_version", length = 20)
    private String javaVersion;

    @Column(name = "os_name", length = 100)
    private String osName;

    @PrePersist
    protected void onCreate() {
        runAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getRunAt() { return runAt; }
    public void setRunAt(LocalDateTime runAt) { this.runAt = runAt; }
    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }
    public Integer getFailures() { return failures; }
    public void setFailures(Integer failures) { this.failures = failures; }
    public Integer getErrors() { return errors; }
    public void setErrors(Integer errors) { this.errors = errors; }
    public Integer getSkipped() { return skipped; }
    public void setSkipped(Integer skipped) { this.skipped = skipped; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getMavenGoal() { return mavenGoal; }
    public void setMavenGoal(String mavenGoal) { this.mavenGoal = mavenGoal; }
    public String getGitCommit() { return gitCommit; }
    public void setGitCommit(String gitCommit) { this.gitCommit = gitCommit; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
}
