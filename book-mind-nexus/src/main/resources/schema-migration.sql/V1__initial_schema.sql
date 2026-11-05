-- =============================================================================
-- BookMind Nexus - Initial Database Schema (PostgreSQL 16 + pgvector)
-- =============================================================================
-- Creates all tables required by the JPA entities:
--   users, books, book_chunks, book_outputs, external_notes,
--   linked_accounts, reading_progress
-- =============================================================================
-- pgvector extension is enabled on the Neon database directly
-- =============================================================================
-- users
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    name           VARCHAR(255),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------
-- books
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS books (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    title          VARCHAR(500) NOT NULL,
    file_name      VARCHAR(500) NOT NULL,
    file_path      VARCHAR(1000) NOT NULL,
    page_count     INTEGER,
    full_text      TEXT,
    language       VARCHAR(10) NOT NULL DEFAULT 'en',
    book_folder    VARCHAR(1000) NOT NULL DEFAULT '',
    tags           TEXT[] NOT NULL DEFAULT '{}',
    indexing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    indexing_error TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_books_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_books_user_id ON books(user_id);
CREATE INDEX IF NOT EXISTS idx_books_user_id_title ON books(user_id, title);

-- -------------------------------------------------
-- book_chunks
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS book_chunks (
    id            BIGSERIAL PRIMARY KEY,
    book_id       BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    chunk_index   INTEGER NOT NULL,
    chunk_text    TEXT NOT NULL,
    embedding     TEXT,
    page_start    INTEGER,
    page_end      INTEGER,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_chunks_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    CONSTRAINT fk_book_chunks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_book_chunks_book_id ON book_chunks(book_id);
CREATE INDEX IF NOT EXISTS idx_book_chunks_user_id ON book_chunks(user_id);

-- -------------------------------------------------
-- linked_accounts
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS linked_accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    platform         VARCHAR(50) NOT NULL,
    account_name     VARCHAR(255),
    access_token     TEXT,
    refresh_token    TEXT,
    token_expiry     TIMESTAMP,
    metadata         TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_linked_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_linked_accounts_user_id ON linked_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_linked_accounts_user_platform ON linked_accounts(user_id, platform);

-- -------------------------------------------------
-- external_notes
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS external_notes (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    linked_account_id BIGINT NOT NULL,
    platform_note_id VARCHAR(255) NOT NULL,
    content          TEXT,
    title            VARCHAR(500),
    url              VARCHAR(1000),
    synced_at        TIMESTAMP NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_external_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_external_notes_linked_account FOREIGN KEY (linked_account_id) REFERENCES linked_accounts(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_external_notes_user_id ON external_notes(user_id);
CREATE INDEX IF NOT EXISTS idx_external_notes_platform_note_id ON external_notes(user_id, platform_note_id);

-- -------------------------------------------------
-- book_outputs
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS book_outputs (
    id                BIGSERIAL PRIMARY KEY,
    book_id           BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    tab_used          VARCHAR(50) NOT NULL,
    linked_account_id BIGINT,
    linked_platform   VARCHAR(50),
    output_text       TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_outputs_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    CONSTRAINT fk_book_outputs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_book_outputs_linked_account FOREIGN KEY (linked_account_id) REFERENCES linked_accounts(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_book_outputs_user_id ON book_outputs(user_id);
CREATE INDEX IF NOT EXISTS idx_book_outputs_book_id ON book_outputs(book_id);

-- -------------------------------------------------
-- reading_progress
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS reading_progress (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    book_id      BIGINT NOT NULL,
    current_page INTEGER NOT NULL DEFAULT 1,
    total_pages  INTEGER,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reading_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_progress_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    CONSTRAINT uk_reading_progress_user_book UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_reading_progress_user_id ON reading_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_reading_progress_book_id ON reading_progress(book_id);

-- =============================================================================
-- End of V1__initial_schema.sql
-- =============================================================================
