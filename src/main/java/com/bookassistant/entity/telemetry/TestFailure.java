package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents an individual test failure linked to a test run.
 * <p>
 * <b>What:</b> Records the failing test class/method, error details,
 * and whether it is a known regression.
 * <p>
 * <b>Why:</b> Enables precise tracking of which tests fail, how often,
 * and whether failures are new or recurring.
 * <p>
 * <b>Where:</b> Test failure registry in the telemetry schema.
 */
@Entity
@Table(name = "test_failures", schema = "telemetry")
public class TestFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(nullable = false, length = 200)
    private String testClass;

    @Column(nullable = false, length = 200)
    private String testMethod;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "error_hash", length = 64)
    private String errorHash;

    @Column(nullable = false)
    private Boolean isRegression = false;

    @Column(name = "bug_id")
    private Long bugId;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getTestClass() { return testClass; }
    public void setTestClass(String testClass) { this.testClass = testClass; }
    public String getTestMethod() { return testMethod; }
    public void setTestMethod(String testMethod) { this.testMethod = testMethod; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    public String getErrorHash() { return errorHash; }
    public void setErrorHash(String errorHash) { this.errorHash = errorHash; }
    public Boolean getIsRegression() { return isRegression; }
    public void setIsRegression(Boolean isRegression) { this.isRegression = isRegression; }
    public Long getBugId() { return bugId; }
    public void setBugId(Long bugId) { this.bugId = bugId; }
}
