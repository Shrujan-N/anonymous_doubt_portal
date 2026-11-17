package com.doubtportal.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final JdbcTemplate jdbcTemplate;

    public SubjectController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> getAllSubjects() {
        String sql = """
            SELECT s.subject_id AS subjectId,
                   s.subject_name AS subjectName,
                   s.subject_code AS subjectCode,
                   s.teacher_id AS teacherId,
                   t.name AS teacherName,
                   t.department AS department
            FROM subject s
            LEFT JOIN teacher t ON s.teacher_id = t.teacher_id
            ORDER BY s.subject_id ASC
        """;
        return jdbcTemplate.queryForList(sql);
    }
}
