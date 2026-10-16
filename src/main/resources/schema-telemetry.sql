-- BookMind Nexus - Telemetry & Observability Schema
-- This schema acts as the project's "body" - a central nervous system
-- that logs every significant event, error, and procedure outcome.

-- Create schema first
CREATE SCHEMA IF NOT EXISTS telemetry;

-- ============================================================================
-- SECTION 1: EVENT LOG (the heartbeat)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.events (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    category VARCHAR(50) NOT NULL,  -- auth, upload, ai, account, navigation, system
    event_type VARCHAR(100) NOT NULL,  -- login_success, link_account_attempt, ai_response, etc.
    severity VARCHAR(20) NOT NULL DEFAULT 'info',  -- debug, info, warning, error, critical
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(100),
    book_id BIGINT REFERENCES books(id) ON DELETE SET NULL,
    entity_type VARCHAR(50),  -- linked_account, book_output, user, etc.
    entity_id BIGINT,
    message TEXT NOT NULL,
    details TEXT,  -- flexible payload for context
    stack_trace TEXT,
    duration_ms INT,
    ip_address VARCHAR(255),
    user_agent TEXT
);

CREATE INDEX idx_events_occurred_at ON telemetry.events(occurred_at);
CREATE INDEX idx_events_category ON telemetry.events(category);
CREATE INDEX idx_events_severity ON telemetry.events(severity);
CREATE INDEX idx_events_user_id ON telemetry.events(user_id);
CREATE INDEX idx_events_entity ON telemetry.events(entity_type, entity_id);
CREATE INDEX idx_events_message_gin ON telemetry.events USING gin(to_tsvector('english', message));

-- ============================================================================
-- SECTION 2: ERROR REGISTRY (the immune system)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.errors (
    id BIGSERIAL PRIMARY KEY,
    first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_hash VARCHAR(64) NOT NULL,  -- SHA256 of error_type + first 200 chars of message
    error_type VARCHAR(100) NOT NULL,  -- ConstraintViolationException, NullPointerException, etc.
    error_message TEXT NOT NULL,
    category VARCHAR(50),  -- database, validation, auth, ai, integration
    root_cause TEXT,  -- analyzed root cause
    affected_component VARCHAR(200),  -- AccountController.linkAccount, etc.
    status VARCHAR(20) DEFAULT 'open',  -- open, investigating, fixed, wont-fix, known
    severity VARCHAR(20) DEFAULT 'major',  -- critical, major, minor
    occurrence_count INT DEFAULT 1,
    is_regression BOOLEAN DEFAULT FALSE,
    regression_of BIGINT REFERENCES telemetry.errors(id),
    fix_description TEXT,
    fix_commit VARCHAR(40),
    fixed_at TIMESTAMP
);

CREATE INDEX idx_errors_error_hash ON telemetry.errors(error_hash);
CREATE INDEX idx_errors_status ON telemetry.errors(status);
CREATE INDEX idx_errors_category ON telemetry.errors(category);
CREATE INDEX idx_errors_affected_component ON telemetry.errors(affected_component);
CREATE INDEX idx_errors_is_regression ON telemetry.errors(is_regression);

-- ============================================================================
-- SECTION 3: PROCEDURE OUTCOMES (the memory)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.procedures (
    id BIGSERIAL PRIMARY KEY,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    procedure_name VARCHAR(200) NOT NULL,  -- link_account, upload_book, generate_summary
    category VARCHAR(50) NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    book_id BIGINT REFERENCES books(id) ON DELETE SET NULL,
    input_summary TEXT,  -- sanitized input parameters
    outcome VARCHAR(20) NOT NULL,  -- success, failure, partial, timeout
    result_summary TEXT,  -- what was produced
    error_id BIGINT REFERENCES telemetry.errors(id),
    duration_ms INT,
    retry_count INT DEFAULT 0,
    context TEXT
);

