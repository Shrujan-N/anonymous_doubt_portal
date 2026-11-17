package com.doubtportal.controller;

import com.doubtportal.dto.AnswerRequest;
import com.doubtportal.model.Answer;
import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.AnswerRepository;
import com.doubtportal.service.AuthService;
import com.doubtportal.service.VectorSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/doubts")
public class DoubtController {

    private static final Set<String> VALID_PRIORITIES = Set.of("Low", "Medium", "High");

    private final JdbcTemplate jdbcTemplate;
    private final VectorSearchService vectorSearchService;
    private final AuthService authService;
    private final AnswerRepository answerRepository;

    public DoubtController(JdbcTemplate jdbcTemplate,
                           VectorSearchService vectorSearchService,
                           AuthService authService,
                           AnswerRepository answerRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorSearchService = vectorSearchService;
        this.authService = authService;
        this.answerRepository = answerRepository;
    }

    @GetMapping
    public List<Map<String, Object>> getAllDoubts(
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            HttpServletRequest request) {

        Optional<AuthService.AuthenticatedUser> currentUser = authService.getAuthenticatedUser(request);
        boolean isAdmin = currentUser.map(u -> "ADMIN".equalsIgnoreCase(u.role())).orElse(false);
        Long currentStudentId = currentUser
                .filter(u -> "STUDENT".equalsIgnoreCase(u.role()))
                .map(AuthService.AuthenticatedUser::userId)
                .orElse(null);

        StringBuilder sql = new StringBuilder("""
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
        """);

        List<Object> params = new ArrayList<>();
        boolean hasFullTextQuery = q != null && !q.trim().isEmpty();
        if (hasFullTextQuery) {
            sql.append(", ROUND(MATCH(d.title, d.description) AGAINST(? IN NATURAL LANGUAGE MODE), 4) AS relevanceScore ");
            params.add(q.trim());
        }

        sql.append("""
            FROM doubt d
            LEFT JOIN student st ON d.student_id = st.student_id
            LEFT JOIN subject s ON d.subject_id = s.subject_id
            LEFT JOIN teacher t ON s.teacher_id = t.teacher_id
            WHERE d.deleted_by_admin_id IS NULL
        """);

        if (subjectId != null) {
            sql.append(" AND d.subject_id = ? ");
            params.add(subjectId);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND d.status = ? ");
            params.add(status.trim());
        }
        if (hasFullTextQuery) {
            sql.append(" AND MATCH(d.title, d.description) AGAINST(? IN NATURAL LANGUAGE MODE) ");
            params.add(q.trim());
            sql.append(" ORDER BY relevanceScore DESC, d.doubt_id DESC ");
        } else {
            sql.append(" ORDER BY d.doubt_id DESC ");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            long doubtId = ((Number) row.get("doubtId")).longValue();
            Long rowStudentId = row.get("studentId") instanceof Number n ? n.longValue() : null;

            Object anonObj = row.get("isAnonymous");
            boolean isAnon = (anonObj instanceof Boolean b) ? b
                    : (anonObj instanceof Number n && n.intValue() == 1);
            item.put("isAnonymous", isAnon);

            boolean revealIdentity = !isAnon || isAdmin || (currentStudentId != null && currentStudentId.equals(rowStudentId));
            item.put("authorDisplay", isAnon
                    ? "Anonymous"
                    : (row.get("studentName") != null ? row.get("studentName") : "Student #" + rowStudentId));
            if (!revealIdentity) {
                item.put("studentId", null);
                item.remove("studentName");
            }

            List<String> tags = jdbcTemplate.queryForList("""
                SELECT tg.tag_name
                FROM doubt_tag dt
                INNER JOIN tag tg ON dt.tag_id = tg.tag_id
                WHERE dt.doubt_id = ?
                ORDER BY tg.tag_name ASC
            """, String.class, doubtId);

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
                  AND a.deleted_by_admin_id IS NULL
                ORDER BY a.is_helpful DESC, a.upvotes DESC, a.answer_id ASC
            """, doubtId);

            List<Map<String, Object>> teacherAnswers = jdbcTemplate.queryForList("""
                SELECT ta.teacher_answer_id AS teacherAnswerId,
                       ta.doubt_id AS doubtId,
                       ta.teacher_id AS teacherId,
                       t.name AS teacherName,
                       ta.answer_text AS answerText,
                       ta.created_at AS createdAt,
                       COUNT(tau.student_id) AS upvotes
                FROM teacher_answer ta
                LEFT JOIN teacher t ON ta.teacher_id = t.teacher_id
                LEFT JOIN teacher_answer_upvote tau ON ta.teacher_answer_id = tau.teacher_answer_id
                WHERE ta.doubt_id = ?
                GROUP BY ta.teacher_answer_id, ta.doubt_id, ta.teacher_id, t.name, ta.answer_text, ta.created_at
                ORDER BY ta.teacher_answer_id ASC
            """, doubtId);

            item.put("tags", tags);
            item.put("answers", studentAnswers);
            item.put("teacherAnswers", teacherAnswers);
            result.add(item);
        }
        return result;
    }

    @PostMapping
    public ResponseEntity<?> createDoubt(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthService.AuthenticatedUser student = authService.requireRole(request, "STUDENT");

        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        if (title.isEmpty() || description.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title and description are required."));
        }

        long subjectId = body.get("subjectId") instanceof Number n
                ? n.longValue()
                : parseLongOrDefault(String.valueOf(body.getOrDefault("subjectId", "1")), 1L);
        boolean isAnonymous = Boolean.TRUE.equals(body.get("isAnonymous"));
        String priority = String.valueOf(body.getOrDefault("priority", "Medium")).trim();
        if (!VALID_PRIORITIES.contains(priority)) {
            priority = "Medium";
        }

        Long doubtId;
        try {
            doubtId = jdbcTemplate.queryForObject(
                    "CALL sp_SubmitDoubt(?, ?, ?, ?, ?, ?)",
                    Long.class,
                    student.userId(),
                    subjectId,
                    title,
                    description,
                    isAnonymous,
                    priority
            );
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", extractSqlMessage(e)));
        }

        // Persist tags into tag + doubt_tag tables if supplied
        List<String> tagList = new ArrayList<>();
        if (body.get("tags") instanceof Collection<?> rawTags) {
            for (Object t : rawTags) {
                if (t != null) {
                    String clean = t.toString().trim();
                    if (!clean.isEmpty() && clean.length() <= 50 && !tagList.contains(clean)) {
                        tagList.add(clean);
                    }
                }
            }
        }
        for (String tagName : tagList) {
            jdbcTemplate.update("INSERT IGNORE INTO tag (tag_name) VALUES (?)", tagName);
            Integer tagId = jdbcTemplate.queryForObject(
                    "SELECT tag_id FROM tag WHERE tag_name = ?", Integer.class, tagName);
            if (tagId != null && doubtId != null) {
                jdbcTemplate.update(
                        "INSERT IGNORE INTO doubt_tag (doubt_id, tag_id) VALUES (?, ?)",
                        doubtId, tagId);
            }
        }

        // Auto-index deterministic 32-d embedding into MongoDB vector store
        try {
            List<String> vectorTags = new ArrayList<>(tagList);
            List<Map<String, Object>> subjRows = jdbcTemplate.queryForList(
                    "SELECT subject_name, subject_code FROM subject WHERE subject_id = ?", subjectId);
            if (!subjRows.isEmpty()) {
                Object code = subjRows.get(0).get("subject_code");
                if (code != null) vectorTags.add(code.toString());
            }
            vectorTags.add(priority);
            if (vectorTags.size() < 4) {
                for (String word : title.split("\\s+")) {
                    String cleanWord = word.replaceAll("[^a-zA-Z0-9+-]", "");
                    if (cleanWord.length() >= 3 && vectorTags.size() < 6 && !vectorTags.contains(cleanWord)) {
                        vectorTags.add(cleanWord);
                    }
                }
            }
            List<Double> embedding = vectorSearchService.computeEmbedding(
                    title + " " + description + " " + String.join(" ", vectorTags));
            vectorSearchService.indexDoubtVector(
                    new DoubtVectorDocument(doubtId, title, embedding, vectorTags));
        } catch (Exception ignored) {
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("doubtId", doubtId);
        response.put("subjectId", subjectId);
        response.put("title", title);
        response.put("description", description);
        response.put("isAnonymous", isAnonymous);
        response.put("priority", priority);
        response.put("status", "Pending");
        response.put("tags", tagList);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<Map<String, Object>> postStudentAnswer(
            @PathVariable Integer id,
            @RequestBody AnswerRequest answerRequest,
            HttpServletRequest request) {
        AuthService.AuthenticatedUser student = authService.requireRole(request, "STUDENT");
        String answerText = answerRequest != null && answerRequest.getAnswerText() != null
                ? answerRequest.getAnswerText().trim() : "";
        if (answerText.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Answer text cannot be empty."));
        }

        try {
            // Triggers trg_prevent_answer_on_closed_doubt & trg_after_student_answer enforce closed guard and status update
            jdbcTemplate.update(
                    "INSERT INTO answer (doubt_id, student_id, answer_text) VALUES (?, ?, ?)",
                    id, student.userId(), answerText
            );
            return ResponseEntity.ok(Map.of("message", "Peer answer posted successfully!"));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", extractSqlMessage(e)));
        }
    }

    @PostMapping("/{id}/teacher-answers")
    public ResponseEntity<Map<String, Object>> postTeacherAnswer(
            @PathVariable Integer id,
            @RequestBody AnswerRequest answerRequest,
            HttpServletRequest request) {
        AuthService.AuthenticatedUser teacher = authService.requireRole(request, "TEACHER");
        String answerText = answerRequest != null && answerRequest.getAnswerText() != null
                ? answerRequest.getAnswerText().trim() : "";
        if (answerText.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Official answer text cannot be empty."));
        }

        try {
            // Triggers trg_prevent_teacher_answer_on_closed_doubt & trg_after_teacher_answer handle closed guard and status update
            jdbcTemplate.update(
                    "INSERT INTO teacher_answer (doubt_id, teacher_id, answer_text) VALUES (?, ?, ?)",
                    id, teacher.userId(), answerText
            );
            return ResponseEntity.ok(Map.of("message", "Official faculty answer posted successfully!"));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", extractSqlMessage(e)));
        }
    }

    @PostMapping("/answers/{answerId}/upvote")
    public ResponseEntity<Map<String, Object>> upvoteAnswer(
            @PathVariable Integer answerId,
            HttpServletRequest request) {
        AuthService.AuthenticatedUser student = authService.requireRole(request, "STUDENT");
        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM answer a
                INNER JOIN doubt d ON a.doubt_id = d.doubt_id
                WHERE a.answer_id = ?
                  AND a.deleted_by_admin_id IS NULL
                  AND d.deleted_by_admin_id IS NULL
                """, Integer.class, answerId);
        if (activeCount == null || activeCount == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Active peer answer not found."));
        }
        try {
            // Trigger trg_after_upvote_insert increments answer.upvotes automatically
            jdbcTemplate.update(
                    "INSERT INTO upvote (student_id, answer_id) VALUES (?, ?)",
                    student.userId(), answerId
            );
            return ResponseEntity.ok(Map.of("message", "Upvote recorded!"));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have already upvoted this answer."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", extractSqlMessage(e)));
        }
    }

    @PostMapping("/teacher-answers/{teacherAnswerId}/upvote")
    public ResponseEntity<Map<String, Object>> upvoteTeacherAnswer(
            @PathVariable Integer teacherAnswerId,
            HttpServletRequest request) {
        AuthService.AuthenticatedUser student = authService.requireRole(request, "STUDENT");
        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM teacher_answer ta
                INNER JOIN doubt d ON ta.doubt_id = d.doubt_id
                WHERE ta.teacher_answer_id = ?
                  AND d.deleted_by_admin_id IS NULL
                """, Integer.class, teacherAnswerId);
        if (activeCount == null || activeCount == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Active faculty answer not found."));
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO teacher_answer_upvote (student_id, teacher_answer_id) VALUES (?, ?)",
                    student.userId(), teacherAnswerId
            );
            return ResponseEntity.ok(Map.of("message", "Faculty answer upvoted!"));
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have already upvoted this faculty answer."));
        } catch (DataAccessException e) {
            return ResponseEntity.badRequest().body(Map.of("error", extractSqlMessage(e)));
        }
    }

    @PutMapping("/answers/{answerId}/helpful")
    public ResponseEntity<Map<String, Object>> toggleAnswerHelpful(
            @PathVariable Long answerId,
            @RequestParam(defaultValue = "true") boolean helpful,
            HttpServletRequest request) {
        authService.requireRole(request, "TEACHER", "ADMIN");
        Optional<Answer> opt = answerRepository.findById(answerId);
        if (opt.isEmpty() || opt.get().getDeletedByAdminId() != null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Answer not found."));
        }
        Answer answer = opt.get();
        answer.setIsHelpful(helpful);
        answerRepository.save(answer);
        return ResponseEntity.ok(Map.of(
                "message", helpful ? "Answer marked as helpful." : "Helpful mark removed.",
                "answerId", answerId,
                "isHelpful", helpful
        ));
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<Map<String, String>> closeDoubt(@PathVariable Integer id, HttpServletRequest request) {
        authService.requireRole(request, "TEACHER", "ADMIN");
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doubt WHERE doubt_id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Doubt not found."));
        }
        jdbcTemplate.update("CALL sp_CloseDoubt(?)", id);
        return ResponseEntity.ok(Map.of("message", "Question closed successfully."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDoubt(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "false") boolean hard,
            HttpServletRequest request) {
        AuthService.AuthenticatedUser admin = authService.requireRole(request, "ADMIN");

        if (hard) {
            int deleted = jdbcTemplate.update("DELETE FROM doubt WHERE doubt_id = ?", id);
            if (deleted == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Doubt not found."));
            }
            vectorSearchService.deleteByDoubtId(id.longValue());
            return ResponseEntity.ok(Map.of(
                    "message", "Doubt #" + id + " permanently deleted."
            ));
        }

        int updated = jdbcTemplate.update(
                "UPDATE doubt SET deleted_by_admin_id = ?, status = 'Closed' WHERE doubt_id = ?",
                admin.userId(), id
        );
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Doubt not found."));
        }
        vectorSearchService.deleteByDoubtId(id.longValue());
        return ResponseEntity.ok(Map.of(
                "message", "Doubt #" + id + " moved to moderation archive."
        ));
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<Map<String, String>> restoreDoubt(@PathVariable Integer id, HttpServletRequest request) {
        authService.requireRole(request, "ADMIN");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT doubt_id, title, description FROM doubt WHERE doubt_id = ?", id);
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Doubt not found."));
        }
        Integer teacherAnsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teacher_answer WHERE doubt_id = ?", Integer.class, id);
        Integer studentAnsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer WHERE doubt_id = ? AND deleted_by_admin_id IS NULL", Integer.class, id);
        String restoredStatus = (teacherAnsCount != null && teacherAnsCount > 0)
                ? "Answered by Teacher"
                : (studentAnsCount != null && studentAnsCount > 0) ? "Answered by Student" : "Pending";

        jdbcTemplate.update(
                "UPDATE doubt SET deleted_by_admin_id = NULL, status = ? WHERE doubt_id = ?",
                restoredStatus, id
        );

        String title = String.valueOf(rows.get(0).get("title"));
        String desc = rows.get(0).get("description") != null ? rows.get(0).get("description").toString() : "";
        List<String> tags = jdbcTemplate.queryForList(
                "SELECT tg.tag_name FROM doubt_tag dt JOIN tag tg ON dt.tag_id = tg.tag_id WHERE dt.doubt_id = ?",
                String.class, id);
        List<Double> embedding = vectorSearchService.computeEmbedding(title + " " + desc + " " + String.join(" ", tags));
        vectorSearchService.indexDoubtVector(new DoubtVectorDocument(id.longValue(), title, embedding, tags));

        return ResponseEntity.ok(Map.of("message", "Doubt #" + id + " restored and re-indexed."));
    }

    @DeleteMapping("/answers/{answerId}")
    public ResponseEntity<Map<String, String>> softDeleteAnswer(@PathVariable Integer answerId, HttpServletRequest request) {
        AuthService.AuthenticatedUser admin = authService.requireRole(request, "ADMIN");
        int updated = jdbcTemplate.update(
                "UPDATE answer SET deleted_by_admin_id = ? WHERE answer_id = ?",
                admin.userId(), answerId
        );
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Answer not found."));
        }
        return ResponseEntity.ok(Map.of("message", "Peer answer #" + answerId + " removed by moderator."));
    }

    @GetMapping("/audit")
    public Map<String, Object> getModerationAudit(HttpServletRequest request) {
        authService.requireRole(request, "ADMIN");
        List<Map<String, Object>> deletedDoubts = jdbcTemplate.queryForList("""
            SELECT d.doubt_id AS doubtId,
                   d.title AS title,
                   d.status AS status,
                   d.deleted_by_admin_id AS deletedByAdminId,
                   adm.name AS adminName,
                   s.subject_code AS subjectCode
            FROM doubt d
            LEFT JOIN admin adm ON d.deleted_by_admin_id = adm.admin_id
            LEFT JOIN subject s ON d.subject_id = s.subject_id
            WHERE d.deleted_by_admin_id IS NOT NULL
            ORDER BY d.doubt_id DESC
        """);
        List<Map<String, Object>> deletedAnswers = jdbcTemplate.queryForList("""
            SELECT a.answer_id AS answerId,
                   a.doubt_id AS doubtId,
                   a.answer_text AS answerText,
                   a.deleted_by_admin_id AS deletedByAdminId,
                   adm.name AS adminName
            FROM answer a
            LEFT JOIN admin adm ON a.deleted_by_admin_id = adm.admin_id
            WHERE a.deleted_by_admin_id IS NOT NULL
            ORDER BY a.answer_id DESC
        """);
        return Map.of("deletedDoubts", deletedDoubts, "deletedAnswers", deletedAnswers);
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
                       IFNULL(SUM(CASE WHEN a.is_helpful = TRUE THEN 1 ELSE 0 END), 0) AS helpfulCount
                FROM student s
                LEFT JOIN answer a ON s.student_id = a.student_id AND a.deleted_by_admin_id IS NULL
                GROUP BY s.student_id, s.name, s.branch
            )
            SELECT studentId,
                   name,
                   branch,
                   totalAnswers,
                   totalUpvotes,
                   helpfulCount,
                   fn_CalculateStudentReputation(studentId) AS score
            FROM StudentStats
            ORDER BY score DESC, totalUpvotes DESC
        """;
        return jdbcTemplate.queryForList(cteSql);
    }

    @GetMapping("/{id}/related")
    public List<Map<String, Object>> getRelatedDoubtsRecursive(@PathVariable Integer id) {
        String recursiveSql = """
            WITH RECURSIVE RelatedDoubtGraph AS (
                SELECT d.doubt_id,
                       d.title,
                       0 AS hop_distance,
                       CAST(d.doubt_id AS CHAR(200)) AS visited_path
                FROM doubt d
                WHERE d.doubt_id = ?
                  AND d.deleted_by_admin_id IS NULL
                UNION ALL
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
            SELECT doubt_id AS doubtId,
                   title,
                   MIN(hop_distance) AS minTagHops,
                   ANY_VALUE(visited_path) AS samplePath
            FROM RelatedDoubtGraph
            WHERE doubt_id <> ?
            GROUP BY doubt_id, title
            ORDER BY minTagHops ASC, doubt_id ASC
        """;
        return jdbcTemplate.queryForList(recursiveSql, id, id);
    }

    private long parseLongOrDefault(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String extractSqlMessage(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        String msg = cause != null ? cause.getMessage() : e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "Unable to complete request.";
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("foreign key constraint fails") || lower.contains("cannot add or update a child row")) {
            return "Selected subject, doubt, or user reference was not found.";
        }
        if (lower.contains("data too long for column") || lower.contains("data truncation")) {
            return "Input exceeds the maximum allowed length.";
        }
        return msg;
    }
}
