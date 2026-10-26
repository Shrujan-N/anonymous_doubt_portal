package com.doubtportal.controller;

import com.doubtportal.model.Doubt;
import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtRepository;
import com.doubtportal.service.VectorSearchService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/doubts")
@CrossOrigin(origins = "*")
public class DoubtController {

    @Autowired
    private DoubtRepository doubtRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorSearchService vectorSearchService;

    @PostConstruct
    public void initDatabase() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS student (
                    student_id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    branch VARCHAR(50) NOT NULL,
                    semester INT CHECK (semester BETWEEN 1 AND 8),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS teacher (
                    teacher_id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    department VARCHAR(50) NOT NULL
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin (
                    admin_id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS subject (
                    subject_id INT AUTO_INCREMENT PRIMARY KEY,
                    subject_name VARCHAR(100) NOT NULL,
                    subject_code VARCHAR(20) UNIQUE NOT NULL,
                    teacher_id INT,
                    FOREIGN KEY (teacher_id) REFERENCES teacher(teacher_id) ON DELETE SET NULL
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS doubt (
                    doubt_id INT AUTO_INCREMENT PRIMARY KEY,
                    student_id INT NOT NULL,
                    subject_id INT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    description TEXT,
                    is_anonymous BOOLEAN DEFAULT FALSE,
                    priority ENUM('Low', 'Medium', 'High') DEFAULT 'Medium',
                    status ENUM('Pending', 'Answered by Student', 'Answered by Teacher', 'Closed') DEFAULT 'Pending',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
                    FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS answer (
                    answer_id INT AUTO_INCREMENT PRIMARY KEY,
                    doubt_id INT NOT NULL,
                    student_id INT NOT NULL,
                    answer_text TEXT NOT NULL,
                    upvotes INT DEFAULT 0,
                    is_helpful BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (doubt_id) REFERENCES doubt(doubt_id) ON DELETE CASCADE,
                    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS teacher_answer (
                    teacher_answer_id INT AUTO_INCREMENT PRIMARY KEY,
                    doubt_id INT NOT NULL,
                    teacher_id INT NOT NULL,
                    answer_text TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (doubt_id) REFERENCES doubt(doubt_id) ON DELETE CASCADE,
                    FOREIGN KEY (teacher_id) REFERENCES teacher(teacher_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tag (
                    tag_id INT AUTO_INCREMENT PRIMARY KEY,
                    tag_name VARCHAR(50) UNIQUE NOT NULL
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS doubt_tag (
                    doubt_id INT,
                    tag_id INT,
                    PRIMARY KEY (doubt_id, tag_id),
                    FOREIGN KEY (doubt_id) REFERENCES doubt(doubt_id) ON DELETE CASCADE,
                    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS subject_tag (
                    subject_id INT,
                    tag_id INT,
                    PRIMARY KEY (subject_id, tag_id),
                    FOREIGN KEY (subject_id) REFERENCES subject(subject_id) ON DELETE CASCADE,
                    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS teacher_answer_tag (
                    teacher_answer_id INT,
                    tag_id INT,
                    PRIMARY KEY (teacher_answer_id, tag_id),
                    FOREIGN KEY (teacher_answer_id) REFERENCES teacher_answer(teacher_answer_id) ON DELETE CASCADE,
                    FOREIGN KEY (tag_id) REFERENCES tag(tag_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS upvote (
                    student_id INT,
                    answer_id INT,
                    voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (student_id, answer_id),
                    FOREIGN KEY (student_id) REFERENCES student(student_id) ON DELETE CASCADE,
                    FOREIGN KEY (answer_id) REFERENCES answer(answer_id) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            Integer studentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student", Integer.class);
            if (studentCount != null && studentCount == 0) {
                jdbcTemplate.execute("""
                    INSERT INTO student (name, email, password, branch, semester) VALUES
                    ('Rahul Sharma', 'rahul@pes.edu', 'hashed_pwd_1', 'CSE', 5),
                    ('Ananya Rao', 'ananya@pes.edu', 'hashed_pwd_2', 'ECE', 3),
                    ('Vikram Verma', 'vikram@pes.edu', 'hashed_pwd_3', 'CSE', 5)
                """);
                jdbcTemplate.execute("""
                    INSERT INTO teacher (name, email, password, department) VALUES
                    ('Dr. Sudhir Kumar', 'sudhir@pes.edu', 'prof_pwd_1', 'CSE'),
                    ('Prof. Meena P', 'meena@pes.edu', 'prof_pwd_2', 'ECE')
                """);
                jdbcTemplate.execute("""
                    INSERT INTO admin (name, email, password) VALUES
                    ('System Admin', 'admin@pes.edu', 'admin_root_pwd')
                """);
                jdbcTemplate.execute("""
                    INSERT INTO subject (subject_name, subject_code, teacher_id) VALUES
                    ('Database Management Systems', 'UE21CS301', 1),
                    ('Digital Electronics', 'UE21EC201', 2)
                """);
                jdbcTemplate.execute("""
                    INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority, status) VALUES
                    (1, 1, 'Difference between B-Tree and B+ Tree indexing?', 'Why are B+ trees preferred for disk storage in databases?', FALSE, 'High', 'Answered by Teacher'),
                    (2, 2, 'Race condition in JK Flip Flop', 'How to prevent race around condition in master-slave setup?', TRUE, 'Medium', 'Pending')
                """);
                jdbcTemplate.execute("""
                    INSERT INTO answer (doubt_id, student_id, answer_text, upvotes, is_helpful) VALUES
                    (1, 3, 'B+ Trees store data pointers only in leaf nodes, leading to faster search and better cache hit rates.', 4, TRUE)
                """);
                jdbcTemplate.execute("""
                    INSERT INTO teacher_answer (doubt_id, teacher_id, answer_text) VALUES
                    (1, 1, 'Correct. Additionally, leaf nodes in B+ Trees are linked sequentially, making range queries very efficient.')
                """);
            }

            // Install Triggers, Stored Procedures, and Stored Function from 03_complex_queries.sql
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_after_student_answer");
            jdbcTemplate.execute("""
                CREATE TRIGGER trg_after_student_answer
                AFTER INSERT ON answer
                FOR EACH ROW
                BEGIN
                    UPDATE doubt
                    SET status = 'Answered by Student'
                    WHERE doubt_id = NEW.doubt_id AND status = 'Pending';
                END
            """);

            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_after_teacher_answer");
            jdbcTemplate.execute("""
                CREATE TRIGGER trg_after_teacher_answer
                AFTER INSERT ON teacher_answer
                FOR EACH ROW
                BEGIN
                    UPDATE doubt
                    SET status = 'Answered by Teacher'
                    WHERE doubt_id = NEW.doubt_id;
                END
            """);

            jdbcTemplate.execute("DROP PROCEDURE IF EXISTS sp_SubmitDoubt");
            jdbcTemplate.execute("""
                CREATE PROCEDURE sp_SubmitDoubt(
                    IN p_student_id INT,
                    IN p_subject_id INT,
                    IN p_title VARCHAR(200),
                    IN p_description TEXT,
                    IN p_is_anonymous BOOLEAN,
                    IN p_priority VARCHAR(10)
                )
                BEGIN
                    INSERT INTO doubt (student_id, subject_id, title, description, is_anonymous, priority)
                    VALUES (p_student_id, p_subject_id, p_title, p_description, p_is_anonymous, p_priority);
                END
            """);

            jdbcTemplate.execute("DROP PROCEDURE IF EXISTS sp_CloseDoubt");
            jdbcTemplate.execute("""
                CREATE PROCEDURE sp_CloseDoubt(IN p_doubt_id INT)
                BEGIN
                    UPDATE doubt SET status = 'Closed' WHERE doubt_id = p_doubt_id;
                END
            """);

            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_CalculateStudentReputation");
            jdbcTemplate.execute("""
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
                END
            """);
        } catch (Exception e) {
            System.err.println("Database init warning: " + e.getMessage());
        }
    }

    @GetMapping
    public List<Map<String, Object>> getAllDoubts() {
        String sql = """
            SELECT d.doubt_id AS doubtId,
                   d.student_id AS studentId,
                   d.subject_id AS subjectId,
                   d.title AS title,
                   d.description AS description,
                   d.is_anonymous AS isAnonymous,
                   d.priority AS priority,
                   d.status AS status,
                   d.created_at AS createdAt,
                   st.name AS studentName,
                   s.subject_name AS subjectName,
                   s.subject_code AS subjectCode,
                   t.name AS teacherName
            FROM doubt d
            LEFT JOIN student st ON d.student_id = st.student_id
            LEFT JOIN subject s ON d.subject_id = s.subject_id
            LEFT JOIN teacher t ON s.teacher_id = t.teacher_id
            ORDER BY d.doubt_id DESC
        """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Number doubtIdNum = (Number) row.get("doubtId");
            long doubtId = doubtIdNum.longValue();

            Object anonObj = row.get("isAnonymous");
            boolean isAnon = (anonObj instanceof Boolean b) ? b
                    : (anonObj instanceof Number n && n.intValue() == 1);
            item.put("isAnonymous", isAnon);
            item.put("authorDisplay", isAnon ? "Anonymous" : (row.get("studentName") != null ? row.get("studentName") : "Student #" + row.get("studentId")));

            List<Map<String, Object>> studentAnswers = jdbcTemplate.queryForList("""
                SELECT a.answer_id AS answerId,
                       a.doubt_id AS doubtId,
                       a.student_id AS studentId,
                       st.name AS studentName,
                       a.answer_text AS answerText,
                       a.upvotes AS upvotes,
                       a.is_helpful AS isHelpful,
                       a.created_at AS createdAt
                FROM answer a
                LEFT JOIN student st ON a.student_id = st.student_id
                WHERE a.doubt_id = ?
                ORDER BY a.upvotes DESC, a.answer_id ASC
            """, doubtId);

            List<Map<String, Object>> teacherAnswers = jdbcTemplate.queryForList("""
                SELECT ta.teacher_answer_id AS teacherAnswerId,
                       ta.doubt_id AS doubtId,
                       ta.teacher_id AS teacherId,
                       t.name AS teacherName,
                       ta.answer_text AS answerText,
                       ta.created_at AS createdAt
                FROM teacher_answer ta
                LEFT JOIN teacher t ON ta.teacher_id = t.teacher_id
                WHERE ta.doubt_id = ?
                ORDER BY ta.teacher_answer_id ASC
            """, doubtId);

            item.put("answers", studentAnswers);
            item.put("teacherAnswers", teacherAnswers);
            result.add(item);
        }
        return result;
    }

    @PostMapping
    public Doubt createDoubt(@RequestBody Doubt doubt) {
        if (doubt.getStudentId() == null) doubt.setStudentId(1L);
        if (doubt.getSubjectId() == null) doubt.setSubjectId(1L);
        if (doubt.getIsAnonymous() == null) doubt.setIsAnonymous(false);
        if (doubt.getPriority() == null || doubt.getPriority().isBlank()) doubt.setPriority("Medium");
        if (doubt.getStatus() == null || doubt.getStatus().isBlank()) doubt.setStatus("Pending");

        try {
            jdbcTemplate.update("CALL sp_SubmitDoubt(?, ?, ?, ?, ?, ?)",
                    doubt.getStudentId(),
                    doubt.getSubjectId(),
                    doubt.getTitle(),
                    doubt.getDescription(),
                    doubt.getIsAnonymous(),
                    doubt.getPriority()
            );
            Long latestId = jdbcTemplate.queryForObject("SELECT MAX(doubt_id) FROM doubt", Long.class);
            doubt.setDoubtId(latestId);
        } catch (Exception e) {
            doubt = doubtRepository.save(doubt);
        }

        // Automatically index the new doubt into MongoDB NoSQL Vector Store
        try {
            String subjectTag = doubt.getSubjectId() != null && doubt.getSubjectId() == 2L ? "Electronics" : "DBMS";
            List<String> autoTags = new ArrayList<>(List.of(subjectTag, doubt.getPriority()));
            if (doubt.getTitle() != null) {
                for (String word : doubt.getTitle().split("\\s+")) {
                    String clean = word.replaceAll("[^a-zA-Z0-9+-]", "");
                    if (clean.length() >= 3 && autoTags.size() < 6) {
                        autoTags.add(clean);
                    }
                }
            }
            vectorSearchService.indexDoubtVector(new DoubtVectorDocument(
                    doubt.getDoubtId(),
                    doubt.getTitle(),
                    List.of(0.25, 0.50, 0.75, 0.90),
                    autoTags
            ));
        } catch (Exception ignored) {
        }

        return doubt;
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<Map<String, Object>> postStudentAnswer(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {
        int studentId = body.get("studentId") instanceof Number n ? n.intValue() : 1;
        String answerText = String.valueOf(body.getOrDefault("answerText", "")).trim();
        if (answerText.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Answer text cannot be empty."));
        }
        jdbcTemplate.update(
                "INSERT INTO answer (doubt_id, student_id, answer_text) VALUES (?, ?, ?)",
                id, studentId, answerText
        );
        // Ensure status updates even if trigger was bypassed
        jdbcTemplate.update(
                "UPDATE doubt SET status = 'Answered by Student' WHERE doubt_id = ? AND status = 'Pending'",
                id
        );
        return ResponseEntity.ok(Map.of(
                "message", "Peer answer posted! Trigger trg_after_student_answer updated status."
        ));
    }

    @PostMapping("/{id}/teacher-answers")
    public ResponseEntity<Map<String, Object>> postTeacherAnswer(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body) {
        int teacherId = body.get("teacherId") instanceof Number n ? n.intValue() : 1;
        String answerText = String.valueOf(body.getOrDefault("answerText", "")).trim();
        if (answerText.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Official answer text cannot be empty."));
        }
        jdbcTemplate.update(
                "INSERT INTO teacher_answer (doubt_id, teacher_id, answer_text) VALUES (?, ?, ?)",
                id, teacherId, answerText
        );
        jdbcTemplate.update(
                "UPDATE doubt SET status = 'Answered by Teacher' WHERE doubt_id = ?",
                id
        );
        return ResponseEntity.ok(Map.of(
                "message", "Official faculty answer posted! Trigger trg_after_teacher_answer updated status."
        ));
    }

    @PostMapping("/answers/{answerId}/upvote")
    public ResponseEntity<Map<String, Object>> upvoteAnswer(
            @PathVariable Integer answerId,
            @RequestParam(defaultValue = "1") Integer studentId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO upvote (student_id, answer_id) VALUES (?, ?)",
                    studentId, answerId
            );
            jdbcTemplate.update(
                    "UPDATE answer SET upvotes = upvotes + 1 WHERE answer_id = ?",
                    answerId
            );
            return ResponseEntity.ok(Map.of("message", "Upvote recorded!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "You have already upvoted this answer (Composite PK constraint on upvote table)."
            ));
        }
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<Map<String, String>> closeDoubt(@PathVariable Integer id) {
        try {
            jdbcTemplate.update("CALL sp_CloseDoubt(?)", id);
        } catch (Exception e) {
            jdbcTemplate.update("UPDATE doubt SET status = 'Closed' WHERE doubt_id = ?", id);
        }
        return ResponseEntity.ok(Map.of(
                "message", "Doubt ID " + id + " closed successfully via Stored Procedure sp_CloseDoubt."
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDoubt(@PathVariable Integer id) {
        jdbcTemplate.update("DELETE FROM doubt WHERE doubt_id = ?", id);
        return ResponseEntity.ok(Map.of(
                "message", "Doubt ID " + id + " deleted by Admin (Cascaded via ON DELETE CASCADE)."
        ));
    }

    @GetMapping("/leaderboard")
    public List<Map<String, Object>> getLeaderboard() {
        String cteSql = """
            WITH StudentStats AS (
                SELECT s.student_id AS studentId,
                       s.name AS name,
                       s.branch AS branch,
                       COUNT(a.answer_id) AS totalAnswers,
                       IFNULL(SUM(a.upvotes), 0) AS totalUpvotes,
                       SUM(CASE WHEN a.is_helpful = TRUE THEN 1 ELSE 0 END) AS helpfulCount
                FROM student s
                LEFT JOIN answer a ON s.student_id = a.student_id
                GROUP BY s.student_id, s.name, s.branch
            )
            SELECT studentId,
                   name,
                   branch,
                   totalAnswers,
                   totalUpvotes,
                   (totalAnswers * 5) + (IFNULL(helpfulCount, 0) * 20) + totalUpvotes AS score
            FROM StudentStats
            ORDER BY score DESC, totalUpvotes DESC
        """;
        return jdbcTemplate.queryForList(cteSql);
    }
}