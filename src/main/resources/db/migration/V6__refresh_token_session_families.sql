ALTER TABLE refresh_tokens ADD COLUMN session_id VARCHAR(36) NULL;

UPDATE refresh_tokens
SET revoked_at = CURRENT_TIMESTAMP
WHERE session_id IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);
