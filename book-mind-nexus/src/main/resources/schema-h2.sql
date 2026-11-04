-- =============================================================================
-- BookMind Nexus - H2 Schema (for testing and local development)
-- =============================================================================
-- H2-compatible version of V1__initial_schema.sql
-- No pgvector extension (H2 doesn't support it)
-- =============================================================================

-- -------------------------------------------------
-- users
-- -------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    title          VARCHAR(500) NOT NULL,
    file_name      VARCHAR(500) NOT NULL,
    file_path      VARCHAR(1000) NOT NULL,
    page_count     INTEGER,
    full_text      VARCHAR(100000),
    language       VARCHAR(10) NOT NULL DEFAULT 'en',
    book_folder    VARCHAR(1000) NOT NULL DEFAULT '',
    tags           VARCHAR(1000) NOT NULL DEFAULT '',
    indexing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    indexing_error VARCHAR(10000),
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
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id       BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    chunk_index   INTEGER NOT NULL,
    chunk_text    VARCHAR(10000) NOT NULL,
    embedding     VARCHAR(10000),
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
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    platform         VARCHAR(50) NOT NULL,
    account_name     VARCHAR(255),
    access_token     VARCHAR(10000),
    refresh_token    VARCHAR(10000),
    token_expiry     TIMESTAMP,
    metadata         VARCHAR(10000),
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
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    linked_account_id BIGINT NOT NULL,
    platform_note_id VARCHAR(255) NOT NULL,
    content          VARCHAR(100000),
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
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id           BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    tab_used          VARCHAR(50) NOT NULL,
    linked_account_id BIGINT,
    linked_platform   VARCHAR(50),
    output_text       VARCHAR(100000) NOT NULL,
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
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
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
-- End of schema-h2.sql
-- =============================================================================
