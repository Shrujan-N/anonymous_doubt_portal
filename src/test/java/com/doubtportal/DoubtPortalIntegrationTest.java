package com.doubtportal;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.AnswerRepository;
import com.doubtportal.repository.DoubtRepository;
import com.doubtportal.repository.TeacherAnswerRepository;
import com.doubtportal.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DoubtPortalIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DoubtRepository doubtRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private TeacherAnswerRepository teacherAnswerRepository;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configureHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private String loginAndGetToken(String email, String password, String role) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password, "role", role)),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "Expected login 200 for " + email);
        assertNotNull(resp.getBody());
        Object token = resp.getBody().get("token");
        assertNotNull(token);
        return token.toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth-Token", token);
        return headers;
    }

    @Test
    void testAuthAndRbacEnforcement() {
        // Invalid credentials -> 401
        ResponseEntity<Map<String, Object>> badLogin = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "rahul@pes.edu", "password", "wrong_pwd", "role", "STUDENT")),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.UNAUTHORIZED, badLogin.getStatusCode());

        // Unauthenticated /me -> 401
        ResponseEntity<Map<String, Object>> unauthMe = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, unauthMe.getStatusCode());

        // Unauthenticated POST /api/doubts -> 401
        ResponseEntity<Map<String, Object>> unauthPost = restTemplate.exchange(
                "/api/doubts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("subjectId", 1, "title", "Blocked", "description", "Unauth")),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthPost.getStatusCode());

        // Student RBAC check -> 403 on teacher/admin endpoints
        String studentToken = loginAndGetToken("rahul@pes.edu", "hashed_pwd_1", "STUDENT");
        ResponseEntity<Map<String, Object>> meResp = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders(studentToken)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertEquals("STUDENT", meResp.getBody().get("role"));

        ResponseEntity<Map<String, Object>> studentCloseForbidden = restTemplate.exchange(
                "/api/doubts/1/close", HttpMethod.PUT, new HttpEntity<>(authHeaders(studentToken)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.FORBIDDEN, studentCloseForbidden.getStatusCode());

        ResponseEntity<Map<String, Object>> studentDeleteForbidden = restTemplate.exchange(
                "/api/doubts/1", HttpMethod.DELETE, new HttpEntity<>(authHeaders(studentToken)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.FORBIDDEN, studentDeleteForbidden.getStatusCode());

        // Logout invalidates token
        restTemplate.exchange(
                "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(authHeaders(studentToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        ResponseEntity<Map<String, Object>> afterLogout = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders(studentToken)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.getStatusCode());

        // Explicitly invalid X-Auth-Token -> 401
        ResponseEntity<Map<String, Object>> badToken = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders("invalid-token-xyz")),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.UNAUTHORIZED, badToken.getStatusCode());

        // Soft-deleted answer #5 cannot be upvoted
        String newStudentToken = loginAndGetToken("rahul@pes.edu", "hashed_pwd_1", "STUDENT");
        ResponseEntity<Map<String, Object>> deletedAnsUpvote = restTemplate.exchange(
                "/api/doubts/answers/5/upvote", HttpMethod.POST, new HttpEntity<>(authHeaders(newStudentToken)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.NOT_FOUND, deletedAnsUpvote.getStatusCode());
    }

    @Test
    void testEndToEndDoubtTriggersProceduresCascadeAndVectorSync() {
        String student1Token = loginAndGetToken("rahul@pes.edu", "hashed_pwd_1", "STUDENT");
        String student2Token = loginAndGetToken("ananya@pes.edu", "hashed_pwd_2", "STUDENT");
        String teacherToken = loginAndGetToken("sudhir@pes.edu", "prof_pwd_1", "TEACHER");
        String adminToken = loginAndGetToken("admin@pes.edu", "admin_root_pwd", "ADMIN");

        // Invalid subjectId should return clean validation message without raw SQL constraint jargon
        ResponseEntity<Map<String, Object>> badSubjectResp = restTemplate.exchange(
                "/api/doubts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "subjectId", 9999,
                        "title", "Invalid Subject Check",
                        "description", "Should return clean error message."
                ), authHeaders(student1Token)),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.BAD_REQUEST, badSubjectResp.getStatusCode());
        String badErr = String.valueOf(badSubjectResp.getBody().get("error"));
        assertFalse(badErr.contains("FOREIGN KEY") || badErr.contains("doubt_portal"), "Must not expose raw FK schema names");

        String uniqueToken = "ZxQ94MVCC";
        Long createdDoubtId = null;
        try {
            // 1. Student creates anonymous doubt via sp_SubmitDoubt
            ResponseEntity<Map<String, Object>> createResp = restTemplate.exchange(
                    "/api/doubts",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(
                            "subjectId", 1,
                            "title", "How does " + uniqueToken + " handle snapshot isolation?",
                            "description", "Testing InnoDB MVCC read views and stored procedure insertion.",
                            "isAnonymous", true,
                            "priority", "High",
                            "tags", List.of("SQL", "Transactions")
                    ), authHeaders(student1Token)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
            createdDoubtId = ((Number) createResp.getBody().get("doubtId")).longValue();
            assertEquals("Pending", doubtRepository.findById(createdDoubtId).orElseThrow().getStatus());

            // 2. Full-Text Scan finds new doubt and masks anonymous identity for other users
            ResponseEntity<List<Map<String, Object>>> ftResp = restTemplate.exchange(
                    "/api/doubts?q=" + uniqueToken,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(student2Token)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, ftResp.getStatusCode());
            assertFalse(ftResp.getBody().isEmpty(), "Full-text search should match new doubt");
            assertEquals("Anonymous", ftResp.getBody().get(0).get("authorDisplay"));
            assertNull(ftResp.getBody().get(0).get("studentId"));

            // 3. Peer answer fires SQL trigger trg_after_student_answer -> 'Answered by Student'
            ResponseEntity<Map<String, Object>> ansResp = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId + "/answers",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("answerText", "MVCC keeps undo log versions per transaction ID."), authHeaders(student2Token)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, ansResp.getStatusCode());
            assertEquals("Answered by Student", doubtRepository.findById(createdDoubtId).orElseThrow().getStatus());

            var answers = answerRepository.findByDoubtId(createdDoubtId);
            assertEquals(1, answers.size());
            Long answerId = answers.get(0).getAnswerId();

            // 4. Upvote fires SQL trigger trg_after_upvote_insert -> answer.upvotes == 1
            ResponseEntity<Map<String, Object>> upvoteResp = restTemplate.exchange(
                    "/api/doubts/answers/" + answerId + "/upvote",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(student1Token)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, upvoteResp.getStatusCode());
            assertEquals(1, answerRepository.findById(answerId).orElseThrow().getUpvotes());

            // Duplicate upvote rejected by composite PK
            ResponseEntity<Map<String, Object>> dupUpvote = restTemplate.exchange(
                    "/api/doubts/answers/" + answerId + "/upvote",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(student1Token)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.BAD_REQUEST, dupUpvote.getStatusCode());

            // 5. Teacher posts faculty answer -> trg_after_teacher_answer -> 'Answered by Teacher'
            ResponseEntity<Map<String, Object>> taResp = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId + "/teacher-answers",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("answerText", "Correct, repeatable read creates a consistent snapshot on first read."), authHeaders(teacherToken)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, taResp.getStatusCode());
            assertEquals("Answered by Teacher", doubtRepository.findById(createdDoubtId).orElseThrow().getStatus());
            assertEquals(1, teacherAnswerRepository.findByDoubtId(createdDoubtId).size());

            // 6. Teacher closes doubt via sp_CloseDoubt; subsequent answer blocked by trigger
            ResponseEntity<Map<String, Object>> closeResp = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId + "/close",
                    HttpMethod.PUT,
                    new HttpEntity<>(authHeaders(teacherToken)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, closeResp.getStatusCode());
            assertEquals("Question closed successfully.", closeResp.getBody().get("message"));
            assertEquals("Closed", doubtRepository.findById(createdDoubtId).orElseThrow().getStatus());

            ResponseEntity<Map<String, Object>> blockedAnswer = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId + "/answers",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("answerText", "Should be rejected by BEFORE INSERT trigger."), authHeaders(student1Token)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.BAD_REQUEST, blockedAnswer.getStatusCode());

            // 7. Admin soft-delete sets deleted_by_admin_id, then restore, then hard-purge CASCADE
            ResponseEntity<Map<String, Object>> softDel = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(adminToken)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, softDel.getStatusCode());
            assertEquals("Doubt #" + createdDoubtId + " moved to moderation archive.", softDel.getBody().get("message"));
            assertEquals(1L, doubtRepository.findById(createdDoubtId).orElseThrow().getDeletedByAdminId());

            ResponseEntity<Map<String, Object>> restore = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId + "/restore",
                    HttpMethod.PUT,
                    new HttpEntity<>(authHeaders(adminToken)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, restore.getStatusCode());
            assertEquals("Doubt #" + createdDoubtId + " restored and re-indexed.", restore.getBody().get("message"));
            assertNull(doubtRepository.findById(createdDoubtId).orElseThrow().getDeletedByAdminId());

            ResponseEntity<Map<String, Object>> hardPurge = restTemplate.exchange(
                    "/api/doubts/" + createdDoubtId + "?hard=true",
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(adminToken)),
                    new ParameterizedTypeReference<>() {}
            );
            assertEquals(HttpStatus.OK, hardPurge.getStatusCode());
            assertEquals("Doubt #" + createdDoubtId + " permanently deleted.", hardPurge.getBody().get("message"));
            assertTrue(doubtRepository.findById(createdDoubtId).isEmpty());
            assertTrue(answerRepository.findByDoubtId(createdDoubtId).isEmpty(), "ON DELETE CASCADE must remove answers");
            assertTrue(teacherAnswerRepository.findByDoubtId(createdDoubtId).isEmpty(), "ON DELETE CASCADE must remove teacher answers");
            Long finalDoubtId = createdDoubtId;
            assertTrue(vectorSearchService.getAllVectors().stream().noneMatch(v -> finalDoubtId.equals(v.getDoubtId())));
            createdDoubtId = null;
        } finally {
            if (createdDoubtId != null) {
                jdbcTemplate.update("DELETE FROM doubt WHERE doubt_id = ?", createdDoubtId);
                vectorSearchService.deleteByDoubtId(createdDoubtId);
            }
        }
    }

    @Test
    void testVectorCosineSimilarityAndRud() {
        List<DoubtVectorDocument> matches = vectorSearchService.semanticSearch("B+ tree disk storage");
        assertFalse(matches.isEmpty());
        assertEquals(1L, matches.get(0).getDoubtId(), "B+ tree query should rank Doubt #1 highest by cosine similarity");
        assertTrue(matches.get(0).getSimilarityScore() > 0.45);

        // Unrelated domain query must not match unrelated doubts with inflated scores
        List<DoubtVectorDocument> unrelated = vectorSearchService.semanticSearch("photosynthesis biology plant cell");
        assertTrue(unrelated.isEmpty(), "Unrelated query should not exceed cosine threshold");

        DoubtVectorDocument temp = vectorSearchService.indexDoubtVector(
                new DoubtVectorDocument(999L, "Temporary JUnit Vector Topic", null, List.of("JUnit", "NoSQL")));
        assertNotNull(temp.getId());
        assertEquals(32, temp.getVectorEmbedding().size());

        // Multiple standalone custom vectors without doubtId (null or 0) must not collide
        DoubtVectorDocument customA = vectorSearchService.indexDoubtVector(
                new DoubtVectorDocument(0L, "Custom Standalone Topic A", null, List.of("CustomA")));
        DoubtVectorDocument customB = vectorSearchService.indexDoubtVector(
                new DoubtVectorDocument(null, "Custom Standalone Topic B", null, List.of("CustomB")));
        assertNotEquals(customA.getId(), customB.getId(), "Standalone vectors must get distinct IDs");

        try {
            DoubtVectorDocument updated = vectorSearchService.updateVectorTags(temp.getId(), List.of("JUnit", "UpdatedTag"));
            assertTrue(updated.getTags().contains("UpdatedTag"));
            assertFalse(vectorSearchService.searchByTag("UpdatedTag").isEmpty());
        } finally {
            String delMsg = vectorSearchService.deleteVectorDocument(temp.getId());
            assertEquals("Topic entry removed from search index.", delMsg);
            vectorSearchService.deleteVectorDocument(customA.getId());
            vectorSearchService.deleteVectorDocument(customB.getId());
        }
    }

    @Test
    void testSubjectsLeaderboardAndRecursiveCte() {
        ResponseEntity<List<Map<String, Object>>> subjects = restTemplate.exchange(
                "/api/subjects", HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, subjects.getStatusCode());
        assertTrue(subjects.getBody().size() >= 4);

        ResponseEntity<List<Map<String, Object>>> leaderboard = restTemplate.exchange(
                "/api/doubts/leaderboard", HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, leaderboard.getStatusCode());
        assertFalse(leaderboard.getBody().isEmpty());
        assertTrue(((Number) leaderboard.getBody().get(0).get("score")).intValue() > 0);

        ResponseEntity<List<Map<String, Object>>> related = restTemplate.exchange(
                "/api/doubts/1/related", HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, related.getStatusCode());
        assertFalse(related.getBody().isEmpty(), "Recursive CTE should find tag-connected doubts from Doubt #1");
    }
}
