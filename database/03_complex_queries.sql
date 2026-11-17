USE doubt_portal;

-- ============================================================================
-- SECTION 1: IDEMPOTENT TRIGGERS
-- ============================================================================

DROP TRIGGER IF EXISTS trg_prevent_answer_on_closed_doubt;
DROP TRIGGER IF EXISTS trg_after_student_answer;
DROP TRIGGER IF EXISTS trg_prevent_teacher_answer_on_closed_doubt;
DROP TRIGGER IF EXISTS trg_after_teacher_answer;
DROP TRIGGER IF EXISTS trg_after_upvote_insert;
DROP TRIGGER IF EXISTS trg_after_upvote_delete;

DELIMITER //

-- Block peer answers on Closed or soft-deleted doubts
CREATE TRIGGER trg_prevent_answer_on_closed_doubt
BEFORE INSERT ON answer
FOR EACH ROW
BEGIN
    DECLARE v_status VARCHAR(30);
    DECLARE v_deleted_by INT;

    SELECT status, deleted_by_admin_id
    INTO v_status, v_deleted_by
    FROM doubt
    WHERE doubt_id = NEW.doubt_id;

    IF v_status = 'Closed' OR v_deleted_by IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot post a peer answer on a closed or moderated doubt.';
    END IF;
END //

-- Automatically transition doubt status when a peer answer is posted
CREATE TRIGGER trg_after_student_answer
AFTER INSERT ON answer
FOR EACH ROW
BEGIN
    IF NEW.deleted_by_admin_id IS NULL THEN
        UPDATE doubt
        SET status = 'Answered by Student'
        WHERE doubt_id = NEW.doubt_id
          AND status = 'Pending'
          AND deleted_by_admin_id IS NULL;
    END IF;
END //

-- Block faculty answers on Closed or soft-deleted doubts
CREATE TRIGGER trg_prevent_teacher_answer_on_closed_doubt
BEFORE INSERT ON teacher_answer
FOR EACH ROW
BEGIN
    DECLARE v_status VARCHAR(30);
    DECLARE v_deleted_by INT;

    SELECT status, deleted_by_admin_id
    INTO v_status, v_deleted_by
    FROM doubt
    WHERE doubt_id = NEW.doubt_id;

    IF v_status = 'Closed' OR v_deleted_by IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot post a faculty answer on a closed or moderated doubt.';
    END IF;
END //

-- Automatically transition doubt status when faculty posts an official answer
CREATE TRIGGER trg_after_teacher_answer
AFTER INSERT ON teacher_answer
FOR EACH ROW
BEGIN
    UPDATE doubt
    SET status = 'Answered by Teacher'
    WHERE doubt_id = NEW.doubt_id
      AND status <> 'Closed'
      AND deleted_by_admin_id IS NULL;
END //

-- Keep cached answer.upvotes synchronized when upvote rows are inserted/deleted
CREATE TRIGGER trg_after_upvote_insert
AFTER INSERT ON upvote
FOR EACH ROW
BEGIN
    UPDATE answer
    SET upvotes = upvotes + 1
    WHERE answer_id = NEW.answer_id;
END //

CREATE TRIGGER trg_after_upvote_delete
AFTER DELETE ON upvote
FOR EACH ROW
BEGIN
    UPDATE answer
    SET upvotes = GREATEST(upvotes - 1, 0)
    WHERE answer_id = OLD.answer_id;
END //

DELIMITER ;

-- ============================================================================
-- SECTION 2: IDEMPOTENT STORED PROCEDURES & STORED FUNCTION
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_SubmitDoubt;
DROP PROCEDURE IF EXISTS sp_CloseDoubt;
DROP FUNCTION IF EXISTS fn_CalculateStudentReputation;

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
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
        INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority, status)
        VALUES (p_student_id, p_subject_id, p_title, p_description, p_is_anonymous, p_priority, 'Pending');
        SELECT LAST_INSERT_ID() AS doubt_id;
    COMMIT;
END //

CREATE PROCEDURE sp_CloseDoubt(IN p_doubt_id INT)
BEGIN
    UPDATE doubt
    SET status = 'Closed'
    WHERE doubt_id = p_doubt_id;
END //

CREATE FUNCTION fn_CalculateStudentReputation(p_student_id INT)
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_answers_count INT DEFAULT 0;
    DECLARE v_helpful_count INT DEFAULT 0;
    DECLARE v_total_upvotes INT DEFAULT 0;

    SELECT COUNT(*),
           IFNULL(SUM(CASE WHEN is_helpful = TRUE THEN 1 ELSE 0 END), 0),
           IFNULL(SUM(upvotes), 0)
    INTO v_answers_count, v_helpful_count, v_total_upvotes
    FROM answer
    WHERE student_id = p_student_id
      AND deleted_by_admin_id IS NULL;

    RETURN (v_answers_count * 5) + (v_helpful_count * 20) + v_total_upvotes;
END //

DELIMITER ;

-- ============================================================================
-- SECTION 3: 6 COMPLEX SQL QUERIES (JOINs, Subquery, CTE, Window, Recursive CTE, FULLTEXT)
-- ============================================================================

-- QUERY 1: Multi-Table JOIN + Aggregation (Doubt feed summary across 8 tables)
SELECT d.doubt_id,
       d.title,
       IF(d.is_anonymous, 'Anonymous', st.name) AS visible_author,
       s.subject_code,
       s.subject_name,
       t.name AS faculty_owner,
       d.priority,
       d.status,
       GROUP_CONCAT(DISTINCT tg.tag_name ORDER BY tg.tag_name SEPARATOR ', ') AS tags,
       COUNT(DISTINCT a.answer_id) AS peer_answer_count,
       COUNT(DISTINCT ta.teacher_answer_id) AS faculty_answer_count
