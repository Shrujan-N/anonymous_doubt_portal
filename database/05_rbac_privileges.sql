-- MySQL 8.0 Database-Level RBAC Roles, Users, GRANT & REVOKE Script
-- Run in PowerShell: Get-Content database/05_rbac_privileges.sql -Raw | mysql -u root -p

USE doubt_portal;

-- Clean existing roles/users for idempotent re-execution
DROP USER IF EXISTS 'portal_student'@'localhost';
DROP USER IF EXISTS 'portal_teacher'@'localhost';
DROP USER IF EXISTS 'portal_admin'@'localhost';

DROP ROLE IF EXISTS 'role_portal_student';
DROP ROLE IF EXISTS 'role_portal_teacher';
DROP ROLE IF EXISTS 'role_portal_admin';

-- 1. Create MySQL Roles
CREATE ROLE 'role_portal_student';
CREATE ROLE 'role_portal_teacher';
CREATE ROLE 'role_portal_admin';

-- 2. Grant Granular Least-Privilege Permissions to Each Role

-- STUDENT ROLE: Can read portal data, ask doubts, post peer answers, and cast upvotes
GRANT SELECT ON doubt_portal.subject TO 'role_portal_student';
GRANT SELECT ON doubt_portal.tag TO 'role_portal_student';
GRANT SELECT ON doubt_portal.doubt TO 'role_portal_student';
GRANT SELECT ON doubt_portal.answer TO 'role_portal_student';
GRANT SELECT ON doubt_portal.teacher_answer TO 'role_portal_student';
GRANT SELECT (student_id, name, branch, semester, created_at) ON doubt_portal.student TO 'role_portal_student';
GRANT SELECT (teacher_id, name, department) ON doubt_portal.teacher TO 'role_portal_student';

GRANT INSERT ON doubt_portal.doubt TO 'role_portal_student';
GRANT INSERT ON doubt_portal.answer TO 'role_portal_student';
GRANT INSERT, DELETE ON doubt_portal.upvote TO 'role_portal_student';
GRANT INSERT, DELETE ON doubt_portal.teacher_answer_upvote TO 'role_portal_student';
GRANT INSERT ON doubt_portal.doubt_tag TO 'role_portal_student';
GRANT INSERT ON doubt_portal.answer_tag TO 'role_portal_student';

GRANT EXECUTE ON PROCEDURE doubt_portal.sp_SubmitDoubt TO 'role_portal_student';
GRANT EXECUTE ON FUNCTION doubt_portal.fn_CalculateStudentReputation TO 'role_portal_student';

-- TEACHER ROLE: Can read all academic records, post official faculty answers, mark helpful, and close doubts
GRANT SELECT ON doubt_portal.* TO 'role_portal_teacher';
GRANT INSERT ON doubt_portal.teacher_answer TO 'role_portal_teacher';
GRANT INSERT ON doubt_portal.teacher_answer_tag TO 'role_portal_teacher';
GRANT UPDATE (is_helpful) ON doubt_portal.answer TO 'role_portal_teacher';
GRANT UPDATE (status) ON doubt_portal.doubt TO 'role_portal_teacher';
GRANT EXECUTE ON PROCEDURE doubt_portal.sp_CloseDoubt TO 'role_portal_teacher';
GRANT EXECUTE ON FUNCTION doubt_portal.fn_CalculateStudentReputation TO 'role_portal_teacher';

-- Demonstrate REVOKE explicitly (Teacher cannot delete student rows even if temporarily granted)
GRANT DELETE ON doubt_portal.student TO 'role_portal_teacher';
REVOKE DELETE ON doubt_portal.student FROM 'role_portal_teacher';

-- ADMIN ROLE: Full DML moderation & procedure execution inside doubt_portal (without global GRANT OPTION)
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON doubt_portal.* TO 'role_portal_admin';

-- 3. Create Individual Database Users & Assign Default Roles
CREATE USER 'portal_student'@'localhost' IDENTIFIED BY 'student_123';
CREATE USER 'portal_teacher'@'localhost' IDENTIFIED BY 'teacher_123';
CREATE USER 'portal_admin'@'localhost'   IDENTIFIED BY 'admin_123';

GRANT 'role_portal_student' TO 'portal_student'@'localhost';
GRANT 'role_portal_teacher' TO 'portal_teacher'@'localhost';
GRANT 'role_portal_admin'   TO 'portal_admin'@'localhost';

SET DEFAULT ROLE ALL TO
    'portal_student'@'localhost',
    'portal_teacher'@'localhost',
    'portal_admin'@'localhost';

FLUSH PRIVILEGES;

-- 4. Verify Privileges (CLI Demonstration)
SHOW GRANTS FOR 'portal_student'@'localhost' USING 'role_portal_student';
SHOW GRANTS FOR 'portal_teacher'@'localhost' USING 'role_portal_teacher';
SHOW GRANTS FOR 'portal_admin'@'localhost'   USING 'role_portal_admin';
