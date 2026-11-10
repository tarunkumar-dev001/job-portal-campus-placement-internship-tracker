CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    min_cgpa DOUBLE NOT NULL,
    max_backlogs INT NOT NULL,
    deadline DATE NOT NULL,
    recruiter_id BIGINT NOT NULL,
    location VARCHAR(255),
    job_type VARCHAR(50),
    required_skills TEXT,
    salary_min DOUBLE,
    salary_max DOUBLE,
    internship BOOLEAN,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS student_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    full_name VARCHAR(255),
    roll_number VARCHAR(255),
    department VARCHAR(255),
    cgpa DOUBLE,
    backlogs INT,
    resume_url VARCHAR(255),
    CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    recruiter_feedback TEXT,
    withdrawn_at TIMESTAMP NULL,
    CONSTRAINT uq_application_student_job UNIQUE (student_id, job_id),
    CONSTRAINT fk_application_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_application_job FOREIGN KEY (job_id) REFERENCES jobs(id)
);

CREATE TABLE colleges (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255),
    state VARCHAR(255),
    university VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    college_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    seats INT,
    CONSTRAINT uq_department_college_code UNIQUE (college_id, code),
    CONSTRAINT fk_department_college FOREIGN KEY (college_id) REFERENCES colleges(id)
);

CREATE TABLE placement_officer_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    college_id BIGINT,
    full_name VARCHAR(255),
    phone VARCHAR(50),
    CONSTRAINT fk_officer_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_officer_college FOREIGN KEY (college_id) REFERENCES colleges(id)
);

CREATE TABLE placement_drives (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    college_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255),
    job_title VARCHAR(255),
    drive_type VARCHAR(100),
    description TEXT,
    min_cgpa DOUBLE,
    max_backlogs INT,
    registration_deadline DATE NOT NULL,
    drive_date DATE,
    status VARCHAR(30) NOT NULL,
    created_by_officer_id BIGINT NOT NULL,
    CONSTRAINT fk_drive_college FOREIGN KEY (college_id) REFERENCES colleges(id)
);

CREATE TABLE drive_registrations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    registered_at TIMESTAMP NOT NULL,
    offered_package DOUBLE,
    feedback TEXT,
    CONSTRAINT uq_drive_student UNIQUE (drive_id, student_id),
    CONSTRAINT fk_registration_drive FOREIGN KEY (drive_id) REFERENCES placement_drives(id),
    CONSTRAINT fk_registration_student FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE placement_selections (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    drive_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    job_title VARCHAR(255),
    package_amount DOUBLE,
    selected_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_selection_drive_student UNIQUE (drive_id, student_id),
    CONSTRAINT fk_selection_drive FOREIGN KEY (drive_id) REFERENCES placement_drives(id),
    CONSTRAINT fk_selection_student FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    industry VARCHAR(255),
    website VARCHAR(255),
    location VARCHAR(255),
    description TEXT,
    logo_url VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    owner_user_id BIGINT NOT NULL,
    CONSTRAINT fk_company_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    target_type VARCHAR(255),
    target_id VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP NOT NULL
);