CREATE INDEX idx_procedures_executed_at ON telemetry.procedures(executed_at);
CREATE INDEX idx_procedures_name ON telemetry.procedures(procedure_name);
CREATE INDEX idx_procedures_outcome ON telemetry.procedures(outcome);
CREATE INDEX idx_procedures_user_id ON telemetry.procedures(user_id);

-- ============================================================================
-- SECTION 4: ACCOUNT LINKING AUDIT (complex issue tracking)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.account_link_audit (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,  -- attempt, success, failure, unlink
    pre_state TEXT,  -- account state before action
    post_state TEXT,  -- account state after action
    error_id BIGINT REFERENCES telemetry.errors(id),
    error_details TEXT,
    resolution VARCHAR(50),  -- created_new, updated_existing, idempotent_skip, blocked
    linked_account_id BIGINT REFERENCES linked_accounts(id) ON DELETE SET NULL,
    duration_ms INT,
    metadata TEXT
);

CREATE INDEX idx_account_link_audit_user_id ON telemetry.account_link_audit(user_id);
CREATE INDEX idx_account_link_audit_platform ON telemetry.account_link_audit(platform);
CREATE INDEX idx_account_link_audit_action ON telemetry.account_link_audit(action);
CREATE INDEX idx_account_link_audit_occurred_at ON telemetry.account_link_audit(occurred_at);

-- ============================================================================
-- SECTION 5: AI INTERACTION LOG (traceability of outputs)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.ai_interactions (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    book_id BIGINT REFERENCES books(id) ON DELETE SET NULL,
    tab_used VARCHAR(50) NOT NULL,
    prompt_hash VARCHAR(64),  -- SHA256 of sanitized prompt
    response_length INT,
    language VARCHAR(10),
    duration_ms INT,
    tokens_estimate INT,
    success BOOLEAN DEFAULT TRUE,
    error_id BIGINT REFERENCES telemetry.errors(id),
    book_output_id BIGINT REFERENCES book_outputs(id) ON DELETE SET NULL,
    model_used VARCHAR(100),
    temperature DOUBLE PRECISION,
    context TEXT
);

CREATE INDEX idx_ai_interactions_occurred_at ON telemetry.ai_interactions(occurred_at);
CREATE INDEX idx_ai_interactions_user_id ON telemetry.ai_interactions(user_id);
CREATE INDEX idx_ai_interactions_tab_used ON telemetry.ai_interactions(tab_used);
CREATE INDEX idx_ai_interactions_success ON telemetry.ai_interactions(success);

-- ============================================================================
-- SECTION 6: SYSTEM HEALTH (the pulse)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.system_health (
    id BIGSERIAL PRIMARY KEY,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    component VARCHAR(100) NOT NULL,  -- database, ai_api, file_system, cache
    status VARCHAR(20) NOT NULL,  -- healthy, degraded, down
    latency_ms INT,
    details TEXT,
    error_id BIGINT REFERENCES telemetry.errors(id)
);

CREATE INDEX idx_system_health_checked_at ON telemetry.health(checked_at);
CREATE INDEX idx_system_health_component ON telemetry.health(component);
CREATE INDEX idx_system_health_status ON telemetry.health(status);

