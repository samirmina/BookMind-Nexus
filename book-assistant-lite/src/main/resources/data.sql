-- Seed demo user for development
INSERT INTO users (email, password_hash, name, created_at, updated_at)
VALUES ('demo@bookassistant.local', 'dev-hash-change-in-production', 'Demo User', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;
