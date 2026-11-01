-- BookMind Nexus - Database Triggers for Self-Updating Telemetry
-- These triggers automatically populate telemetry tables when data changes,
-- without the application explicitly calling telemetryService.

-- ============================================================================
-- HELPER FUNCTION: Generate JSON state snapshot
-- ============================================================================
CREATE OR REPLACE FUNCTION telemetry.row_to_json(p_table text, p_row_id bigint)
RETURNS TEXT AS $$
DECLARE
    v_json TEXT;
BEGIN
    EXECUTE format('SELECT row_to_json(t) FROM %I t WHERE id = %L', p_table, p_row_id) INTO v_json;
    RETURN v_json;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER 1: linked_accounts audit
-- ============================================================================
CREATE OR REPLACE FUNCTION telemetry.audit_linked_account_change()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id BIGINT;
    v_platform VARCHAR(50);
    v_action VARCHAR(20);
    v_pre_state JSONB := NULL;
    v_post_state JSONB := NULL;
    v_error_id BIGINT := NULL;
    v_resolution VARCHAR(50) := NULL;
    v_linked_account_id BIGINT := NULL;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_user_id := NEW.user_id;
        v_platform := NEW.platform;
        v_action := 'success';
        v_post_state := jsonb_build_object(
            'id', NEW.id,
            'platform', NEW.platform,
            'account_name', NEW.account_name,
            'created_at', NEW.created_at
        );
        v_resolution := 'created_new';
        v_linked_account_id := NEW.id;
    ELSIF TG_OP = 'UPDATE' THEN
        v_user_id := NEW.user_id;
        v_platform := NEW.platform;
        v_action := 'success';
        v_pre_state := jsonb_build_object(
            'id', OLD.id,
            'platform', OLD.platform,
            'account_name', OLD.account_name,
            'created_at', OLD.created_at
        );
        v_post_state := jsonb_build_object(
            'id', NEW.id,
            'platform', NEW.platform,
            'account_name', NEW.account_name,
            'created_at', NEW.created_at
        );
        v_resolution := 'updated_existing';
        v_linked_account_id := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        v_user_id := OLD.user_id;
        v_platform := OLD.platform;
        v_action := 'unlink';
        v_pre_state := jsonb_build_object(
            'id', OLD.id,
            'platform', OLD.platform,
            'account_name', OLD.account_name,
            'created_at', OLD.created_at
        );
        v_resolution := 'unlinked';
        v_linked_account_id := OLD.id;
    END IF;

    INSERT INTO telemetry.account_link_audit (
        user_id, platform, action, pre_state, post_state,
        error_id, error_details, resolution, linked_account_id
    ) VALUES (
        v_user_id, v_platform, v_action, v_pre_state, v_post_state,
        v_error_id, NULL, v_resolution, v_linked_account_id
    );

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_linked_accounts_audit
AFTER INSERT OR UPDATE OR DELETE ON linked_accounts
FOR EACH ROW EXECUTE FUNCTION telemetry.audit_linked_account_change();

