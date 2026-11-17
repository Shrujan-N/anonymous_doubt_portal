-- MySQL DDL Setup Script for Anonymous Classroom Doubt Portal
-- Synchronized with Relational_Schema.drawio (All 14 normalized tables)

DROP DATABASE IF EXISTS doubt_portal;
CREATE DATABASE doubt_portal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE doubt_portal;

-- 1. STUDENT ENTITY
CREATE TABLE student (
    student_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    branch VARCHAR(50) NOT NULL,
    semester INT NOT NULL CHECK (semester BETWEEN 1 AND 8),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. TEACHER ENTITY
CREATE TABLE teacher (
    teacher_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    department VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

-- 3. ADMIN ENTITY
CREATE TABLE admin (
    admin_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- 4. SUBJECT ENTITY
CREATE TABLE subject (
    subject_id INT AUTO_INCREMENT PRIMARY KEY,
    subject_name VARCHAR(100) NOT NULL,
    subject_code VARCHAR(20) UNIQUE NOT NULL,
    teacher_id INT NULL,
    FOREIGN KEY (teacher_id) REFERENCES teacher(teacher_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 5. TAG ENTITY
CREATE TABLE tag (
    tag_id INT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(50) UNIQUE NOT NULL
) ENGINE=InnoDB;

-- 6. DOUBT ENTITY (includes optional admin soft-delete audit FK + FULLTEXT index)
CREATE TABLE doubt (
    doubt_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    subject_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    is_anonymous BOOLEAN DEFAULT FALSE,
    priority ENUM('Low', 'Medium', 'High') DEFAULT 'Medium',
    status ENUM('Pending', 'Answered by Student', 'Answered by Teacher', 'Closed') DEFAULT 'Pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by_admin_id INT NULL,
    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE,
    FOREIGN KEY (deleted_by_admin_id) REFERENCES admin(admin_id) ON DELETE SET NULL,
    FULLTEXT INDEX idx_doubt_fulltext (title, description)
) ENGINE=InnoDB;

-- 7. ANSWER ENTITY (peer student answer + optional admin soft-delete audit FK)
CREATE TABLE answer (
    answer_id INT AUTO_INCREMENT PRIMARY KEY,
    doubt_id INT NOT NULL,
    student_id INT NOT NULL,
    answer_text TEXT NOT NULL,
    upvotes INT DEFAULT 0 CHECK (upvotes >= 0),
    is_helpful BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by_admin_id INT NULL,
    FOREIGN KEY (doubt_id) REFERENCES doubt(doubt_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
    FOREIGN KEY (deleted_by_admin_id) REFERENCES admin(admin_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 8. TEACHER_ANSWER ENTITY
CREATE TABLE teacher_answer (
    teacher_answer_id INT AUTO_INCREMENT PRIMARY KEY,
    doubt_id INT NOT NULL,
    teacher_id INT NOT NULL,
    answer_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (doubt_id) REFERENCES doubt(doubt_id) ON DELETE CASCADE,
    FOREIGN KEY (teacher_id) REFERENCES teacher(teacher_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 9. UPVOTE JUNCTION ENTITY (student upvotes peer answer, composite PK prevents duplicate votes)
CREATE TABLE upvote (
    student_id INT NOT NULL,
    answer_id INT NOT NULL,
    voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, answer_id),
    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
    FOREIGN KEY (answer_id) REFERENCES answer(answer_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 10. TEACHER_ANSWER_UPVOTE JUNCTION ENTITY (student upvotes faculty answer)
CREATE TABLE teacher_answer_upvote (
    student_id INT NOT NULL,
    teacher_answer_id INT NOT NULL,
    voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, teacher_answer_id),
    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
    FOREIGN KEY (teacher_answer_id) REFERENCES teacher_answer(teacher_answer_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 11. SUBJECT_TAG M:N JUNCTION
CREATE TABLE subject_tag (
    subject_id INT NOT NULL,
    tag_id INT NOT NULL,
    PRIMARY KEY (subject_id, tag_id),
    FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 12. DOUBT_TAG M:N JUNCTION
CREATE TABLE doubt_tag (
    doubt_id INT NOT NULL,
    tag_id INT NOT NULL,
    PRIMARY KEY (doubt_id, tag_id),
    FOREIGN KEY (doubt_id) REFERENCES doubt(doubt_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 13. ANSWER_TAG M:N JUNCTION
CREATE TABLE answer_tag (
    answer_id INT NOT NULL,
    tag_id INT NOT NULL,
    PRIMARY KEY (answer_id, tag_id),
    FOREIGN KEY (answer_id) REFERENCES answer(answer_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 14. TEACHER_ANSWER_TAG M:N JUNCTION
CREATE TABLE teacher_answer_tag (
    teacher_answer_id INT NOT NULL,
    tag_id INT NOT NULL,
    PRIMARY KEY (teacher_answer_id, tag_id),
    FOREIGN KEY (teacher_answer_id) REFERENCES teacher_answer(teacher_answer_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- PERFORMANCE INDEXES
CREATE INDEX idx_doubt_subject ON doubt(subject_id);
CREATE INDEX idx_doubt_status ON doubt(status);
CREATE INDEX idx_doubt_student ON doubt(student_id);
CREATE INDEX idx_doubt_deleted_by ON doubt(deleted_by_admin_id);
CREATE INDEX idx_answer_deleted_by ON answer(deleted_by_admin_id);