FROM doubt d
INNER JOIN student st ON d.student_id = st.student_id
INNER JOIN subject s ON d.subject_id = s.subject_id
LEFT JOIN teacher t ON s.teacher_id = t.teacher_id
LEFT JOIN doubt_tag dt ON d.doubt_id = dt.doubt_id
LEFT JOIN tag tg ON dt.tag_id = tg.tag_id
LEFT JOIN answer a ON d.doubt_id = a.doubt_id AND a.deleted_by_admin_id IS NULL
LEFT JOIN teacher_answer ta ON d.doubt_id = ta.doubt_id
WHERE d.deleted_by_admin_id IS NULL
GROUP BY d.doubt_id, d.title, d.is_anonymous, st.name, s.subject_code, s.subject_name, t.name, d.priority, d.status
ORDER BY d.doubt_id;

-- QUERY 2: Correlated & Nested Subquery (Students posting above-average active doubts)
SELECT st.student_id,
       st.name,
       st.branch,
       (SELECT COUNT(*)
        FROM doubt d2
        WHERE d2.student_id = st.student_id
          AND d2.deleted_by_admin_id IS NULL) AS doubts_asked
FROM student st
WHERE (
    SELECT COUNT(*)
    FROM doubt d1
    WHERE d1.student_id = st.student_id
      AND d1.deleted_by_admin_id IS NULL
) >= (
    SELECT AVG(cnt)
    FROM (
        SELECT COUNT(*) AS cnt
        FROM doubt
        WHERE deleted_by_admin_id IS NULL
        GROUP BY student_id
    ) AS per_student_counts
)
ORDER BY doubts_asked DESC, st.student_id ASC;

-- QUERY 3: Common Table Expression (CTE) + Stored Function Leaderboard
WITH StudentStats AS (
    SELECT s.student_id,
           s.name,
           s.branch,
           COUNT(a.answer_id) AS total_answers,
           IFNULL(SUM(a.upvotes), 0) AS total_upvotes,
           IFNULL(SUM(CASE WHEN a.is_helpful = TRUE THEN 1 ELSE 0 END), 0) AS helpful_answers
    FROM student s
    LEFT JOIN answer a ON s.student_id = a.student_id AND a.deleted_by_admin_id IS NULL
    GROUP BY s.student_id, s.name, s.branch
)
SELECT student_id,
       name,
       branch,
       total_answers,
       total_upvotes,
       helpful_answers,
       fn_CalculateStudentReputation(student_id) AS reputation_score
FROM StudentStats
ORDER BY reputation_score DESC, total_upvotes DESC;

-- QUERY 4: Window Functions (DENSE_RANK per subject & overall RANK by reputation)
SELECT sub.subject_code,
       d.title AS doubt_title,
       st.name AS answer_author,
       a.upvotes,
       DENSE_RANK() OVER (PARTITION BY sub.subject_code ORDER BY a.upvotes DESC) AS subject_answer_rank,
       RANK() OVER (ORDER BY fn_CalculateStudentReputation(st.student_id) DESC) AS global_contributor_rank
FROM answer a
INNER JOIN doubt d ON a.doubt_id = d.doubt_id
INNER JOIN subject sub ON d.subject_id = sub.subject_id
INNER JOIN student st ON a.student_id = st.student_id
WHERE a.deleted_by_admin_id IS NULL
  AND d.deleted_by_admin_id IS NULL
ORDER BY sub.subject_code, subject_answer_rank;

-- QUERY 5: Table-Based Recursive CTE (Multi-hop related doubts via shared tags in doubt_tag)
WITH RECURSIVE RelatedDoubtGraph AS (
    -- Anchor member: Start from Doubt #1
    SELECT d.doubt_id,
           d.title,
           0 AS hop_distance,
           CAST(d.doubt_id AS CHAR(200)) AS visited_path
    FROM doubt d
    WHERE d.doubt_id = 1
      AND d.deleted_by_admin_id IS NULL

    UNION ALL

    -- Recursive member: Discover connected doubts sharing at least one tag
    SELECT d2.doubt_id,
           d2.title,
           rdg.hop_distance + 1,
           CAST(CONCAT(rdg.visited_path, ',', d2.doubt_id) AS CHAR(200))
    FROM RelatedDoubtGraph rdg
    INNER JOIN doubt_tag dt1 ON rdg.doubt_id = dt1.doubt_id
    INNER JOIN doubt_tag dt2 ON dt1.tag_id = dt2.tag_id AND dt2.doubt_id <> rdg.doubt_id
    INNER JOIN doubt d2 ON dt2.doubt_id = d2.doubt_id
    WHERE rdg.hop_distance < 3
      AND d2.deleted_by_admin_id IS NULL
      AND FIND_IN_SET(d2.doubt_id, rdg.visited_path) = 0
)
SELECT doubt_id,
       title,
       MIN(hop_distance) AS min_tag_hops,
       ANY_VALUE(visited_path) AS sample_path
FROM RelatedDoubtGraph
GROUP BY doubt_id, title
ORDER BY min_tag_hops ASC, doubt_id ASC;

-- QUERY 6: Full-Text Scan using FULLTEXT INDEX idx_doubt_fulltext (title, description)
SELECT d.doubt_id,
       d.title,
       d.status,
       ROUND(MATCH(d.title, d.description) AGAINST('B-Tree indexing disk storage' IN NATURAL LANGUAGE MODE), 4) AS relevance_score
FROM doubt d
WHERE d.deleted_by_admin_id IS NULL
  AND MATCH(d.title, d.description) AGAINST('B-Tree indexing disk storage' IN NATURAL LANGUAGE MODE)
ORDER BY relevance_score DESC;
