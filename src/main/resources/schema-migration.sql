-- Migration: add book_outputs table and new columns to books
-- Run this against an existing book_assistant database

-- Add new columns to books table if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'books' AND column_name = 'book_folder') THEN
        ALTER TABLE books ADD COLUMN book_folder VARCHAR(1000) NOT NULL DEFAULT '';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'books' AND column_name = 'tags') THEN
        ALTER TABLE books ADD COLUMN tags TEXT[] DEFAULT '{}';
    END IF;
END $$;

-- Create book_outputs table
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

-- Create indexes (ignore if they already exist)
CREATE INDEX IF NOT EXISTS idx_book_outputs_book_id ON book_outputs(book_id);
CREATE INDEX IF NOT EXISTS idx_book_outputs_user_id ON book_outputs(user_id);
CREATE INDEX IF NOT EXISTS idx_book_outputs_tab_used ON book_outputs(tab_used);
CREATE INDEX IF NOT EXISTS idx_books_user_folder ON books(user_id, book_folder);
