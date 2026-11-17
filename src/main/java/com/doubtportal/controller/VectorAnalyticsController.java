package com.doubtportal.controller;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.service.AuthService;
import com.doubtportal.service.VectorSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nosql/vectors")
public class VectorAnalyticsController {
    private final VectorSearchService vectorService;
    private final AuthService authService;

    public VectorAnalyticsController(VectorSearchService vectorService, AuthService authService) {
        this.vectorService = vectorService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> createVector(@RequestBody DoubtVectorDocument document, HttpServletRequest request) {
        authService.requireRole(request, "TEACHER", "ADMIN");
        try {
            return ResponseEntity.ok(vectorService.indexDoubtVector(document));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<DoubtVectorDocument>> getAllVectors() {
        return ResponseEntity.ok(vectorService.getAllVectors());
    }

    @GetMapping("/search")
    public ResponseEntity<List<DoubtVectorDocument>> searchByTag(@RequestParam(defaultValue = "") String tag) {
        return ResponseEntity.ok(vectorService.searchByTag(tag));
    }

    @GetMapping("/semantic")
    public ResponseEntity<List<DoubtVectorDocument>> semanticSearch(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(vectorService.semanticSearch(q));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<?> updateTags(@PathVariable String id,
                                        @RequestBody List<String> tags,
                                        HttpServletRequest request) {
        authService.requireRole(request, "TEACHER", "ADMIN");
        try {
            return ResponseEntity.ok(vectorService.updateVectorTags(id, tags));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVector(@PathVariable String id, HttpServletRequest request) {
        authService.requireRole(request, "TEACHER", "ADMIN");
        try {
            return ResponseEntity.ok(Map.of("message", vectorService.deleteVectorDocument(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
