package com.bookassistant.service.telemetry;

import com.bookassistant.entity.telemetry.AccountLinkAudit;
import com.bookassistant.entity.telemetry.AiInteraction;
import com.bookassistant.entity.telemetry.SystemHealth;
import com.bookassistant.entity.telemetry.TelemetryError;
import com.bookassistant.entity.telemetry.TelemetryEvent;
import com.bookassistant.entity.telemetry.TelemetryProcedure;
import com.bookassistant.entity.telemetry.TestRun;
import com.bookassistant.entity.telemetry.TestFailure;
import com.bookassistant.entity.telemetry.CoverageSnapshot;
import com.bookassistant.repository.telemetry.AccountLinkAuditRepository;
import com.bookassistant.repository.telemetry.AiInteractionRepository;
import com.bookassistant.repository.telemetry.SystemHealthRepository;
import com.bookassistant.repository.telemetry.TelemetryErrorRepository;
import com.bookassistant.repository.telemetry.TelemetryEventRepository;
import com.bookassistant.repository.telemetry.TelemetryProcedureRepository;
import com.bookassistant.repository.telemetry.TestRunRepository;
import com.bookassistant.repository.telemetry.TestFailureRepository;
import com.bookassistant.repository.telemetry.CoverageSnapshotRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Central telemetry service that acts as the project's nervous system.
 * <p>
 * <b>What:</b> Logs events, tracks errors, audits procedures, and provides
 * analytical queries for the entire application.
 * <p>
 * <b>Why:</b> Required to identify patterns in errors, track fix effectiveness,
 * and maintain a complete audit trail of all significant operations.
 * <p>
 * <b>Where:</b> Central service for all telemetry operations.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryEventRepository eventRepository;
    private final TelemetryErrorRepository errorRepository;
    private final TelemetryProcedureRepository procedureRepository;
    private final AccountLinkAuditRepository accountLinkAuditRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final SystemHealthRepository systemHealthRepository;
    private final TestRunRepository testRunRepository;
    private final TestFailureRepository testFailureRepository;
    private final CoverageSnapshotRepository coverageSnapshotRepository;

    public TelemetryService(TelemetryEventRepository eventRepository,
                            TelemetryErrorRepository errorRepository,
                            TelemetryProcedureRepository procedureRepository,
                            AccountLinkAuditRepository accountLinkAuditRepository,
                            AiInteractionRepository aiInteractionRepository,
                            SystemHealthRepository systemHealthRepository,
                            TestRunRepository testRunRepository,
                            TestFailureRepository testFailureRepository,
                            CoverageSnapshotRepository coverageSnapshotRepository) {
        this.eventRepository = eventRepository;
        this.errorRepository = errorRepository;
        this.procedureRepository = procedureRepository;
        this.accountLinkAuditRepository = accountLinkAuditRepository;
        this.aiInteractionRepository = aiInteractionRepository;
        this.systemHealthRepository = systemHealthRepository;
        this.testRunRepository = testRunRepository;
        this.testFailureRepository = testFailureRepository;
        this.coverageSnapshotRepository = coverageSnapshotRepository;
    }

    // ========================================================================
    // EVENT LOGGING
    // ========================================================================

    public TelemetryEvent logEvent(String category, String eventType, String severity,
                                   Long userId, String sessionId, Long bookId,
                                   String entityType, Long entityId,
                                   String message, String details, String stackTrace,
                                   Integer durationMs) {
        LoggingUtils.enter(log, "logEvent", "category", category);
        LoggingUtils.enter(log, "logEvent", "eventType", eventType);

        TelemetryEvent event = new TelemetryEvent();
        event.setCategory(category);
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setBookId(bookId);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setMessage(message);
        event.setDetails(details);
        event.setStackTrace(stackTrace);
        event.setDurationMs(durationMs);

        TelemetryEvent saved = eventRepository.save(event);
        LoggingUtils.exit(log, "logEvent", "id", saved.getId());
        return saved;
    }

    // ========================================================================
    // ERROR TRACKING
    // ========================================================================

    @Transactional
    public TelemetryError trackError(String errorType, String errorMessage, String category,
                                     String affectedComponent, String stackTrace) {
        LoggingUtils.enter(log, "trackError", "errorType", errorType);

        String errorHash = generateErrorHash(errorType, errorMessage);

        TelemetryError error = errorRepository.findByErrorHash(errorHash)
            .orElseGet(() -> {
                TelemetryError newError = new TelemetryError();
                newError.setErrorHash(errorHash);
                newError.setErrorType(errorType);
                newError.setErrorMessage(errorMessage);
                newError.setCategory(category);
                newError.setAffectedComponent(affectedComponent);
                return newError;
            });

        error.setLastSeenAt(LocalDateTime.now());
        error.setOccurrenceCount(error.getOccurrenceCount() + 1);

        if (stackTrace != null && error.getRootCause() == null) {
            error.setRootCause(extractRootCause(stackTrace));
        }

        TelemetryError saved = errorRepository.save(error);
        LoggingUtils.exit(log, "trackError", "errorId", saved.getId());
        return saved;
    }

    public List<TelemetryError> getActiveErrors() {
        return errorRepository.findActiveErrors();
    }

    public List<TelemetryError> getRegressionErrors() {
        return errorRepository.findByIsRegressionTrueOrderByLastSeenAtDesc();
    }

    // ========================================================================
    // PROCEDURE OUTCOMES
    // ========================================================================

    public TelemetryProcedure logProcedure(String procedureName, String category,
                                           Long userId, Long bookId,
                                           String inputSummary, String outcome,
                                           String resultSummary, Long errorId,
                                           Integer durationMs, Integer retryCount,
                                           String context) {
        LoggingUtils.enter(log, "logProcedure", "procedure", procedureName);

        TelemetryProcedure procedure = new TelemetryProcedure();
        procedure.setProcedureName(procedureName);
        procedure.setCategory(category);
        procedure.setUserId(userId);
        procedure.setBookId(bookId);
        procedure.setInputSummary(inputSummary);
        procedure.setOutcome(outcome);
        procedure.setResultSummary(resultSummary);
        procedure.setErrorId(errorId);
        procedure.setDurationMs(durationMs);
        procedure.setRetryCount(retryCount);
        procedure.setContext(context);

        TelemetryProcedure saved = procedureRepository.save(procedure);
        LoggingUtils.exit(log, "logProcedure", "procedureId", saved.getId());
        return saved;
    }

    // ========================================================================
    // ACCOUNT LINKING AUDIT
    // ========================================================================

    @Transactional
    public AccountLinkAudit auditAccountLink(Long userId, String platform, String action,
                                             String preState, String postState,
                                             String errorDetails, String resolution,
                                             Long linkedAccountId, Integer durationMs) {
        LoggingUtils.enter(log, "auditAccountLink", "userId", userId);
        LoggingUtils.enter(log, "auditAccountLink", "platform", platform);

        AccountLinkAudit audit = new AccountLinkAudit();
        audit.setUserId(userId);
        audit.setPlatform(platform);
        audit.setAction(action);
        audit.setPreState(preState);
        audit.setPostState(postState);
        audit.setErrorDetails(errorDetails);
        audit.setResolution(resolution);
        audit.setLinkedAccountId(linkedAccountId);
        audit.setDurationMs(durationMs);

        AccountLinkAudit saved = accountLinkAuditRepository.save(audit);
        LoggingUtils.exit(log, "auditAccountLink", "auditId", saved.getId());
        return saved;
    }

    public List<AccountLinkAudit> getAccountLinkFailures(Long userId) {
        return accountLinkAuditRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
            .filter(a -> "failure".equals(a.getAction()))
            .toList();
    }

    // ========================================================================
    // AI INTERACTION LOGGING
    // ========================================================================

    public AiInteraction logAiInteraction(Long userId, Long bookId, String tabUsed,
                                           String promptHash, Integer responseLength,
                                           String language, Integer durationMs,
                                           Integer tokensEstimate, Boolean success,
                                           Long errorId, Long bookOutputId,
                                           String modelUsed, Double temperature, String context) {
        LoggingUtils.enter(log, "logAiInteraction", "tab", tabUsed);

        AiInteraction interaction = new AiInteraction();
        interaction.setUserId(userId);
        interaction.setBookId(bookId);
        interaction.setTabUsed(tabUsed);
        interaction.setPromptHash(promptHash);
        interaction.setResponseLength(responseLength);
        interaction.setLanguage(language);
        interaction.setDurationMs(durationMs);
        interaction.setTokensEstimate(tokensEstimate);
        interaction.setSuccess(success);
        interaction.setErrorId(errorId);
        interaction.setBookOutputId(bookOutputId);
        interaction.setModelUsed(modelUsed);
        interaction.setTemperature(temperature);
        interaction.setContext(context);

        AiInteraction saved = aiInteractionRepository.save(interaction);
        LoggingUtils.exit(log, "logAiInteraction", "interactionId", saved.getId());
        return saved;
    }

    // ========================================================================
    // SYSTEM HEALTH
    // ========================================================================

    public SystemHealth logSystemHealth(String component, String status,
                                        Integer latencyMs, String details, Long errorId) {
        LoggingUtils.enter(log, "logSystemHealth", "component", component);

        SystemHealth health = new SystemHealth();
        health.setComponent(component);
        health.setStatus(status);
        health.setLatencyMs(latencyMs);
        health.setDetails(details);
        health.setErrorId(errorId);

        SystemHealth saved = systemHealthRepository.save(health);
        LoggingUtils.exit(log, "logSystemHealth", "healthId", saved.getId());
        return saved;
    }

    // ========================================================================
    // TEST RUN TRACKING
    // ========================================================================

    public TestRun logTestRun(Integer totalTests, Integer failures, Integer errors,
                              Integer skipped, Integer durationMs, String mavenGoal,
                              String gitCommit, String branch, String javaVersion, String osName) {
        LoggingUtils.enter(log, "logTestRun", "goal", mavenGoal);

        TestRun run = new TestRun();
        run.setTotalTests(totalTests);
        run.setFailures(failures);
        run.setErrors(errors);
        run.setSkipped(skipped);
        run.setDurationMs(durationMs);
        run.setMavenGoal(mavenGoal);
        run.setGitCommit(gitCommit);
        run.setBranch(branch);
        run.setJavaVersion(javaVersion);
        run.setOsName(osName);

        TestRun saved = testRunRepository.save(run);
        LoggingUtils.exit(log, "logTestRun", "runId", saved.getId());
        return saved;
    }

    public TestFailure logTestFailure(Long runId, String testClass, String testMethod,
                                      String errorType, String errorMessage,
                                      String stackTrace, Boolean isRegression, Long bugId) {
        LoggingUtils.enter(log, "logTestFailure", "test", testClass + "#" + testMethod);

        String errorHash = generateErrorHash(errorType, errorMessage);

        TestFailure failure = new TestFailure();
        failure.setRunId(runId);
        failure.setTestClass(testClass);
        failure.setTestMethod(testMethod);
        failure.setErrorType(errorType);
        failure.setErrorMessage(errorMessage);
        failure.setStackTrace(stackTrace);
        failure.setErrorHash(errorHash);
        failure.setIsRegression(isRegression);
        failure.setBugId(bugId);

        TestFailure saved = testFailureRepository.save(failure);
        LoggingUtils.exit(log, "logTestFailure", "failureId", saved.getId());
        return saved;
    }

    // ========================================================================
    // COVERAGE TRACKING
    // ========================================================================

    public CoverageSnapshot logCoverage(String className, BigDecimal lineCoveragePct,
                                        BigDecimal branchCoveragePct, BigDecimal methodCoveragePct,
                                        Integer linesCovered, Integer linesTotal) {
        LoggingUtils.enter(log, "logCoverage", "class", className);

        CoverageSnapshot snapshot = new CoverageSnapshot();
        snapshot.setClassName(className);
        snapshot.setLineCoveragePct(lineCoveragePct);
        snapshot.setBranchCoveragePct(branchCoveragePct);
        snapshot.setMethodCoveragePct(methodCoveragePct);
        snapshot.setLinesCovered(linesCovered);
        snapshot.setLinesTotal(linesTotal);
        snapshot.setBelowThreshold(
            (lineCoveragePct != null && lineCoveragePct.compareTo(BigDecimal.valueOf(90)) < 0) ||
            (branchCoveragePct != null && branchCoveragePct.compareTo(BigDecimal.valueOf(85)) < 0) ||
            (methodCoveragePct != null && methodCoveragePct.compareTo(BigDecimal.valueOf(90)) < 0)
        );

        CoverageSnapshot saved = coverageSnapshotRepository.save(snapshot);
        LoggingUtils.exit(log, "logCoverage", "snapshotId", saved.getId());
        return saved;
    }

    // ========================================================================
    // ANALYTICAL QUERIES
    // ========================================================================

    public List<Object[]> getProcedureStats(String procedureName, Integer days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return procedureRepository.getProcedureStatsSince(since);
    }

    public List<Object[]> getAccountLinkFailurePatterns(Integer days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return accountLinkAuditRepository.findFailurePatternsSince(since);
    }

    public List<Object[]> getAiTabStats(Integer days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return aiInteractionRepository.getAiTabStatsSince(since);
    }

    public List<Object[]> getHealthStats(Integer days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return systemHealthRepository.getHealthStatsSince(since);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private String generateErrorHash(String errorType, String errorMessage) {
        String raw = (errorType != null ? errorType : "") + "|" + (errorMessage != null ? errorMessage.substring(0, Math.min(200, errorMessage.length())) : "");
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String extractRootCause(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) {
            return "Unknown";
        }
        String[] lines = stackTrace.split("\n");
        for (String line : lines) {
            if (line.contains("Caused by:")) {
                return line.trim();
            }
        }
        return lines[0].trim();
    }
}