-- ============================================================================
-- TRIGGER 2: book_outputs audit
-- ============================================================================
CREATE OR REPLACE FUNCTION telemetry.audit_book_output_change()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id BIGINT;
    v_book_id BIGINT;
    v_tab_used VARCHAR(50);
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_user_id := NEW.user_id;
        v_book_id := NEW.book_id;
        v_tab_used := NEW.tab_used;

        INSERT INTO telemetry.ai_interactions (
            user_id, book_id, tab_used, prompt_hash, response_length,
            language, success, book_output_id
        ) VALUES (
            v_user_id, v_book_id, v_tab_used,
            encode(digest(NEW.output_text, 'sha256'), 'hex'),
            length(NEW.output_text),
            'auto',
            TRUE,
            NEW.id
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_book_outputs_audit
AFTER INSERT ON book_outputs
FOR EACH ROW EXECUTE FUNCTION telemetry.audit_book_output_change();

-- ============================================================================
-- TRIGGER 3: books audit (uploads)
-- ============================================================================
CREATE OR REPLACE FUNCTION telemetry.audit_book_change()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id BIGINT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_user_id := NEW.user_id;

        INSERT INTO telemetry.procedures (
            procedure_name, category, user_id, book_id,
            input_summary, outcome, result_summary
        ) VALUES (
            'upload_book',
            'upload',
            v_user_id,
            NEW.id,
            jsonb_build_object('fileName', NEW.file_name, 'pageCount', NEW.page_count),
            'success',
            'Book uploaded successfully'
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_books_audit
AFTER INSERT ON books
FOR EACH ROW EXECUTE FUNCTION telemetry.audit_book_change();

-- ============================================================================
-- TRIGGER 4: users audit (registration)
-- ============================================================================
CREATE OR REPLACE FUNCTION telemetry.audit_user_change()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO telemetry.procedures (
            procedure_name, category, user_id,
            input_summary, outcome, result_summary
        ) VALUES (
            'register_user',
            'auth',
            NEW.id,
            jsonb_build_object('email', NEW.email, 'name', NEW.name),
            'success',
            'User registered successfully'
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_audit
AFTER INSERT ON users
FOR EACH ROW EXECUTE FUNCTION telemetry.audit_user_change();

-- ============================================================================
-- SELF-QUERYING VIEWS (the "manual")
-- ============================================================================

-- View 1: Account linking health dashboard
CREATE OR REPLACE VIEW telemetry.v_account_linking_health AS
SELECT 
    platform,
    COUNT(*) as total_attempts,
    COUNT(*) FILTER (WHERE action = 'success') as successes,
    COUNT(*) FILTER (WHERE action = 'failure') as failures,
    COUNT(*) FILTER (WHERE resolution = 'idempotent_skip') as idempotent_skips,
    ROUND(
        COUNT(*) FILTER (WHERE action = 'success')::NUMERIC / COUNT(*) * 100,
        2
    ) as success_rate,
    MAX(occurred_at) as last_attempt
FROM telemetry.account_link_audit
GROUP BY platform
ORDER BY success_rate ASC;

-- View 2: Open errors dashboard
CREATE OR REPLACE VIEW telemetry.v_open_errors AS
SELECT 
    error_hash,
    error_type,
    error_message,
    category,
    affected_component,
    occurrence_count,
    first_seen_at,
    last_seen_at,
    EXTRACT(DAY FROM (CURRENT_TIMESTAMP - first_seen_at))::INT as days_active,
    status,
    severity
FROM telemetry.errors
WHERE status != 'fixed'
ORDER BY occurrence_count DESC;

-- View 3: Procedure success rates
CREATE OR REPLACE VIEW telemetry.v_procedure_stats AS
SELECT 
    procedure_name,
    category,
    COUNT(*) as total_executions,
    COUNT(*) FILTER (WHERE outcome = 'success') as successes,
    COUNT(*) FILTER (WHERE outcome = 'failure') as failures,
    ROUND(
        COUNT(*) FILTER (WHERE outcome = 'success')::NUMERIC / COUNT(*) * 100,
        2
    ) as success_rate,
    AVG(duration_ms) as avg_duration_ms
FROM telemetry.procedures
GROUP BY procedure_name, category
ORDER BY success_rate ASC;

-- View 4: AI interaction stats
CREATE OR REPLACE VIEW telemetry.v_ai_stats AS
SELECT 
    tab_used,
    COUNT(*) as total_interactions,
    AVG(duration_ms) as avg_duration_ms,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failures,
    ROUND(
        SUM(CASE WHEN success = true THEN 1 ELSE 0 END)::NUMERIC / COUNT(*) * 100,
        2
    ) as success_rate
FROM telemetry.ai_interactions
GROUP BY tab_used
ORDER BY failures DESC;

-- View 5: Test run history
CREATE OR REPLACE VIEW telemetry.v_test_history AS
SELECT 
    run_at,
    total_tests,
    failures,
    errors,
    skipped,
    duration_ms,
    maven_goal,
    ROUND((failures + errors)::NUMERIC / NULLIF(total_tests, 0) * 100, 2) as failure_rate
FROM telemetry.test_runs
ORDER BY run_at DESC;

-- ============================================================================
-- AUTO-REFRESH FUNCTION FOR MATERIALIZED VIEWS
-- ============================================================================

CREATE OR REPLACE FUNCTION telemetry.refresh_dashboards()
RETURNS void AS $$
BEGIN
    -- Refresh materialized views if any are added later
    -- Currently using regular views which are always up-to-date
    NULL;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- RETENTION POLICY (optional - keeps database from growing forever)
-- ============================================================================

-- Keep events for 90 days, errors for 1 year, procedures for 90 days
-- Uncomment to enable automatic cleanup:
-- CREATE OR REPLACE FUNCTION telemetry.apply_retention_policy()
-- RETURNS void AS $$
-- BEGIN
--     DELETE FROM telemetry.events WHERE occurred_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
--     DELETE FROM telemetry.procedures WHERE executed_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
--     DELETE FROM telemetry.ai_interactions WHERE occurred_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
--     DELETE FROM telemetry.system_health WHERE checked_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
--     -- Keep errors and test runs indefinitely for historical analysis
-- END;
-- $$ LANGUAGE plpgsql;
