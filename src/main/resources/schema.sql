-- Book Assistant Lite - Fresh PostgreSQL Schema
-- Run this once against the new book_assistant database

-- Required extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Linked external accounts (Notion, Google Keep, etc.)
CREATE TABLE IF NOT EXISTS linked_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    account_name VARCHAR(255),
    access_token TEXT,
    refresh_token TEXT,
    token_expiry TIMESTAMP,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, platform)
);

-- Uploaded books
CREATE TABLE IF NOT EXISTS books (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    page_count INTEGER,
    full_text TEXT,
    language VARCHAR(10) DEFAULT 'en',
    book_folder VARCHAR(1000) NOT NULL DEFAULT '',
    tags TEXT[] DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI outputs per book entry (tab_used, linked account tracking, output text)
CREATE TABLE IF NOT EXISTS book_outputs (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tab_used VARCHAR(50) NOT NULL,
    linked_account_id BIGINT REFERENCES linked_accounts(id) ON DELETE SET NULL,
    linked_platform VARCHAR(50),
    output_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Reading progress per user per book
CREATE TABLE IF NOT EXISTS reading_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    current_page INTEGER DEFAULT 1,
    total_pages INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, book_id)
);

-- External notes synced from linked platforms
CREATE TABLE IF NOT EXISTS external_notes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    linked_account_id BIGINT NOT NULL REFERENCES linked_accounts(id) ON DELETE CASCADE,
    platform_note_id VARCHAR(255) NOT NULL,
    content TEXT,
    title VARCHAR(500),
    url VARCHAR(1000),
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, linked_account_id, platform_note_id)
);

-- Indexes for common queries
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_books_user_id ON books(user_id);
CREATE INDEX idx_books_user_folder ON books(user_id, book_folder);
CREATE INDEX idx_book_outputs_book_id ON book_outputs(book_id);
CREATE INDEX idx_book_outputs_user_id ON book_outputs(user_id);
CREATE INDEX idx_book_outputs_tab_used ON book_outputs(tab_used);
CREATE INDEX idx_reading_progress_user_book ON reading_progress(user_id, book_id);
CREATE INDEX idx_external_notes_user_id ON external_notes(user_id);
CREATE INDEX idx_linked_accounts_user_platform ON linked_accounts(user_id, platform);

-- RAG chunks with embeddings
CREATE TABLE IF NOT EXISTS book_chunks (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(768),
    page_start INTEGER,
    page_end INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(book_id, chunk_index)
);

CREATE INDEX idx_book_chunks_book_id ON book_chunks(book_id);
CREATE INDEX idx_book_chunks_user_id ON book_chunks(user_id);
CREATE INDEX idx_book_chunks_embedding ON book_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

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

-- ============================================================================
-- SECTION 6: CONSOLE OUTPUT CAPTURE
-- ============================================================================
CREATE TABLE IF NOT EXISTS console_entries (
    id BIGSERIAL PRIMARY KEY,
    level VARCHAR(20) NOT NULL,
    source VARCHAR(100),
    message TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_console_entries_level ON console_entries(level);
CREATE INDEX idx_console_entries_source ON console_entries(source);
CREATE INDEX idx_console_entries_occurred_at ON console_entries(occurred_at);
