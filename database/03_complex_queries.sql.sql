USE doubt_portal;

-- TRIGGERS

DELIMITER //
CREATE TRIGGER trg_after_student_answer
AFTER INSERT ON answer
FOR EACH ROW
BEGIN
    UPDATE doubt
    SET status = 'Answered by Student'
    WHERE doubt_id = NEW.doubt_id AND status = 'Pending';
END //
DELIMITER ;

DELIMITER //
CREATE TRIGGER trg_after_teacher_answer
AFTER INSERT ON teacher_answer
FOR EACH ROW
BEGIN
    UPDATE doubt
    SET status = 'Answered by Teacher'
    WHERE doubt_id = NEW.doubt_id;
END //
DELIMITER ;


-- STORED PROCEDURES & FUNCTIONS

DELIMITER //
CREATE PROCEDURE sp_SubmitDoubt(
    IN p_student_id INT,
    IN p_subject_id INT,
    IN p_title VARCHAR(200),
    IN p_description TEXT,
    IN p_is_anonymous BOOLEAN,
    IN p_priority VARCHAR(10)
)
BEGIN
    START TRANSACTION;
    INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority)
    VALUES (p_student_id, p_subject_id, p_title, p_description, p_is_anonymous, p_priority);
    COMMIT;
END //
DELIMITER ;

DELIMITER //
CREATE PROCEDURE sp_CloseDoubt(IN p_doubt_id INT)
BEGIN
    UPDATE doubt SET status = 'Closed' WHERE doubt_id = p_doubt_id;
END //
DELIMITER ;

DELIMITER //
CREATE FUNCTION fn_CalculateStudentReputation(p_student_id INT)
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_reputation INT DEFAULT 0;
    DECLARE v_answers_count INT DEFAULT 0;
    DECLARE v_helpful_count INT DEFAULT 0;
    
    SELECT COUNT(*) INTO v_answers_count FROM answer WHERE student_id = p_student_id;
    SELECT COUNT(*) INTO v_helpful_count FROM answer WHERE student_id = p_student_id AND is_helpful = TRUE;
    
    SET v_reputation = (v_answers_count * 5) + (v_helpful_count * 20);
    RETURN v_reputation;
END //
DELIMITER ;


-- ADVANCED QUERIES (Joins, CTEs, Recursive CTE, Correlated)

-- Multi-Table JOIN (Doubts with Subject and Teacher Info)
SELECT d.doubt_id, d.title, s.subject_name, t.name AS teacher_name, d.status
FROM doubt d
INNER JOIN subject s ON d.subject_id = s.subject_id
LEFT JOIN teacher t ON s.teacher_id = t.teacher_id;

-- Correlated Subquery (Students who posted more than the average number of doubts)
SELECT st.student_id, st.name
FROM student st
WHERE (SELECT COUNT(*) FROM doubt d WHERE d.student_id = st.student_id) > (
    SELECT COUNT(*) / COUNT(DISTINCT student_id) FROM doubt
);

-- Common Table Expression (CTE) - Leaderboard of top peers
WITH StudentStats AS (
    SELECT 
        s.student_id, 
        s.name,
        COUNT(a.answer_id) AS total_answers,
        SUM(a.upvotes) AS total_upvotes
    FROM student s
    LEFT JOIN answer a ON s.student_id = a.student_id
    GROUP BY s.student_id, s.name
)
SELECT name, total_answers, IFNULL(total_upvotes, 0) AS score
FROM StudentStats
ORDER BY score DESC;

-- Recursive CTE (Simulating Topic/Semester Hierarchy escalation)
WITH RECURSIVE SemesterEscalation AS (
    SELECT 1 AS sem_level, 'Level 1: Peer Resolution' AS stage
    UNION ALL
    SELECT sem_level + 1, CONCAT('Level ', sem_level + 1, ': Faculty Review Stage')
    FROM SemesterEscalation
    WHERE sem_level < 4
)
SELECT * FROM SemesterEscalation;