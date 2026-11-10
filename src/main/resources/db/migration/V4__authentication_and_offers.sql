ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE interview_schedules ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED';
ALTER TABLE interview_schedules ADD COLUMN cancelled_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS security_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    CONSTRAINT fk_security_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    replaced_by_hash VARCHAR(64),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS offers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL UNIQUE,
    package_details VARCHAR(255),
    package_amount DOUBLE,
    joining_date TIMESTAMP NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_offer_application FOREIGN KEY (application_id) REFERENCES applications(id)
);
