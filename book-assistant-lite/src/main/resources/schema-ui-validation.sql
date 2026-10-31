-- BookMind Nexus - UI Validation Schema
-- Stores HTML snapshots, anomalies, and screenshot baselines for UI quality checks.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- SECTION 1: UI SNAPSHOTS
-- ============================================================================
CREATE TABLE IF NOT EXISTS ui_snapshots (
    id BIGSERIAL PRIMARY KEY,
    page_path VARCHAR(255) NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(100),
    html_content TEXT NOT NULL,
    snapshot_type VARCHAR(50) NOT NULL DEFAULT 'current',
    viewport_width INTEGER DEFAULT 1920,
    viewport_height INTEGER DEFAULT 1080,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ui_snapshots_page ON ui_snapshots(page_path);
CREATE INDEX idx_ui_snapshots_user ON ui_snapshots(user_id);
CREATE INDEX idx_ui_snapshots_created ON ui_snapshots(created_at);

-- ============================================================================
-- SECTION 2: UI ANOMALIES
-- ============================================================================
CREATE TABLE IF NOT EXISTS ui_anomalies (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT REFERENCES ui_snapshots(id) ON DELETE CASCADE,
    page_path VARCHAR(255) NOT NULL,
    anomaly_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'warning',
    description TEXT NOT NULL,
    element_selector TEXT,
    expected_value TEXT,
    actual_value TEXT,
    remediation TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ui_anomalies_page ON ui_anomalies(page_path);
CREATE INDEX idx_ui_anomalies_severity ON ui_anomalies(severity);
CREATE INDEX idx_ui_anomalies_created ON ui_anomalies(created_at);

-- ============================================================================
-- SECTION 3: SCREENSHOT BASELINES
-- ============================================================================
CREATE TABLE IF NOT EXISTS ui_screenshot_baselines (
    id BIGSERIAL PRIMARY KEY,
    page_path VARCHAR(255) NOT NULL,
    user_state VARCHAR(50) NOT NULL DEFAULT 'anonymous',
    screenshot_path VARCHAR(1000) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    diff_threshold INTEGER DEFAULT 5,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(page_path, user_state)
);

CREATE TABLE IF NOT EXISTS ui_screenshot_comparisons (
    id BIGSERIAL PRIMARY KEY,
    baseline_id BIGINT REFERENCES ui_screenshot_baselines(id) ON DELETE CASCADE,
    snapshot_id BIGINT REFERENCES ui_snapshots(id) ON DELETE CASCADE,
    diff_percentage NUMERIC(5,2),
    diff_pixels INTEGER,
    threshold INTEGER DEFAULT 5,
    passed BOOLEAN,
    diff_image_path VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ui_baselines_page ON ui_screenshot_baselines(page_path);
CREATE INDEX idx_ui_comparisons_baseline ON ui_screenshot_comparisons(baseline_id);

-- ============================================================================
-- SECTION 4: ELEMENT REGISTRY (expected elements per page)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ui_element_registry (
    id BIGSERIAL PRIMARY KEY,
    page_path VARCHAR(255) NOT NULL,
    user_state VARCHAR(50) NOT NULL DEFAULT 'anonymous',
    element_type VARCHAR(50) NOT NULL,
    element_selector TEXT NOT NULL,
    expected_text TEXT,
    expected_href TEXT,
    expected_visible BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(page_path, user_state, element_selector)
);

CREATE INDEX idx_ui_element_registry_page ON ui_element_registry(page_path, user_state);

-- ============================================================================
-- SECTION 5: VALIDATION SESSION LOG
-- ============================================================================
CREATE TABLE IF NOT EXISTS ui_validation_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    pages_validated INTEGER DEFAULT 0,
    anomalies_found INTEGER DEFAULT 0,
    screenshots_captured INTEGER DEFAULT 0,
    overall_passed BOOLEAN,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
