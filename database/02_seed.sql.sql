USE doubt_portal;

-- Seed Users
INSERT INTO student (name, email, password, branch, semester) VALUES
('Rahul Sharma', 'rahul@pes.edu', 'hashed_pwd_1', 'CSE', 5),
('Ananya Rao', 'ananya@pes.edu', 'hashed_pwd_2', 'ECE', 3),
('Vikram Verma', 'vikram@pes.edu', 'hashed_pwd_3', 'CSE', 5);

INSERT INTO teacher (name, email, password, department) VALUES
('Dr. Sudhir Kumar', 'sudhir@pes.edu', 'prof_pwd_1', 'CSE'),
('Prof. Meena P', 'meena@pes.edu', 'prof_pwd_2', 'ECE');

INSERT INTO admin (name, email, password) VALUES
('System Admin', 'admin@pes.edu', 'admin_root_pwd');

-- Seed Subjects
INSERT INTO subject (subject_name, subject_code, teacher_id) VALUES
('Database Management Systems', 'UE21CS301', 1),
('Digital Electronics', 'UE21EC201', 2);

-- Seed Doubts
INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority) VALUES
(1, 1, 'Difference between B-Tree and B+ Tree indexing?', 'Why are B+ trees preferred for disk storage in databases?', FALSE, 'High'),
(2, 2, 'Race condition in JK Flip Flop', 'How to prevent race around condition in master-slave setup?', TRUE, 'Medium');

-- Seed Answers
INSERT INTO answer (doubt_id, student_id, answer_text, upvotes, is_helpful) VALUES
(1, 3, 'B+ Trees store data pointers only in leaf nodes, leading to faster search and better cache hit rates.', 4, TRUE);

INSERT INTO teacher_answer (doubt_id, teacher_id, answer_text) VALUES
(1, 1, 'Correct. Additionally, leaf nodes in B+ Trees are linked sequentially, making range queries very efficient.');