-- ============================================================================
-- SECTION 7: COVERAGE SNAPSHOTS (test quality tracking)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.coverage_snapshots (
    id BIGSERIAL PRIMARY KEY,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    class_name VARCHAR(200) NOT NULL,
    line_coverage_pct DECIMAL(5,2),
    branch_coverage_pct DECIMAL(5,2),
    method_coverage_pct DECIMAL(5,2),
    lines_covered INT,
    lines_total INT,
    below_threshold BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_coverage_class_name ON telemetry.coverage_snapshots(class_name);
CREATE INDEX idx_coverage_below_threshold ON telemetry.coverage_snapshots(below_threshold);

-- ============================================================================
-- SECTION 8: TEST RUNS (CI/CD tracking)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.test_runs (
    id BIGSERIAL PRIMARY KEY,
    run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_tests INT NOT NULL,
    failures INT NOT NULL,
    errors INT NOT NULL,
    skipped INT NOT NULL,
    duration_ms INT NOT NULL,
    maven_goal VARCHAR(100) NOT NULL,
    git_commit VARCHAR(40),
    branch VARCHAR(100),
    java_version VARCHAR(20),
    os_name VARCHAR(100)
);

CREATE INDEX idx_test_runs_run_at ON telemetry.test_runs(run_at);

-- ============================================================================
-- SECTION 9: TEST FAILURES (linked to runs and errors)
-- ============================================================================
CREATE TABLE IF NOT EXISTS telemetry.test_failures (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES telemetry.test_runs(id) ON DELETE CASCADE,
    test_class VARCHAR(200) NOT NULL,
    test_method VARCHAR(200) NOT NULL,
    error_type VARCHAR(100),
    error_message TEXT,
    stack_trace TEXT,
    error_hash VARCHAR(64),
    is_regression BOOLEAN DEFAULT FALSE,
    bug_id BIGINT REFERENCES telemetry.errors(id)
);

CREATE INDEX idx_test_failures_run_id ON telemetry.test_failures(run_id);
CREATE INDEX idx_test_failures_error_hash ON telemetry.test_failures(error_hash);
CREATE INDEX idx_test_failures_is_regression ON telemetry.test_failures(is_regression);

-- ============================================================================
-- HELPER FUNCTIONS
-- ============================================================================

-- Generate error hash for deduplication
CREATE OR REPLACE FUNCTION telemetry.generate_error_hash(
    p_error_type VARCHAR,
    p_error_message TEXT
) RETURNS VARCHAR AS $$
BEGIN
    RETURN encode(digest(
        coalesce(p_error_type, '') || '|' || coalesce(left(p_error_message, 200), ''),
        'sha256'
    ), 'hex');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Record an event with automatic error deduplication
CREATE OR REPLACE FUNCTION telemetry.record_event(
    p_category VARCHAR,
    p_event_type VARCHAR,
    p_severity VARCHAR DEFAULT 'info',
    p_user_id BIGINT DEFAULT NULL,
    p_session_id VARCHAR DEFAULT NULL,
    p_book_id BIGINT DEFAULT NULL,
    p_entity_type VARCHAR DEFAULT NULL,
    p_entity_id BIGINT DEFAULT NULL,
    p_message TEXT DEFAULT NULL,
    p_details TEXT DEFAULT NULL,
    p_stack_trace TEXT DEFAULT NULL,
    p_duration_ms INT DEFAULT NULL,
    p_ip_address VARCHAR(255) DEFAULT NULL,
    p_user_agent TEXT DEFAULT NULL
) RETURNS BIGINT AS $$
DECLARE
    v_event_id BIGINT;
    v_error_id BIGINT := NULL;
    v_error_hash VARCHAR(64);
BEGIN
    -- Insert event
    INSERT INTO telemetry.events (
        category, event_type, severity, user_id, session_id, book_id,
        entity_type, entity_id, message, details, stack_trace, duration_ms,
        ip_address, user_agent
    ) VALUES (
        p_category, p_event_type, p_severity, p_user_id, p_session_id, p_book_id,
        p_entity_type, p_entity_id, p_message, p_details, p_stack_trace, p_duration_ms,
        p_ip_address, p_user_agent
    ) RETURNING id INTO v_event_id;

    -- If this is an error or warning, track in errors registry
    IF p_severity IN ('error', 'critical') AND p_stack_trace IS NOT NULL THEN
        v_error_hash := telemetry.generate_error_hash(
            p_event_type,
            p_stack_trace
        );

        -- Try to find existing error or create new one
        INSERT INTO telemetry.errors (error_hash, error_type, error_message, category, affected_component, occurrence_count)
        VALUES (
            v_error_hash,
            p_event_type,
            p_message,
            p_category,
            p_entity_type,
            1
        )
        ON CONFLICT (error_hash) DO UPDATE SET
            last_seen_at = CURRENT_TIMESTAMP,
            occurrence_count = telemetry.errors.occurrence_count + 1
        RETURNING id INTO v_error_id;

        -- Link event to error via details text
        UPDATE telemetry.events SET details = 
            COALESCE(details, '') || CASE WHEN COALESCE(details, '') = '' THEN '' ELSE ',' END || '"error_id":' || v_error_id
        WHERE id = v_event_id;
    END IF;

    RETURN v_event_id;
END;
$$ LANGUAGE plpgsql;

-- Get error trend analysis
CREATE OR REPLACE FUNCTION telemetry.get_error_trend(
    p_days INT DEFAULT 7
) RETURNS TABLE (
    error_hash VARCHAR,
    error_type VARCHAR,
    category VARCHAR,
    occurrence_count BIGINT,
    first_seen TIMESTAMP,
    last_seen TIMESTAMP,
    days_active INT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        e.error_hash,
        e.error_type,
        e.category,
        e.occurrence_count::BIGINT,
        e.first_seen_at,
        e.last_seen_at,
        EXTRACT(DAY FROM (CURRENT_TIMESTAMP - e.first_seen_at))::INT AS days_active
    FROM telemetry.errors e
    WHERE e.last_seen_at >= CURRENT_TIMESTAMP - (p_days || ' days')::INTERVAL
    ORDER BY e.occurrence_count DESC;
END;
$$ LANGUAGE plpgsql;

-- Get procedure success rate
CREATE OR REPLACE FUNCTION telemetry.get_procedure_stats(
    p_procedure_name VARCHAR DEFAULT NULL,
    p_days INT DEFAULT 7
) RETURNS TABLE (
    procedure_name VARCHAR,
    total_executions BIGINT,
    successes BIGINT,
    failures BIGINT,
    success_rate NUMERIC,
    avg_duration_ms NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.procedure_name,
        COUNT(*)::BIGINT AS total_executions,
        COUNT(*) FILTER (WHERE p.outcome = 'success')::BIGINT AS successes,
        COUNT(*) FILTER (WHERE p.outcome = 'failure')::BIGINT AS failures,
        ROUND(
            COUNT(*) FILTER (WHERE p.outcome = 'success')::NUMERIC / COUNT(*) * 100,
            2
        ) AS success_rate,
        ROUND(AVG(p.duration_ms), 2) AS avg_duration_ms
    FROM telemetry.procedures p
    WHERE p.executed_at >= CURRENT_TIMESTAMP - (p_days || ' days')::INTERVAL
        AND (p_procedure_name IS NULL OR p.procedure_name = p_procedure_name)
    GROUP BY p.procedure_name
    ORDER BY success_rate ASC;
END;
$$ LANGUAGE plpgsql;

-- Get account linking health
CREATE OR REPLACE FUNCTION telemetry.get_account_linking_health()
RETURNS TABLE (
    platform VARCHAR,
    total_attempts BIGINT,
    successes BIGINT,
    failures BIGINT,
    idempotent_skips BIGINT,
        success_rate NUMERIC,
        common_errors TEXT
    ) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.platform,
        COUNT(*)::BIGINT AS total_attempts,
        COUNT(*) FILTER (WHERE al.action = 'success')::BIGINT AS successes,
        COUNT(*) FILTER (WHERE al.action = 'failure')::BIGINT AS failures,
        COUNT(*) FILTER (WHERE al.resolution = 'idempotent_skip')::BIGINT AS idempotent_skips,
        ROUND(
            COUNT(*) FILTER (WHERE al.action = 'success')::NUMERIC / COUNT(*) * 100,
            2
        ) AS success_rate,
        jsonb_object_agg(
            COALESCE(e.error_type, 'unknown'),
            COUNT(*)
        ) FILTER (WHERE e.error_type IS NOT NULL)::TEXT AS common_errors
    FROM telemetry.account_link_audit al
    LEFT JOIN telemetry.errors e ON al.error_id = e.id
    WHERE al.occurred_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
    GROUP BY al.platform
    ORDER BY success_rate ASC;
END;
$$ LANGUAGE plpgsql;
