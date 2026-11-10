ALTER TABLE student_profiles ADD COLUMN phone VARCHAR(50);
ALTER TABLE student_profiles ADD COLUMN education VARCHAR(255);
ALTER TABLE student_profiles ADD COLUMN graduation_year INT;
ALTER TABLE student_profiles ADD COLUMN current_semester INT;
ALTER TABLE student_profiles ADD COLUMN skills TEXT;
ALTER TABLE student_profiles ADD COLUMN certifications TEXT;
ALTER TABLE student_profiles ADD COLUMN projects TEXT;
ALTER TABLE student_profiles ADD COLUMN experience TEXT;
ALTER TABLE student_profiles ADD COLUMN linkedin_url VARCHAR(255);
ALTER TABLE student_profiles ADD COLUMN github_url VARCHAR(255);
ALTER TABLE student_profiles ADD COLUMN portfolio_url VARCHAR(255);
ALTER TABLE student_profiles ADD COLUMN profile_photo_url VARCHAR(255);
ALTER TABLE student_profiles ADD COLUMN college_id BIGINT;
ALTER TABLE student_profiles ADD COLUMN department_id BIGINT;

CREATE TABLE saved_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    saved_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_saved_job_student_job UNIQUE (student_id, job_id),
    CONSTRAINT fk_saved_job_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_saved_job_job FOREIGN KEY (job_id) REFERENCES jobs(id)
);

CREATE TABLE application_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    note TEXT,
    CONSTRAINT fk_status_application FOREIGN KEY (application_id) REFERENCES applications(id)
);

CREATE TABLE interview_schedules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    mode VARCHAR(50) NOT NULL,
    location_or_link VARCHAR(255),
    notes TEXT,
    feedback TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_interview_application FOREIGN KEY (application_id) REFERENCES applications(id)
);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50),
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
