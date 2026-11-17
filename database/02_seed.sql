USE doubt_portal;

-- 1. Seed Students
INSERT INTO student (name, email, password, branch, semester) VALUES
('Rahul Sharma', 'rahul@pes.edu', 'hashed_pwd_1', 'CSE', 5),
('Ananya Rao', 'ananya@pes.edu', 'hashed_pwd_2', 'ECE', 3),
('Vikram Verma', 'vikram@pes.edu', 'hashed_pwd_3', 'CSE', 5),
('Sneha Kulkarni', 'sneha@pes.edu', 'hashed_pwd_4', 'AIML', 5),
('Arjun Nair', 'arjun@pes.edu', 'hashed_pwd_5', 'CSE', 3);

-- 2. Seed Teachers
INSERT INTO teacher (name, email, password, department) VALUES
('Dr. Sudhir Kumar', 'sudhir@pes.edu', 'prof_pwd_1', 'CSE'),
('Prof. Meena P', 'meena@pes.edu', 'prof_pwd_2', 'ECE'),
('Dr. Kavitha R', 'kavitha@pes.edu', 'prof_pwd_3', 'CSE');

-- 3. Seed Admins
INSERT INTO admin (name, email, password) VALUES
('System Admin', 'admin@pes.edu', 'admin_root_pwd'),
('Dept Moderator', 'mod@pes.edu', 'mod_pwd_1');

-- 4. Seed Subjects
INSERT INTO subject (subject_name, subject_code, teacher_id) VALUES
('Database Management Systems', 'UE21CS301', 1),
('Digital Electronics', 'UE21EC201', 2),
('Operating Systems', 'UE21CS302', 3),
('Computer Networks', 'UE21CS303', 3);

-- 5. Seed Tags
INSERT INTO tag (tag_name) VALUES
('SQL'),
('Indexing'),
('B-Tree'),
('Normalization'),
('Transactions'),
('FlipFlop'),
('Deadlock'),
('TCP-IP'),
('Concurrency'),
('ExamPrep');

-- 6. Seed Doubts (including 1 soft-deleted moderation sample #6)
INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority, status, deleted_by_admin_id) VALUES
(1, 1, 'Difference between B-Tree and B+ Tree indexing?', 'Why are B+ trees preferred for disk storage and range scans in relational databases?', FALSE, 'High', 'Answered by Teacher', NULL),
(2, 2, 'Race condition in JK Flip Flop', 'How does a master-slave JK flip-flop setup prevent the race-around condition?', TRUE, 'Medium', 'Answered by Student', NULL),
(3, 3, 'Bankers algorithm vs deadlock detection', 'When should an operating system prefer deadlock avoidance over periodic cycle detection?', TRUE, 'High', 'Pending', NULL),
(4, 1, 'Why is BCNF stricter than 3NF?', 'Can every 3NF relation be decomposed into BCNF without losing functional dependencies?', FALSE, 'Medium', 'Answered by Teacher', NULL),
(1, 4, 'TCP slow start vs congestion avoidance', 'How does congestion window cwnd grow before and after hitting ssthresh?', FALSE, 'Low', 'Closed', NULL),
(5, 1, 'Practice off-topic spam post', 'Archived off-topic sample post held in moderation history.', TRUE, 'Low', 'Closed', 1);

-- 7. Seed Peer Answers (upvotes match rows inserted into upvote table below)
INSERT INTO answer (doubt_id, student_id, answer_text, upvotes, is_helpful, deleted_by_admin_id) VALUES
(1, 3, 'B+ Trees store data pointers only in leaf nodes, allowing higher fanout in internal nodes and faster sequential range scans.', 3, TRUE, NULL),
(2, 1, 'Master-slave cascading decouples the clock edge: the master samples on the rising edge and the slave updates on the falling edge.', 2, TRUE, NULL),
(4, 5, 'BCNF requires every non-trivial determinant to be a superkey, whereas 3NF allows prime attributes on the right-hand side.', 1, FALSE, NULL),
(5, 3, 'Slow start doubles cwnd exponentially per RTT until ssthresh, then congestion avoidance switches to linear AIMD growth.', 2, TRUE, NULL),
(6, 4, 'Off-topic peer reply removed by moderator.', 0, FALSE, 2);

-- 8. Seed Faculty Answers
INSERT INTO teacher_answer (doubt_id, teacher_id, answer_text) VALUES
(1, 1, 'Correct. Additionally, linked leaf nodes in a B+ Tree eliminate backtracking during BETWEEN range queries in MySQL InnoDB.'),
(4, 1, 'Every binary relation is in BCNF, and 3NF always preserves dependencies whereas BCNF guarantees zero FD-based redundancy at the cost of occasional dependency loss.'),
(5, 3, 'Exact summary. Remember that triple duplicate ACKs trigger fast retransmit and halve cwnd without resetting to 1 MSS.');

-- 9. Seed Peer Answer Upvotes (matches answer.upvotes above)
INSERT INTO upvote (student_id, answer_id) VALUES
(1, 1), (2, 1), (4, 1),
(3, 2), (5, 2),
(1, 3),
(1, 4), (2, 4);

-- 10. Seed Teacher Answer Upvotes (14-table relational integrity)
INSERT INTO teacher_answer_upvote (student_id, teacher_answer_id) VALUES
(1, 1), (2, 1), (3, 1),
(4, 2),
(1, 3);

-- 11. Seed Subject Tags
INSERT INTO subject_tag (subject_id, tag_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 10),
(2, 6), (2, 10),
(3, 7), (3, 9),
(4, 8), (4, 10);

-- 12. Seed Doubt Tags (connected graph for Recursive CTE traversal)
INSERT INTO doubt_tag (doubt_id, tag_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 10),
(2, 6), (2, 10),
(3, 7), (3, 9),
(4, 1), (4, 4), (4, 5),
(5, 8), (5, 9), (5, 10);

-- 13. Seed Answer Tags
INSERT INTO answer_tag (answer_id, tag_id) VALUES
(1, 2), (1, 3),
(2, 6),
(3, 4),
(4, 8);

-- 14. Seed Teacher Answer Tags
INSERT INTO teacher_answer_tag (teacher_answer_id, tag_id) VALUES
(1, 2), (1, 3),
(2, 4),
(3, 8);

-- ============================================================================
-- DML OPERATIONS DEMONSTRATION (INSERT, SELECT, UPDATE, DELETE)
-- Safe self-contained transaction block for Viva / Rubric #6 verification
-- ============================================================================
-- SELECT: View active doubts with masked anonymity
SELECT doubt_id,
       IF(is_anonymous, 'Anonymous', CONCAT('Student #', student_id)) AS author,
       title,
       priority,
       status
FROM doubt
WHERE deleted_by_admin_id IS NULL;

-- INSERT + UPDATE + DELETE (self-contained cycle that cleans up its temporary row)
START TRANSACTION;
INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority)
VALUES (2, 1, 'DML Demo: Difference between ACID and BASE', 'Temporary demo row for DML walkthrough.', TRUE, 'Low');

SET @demo_doubt_id = LAST_INSERT_ID();

UPDATE doubt
SET priority = 'High', status = 'Closed'
WHERE doubt_id = @demo_doubt_id;

SELECT doubt_id, title, priority, status FROM doubt WHERE doubt_id = @demo_doubt_id;

DELETE FROM doubt WHERE doubt_id = @demo_doubt_id;
COMMIT;
