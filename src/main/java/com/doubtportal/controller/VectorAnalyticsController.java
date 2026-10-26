package com.doubtportal.controller;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.service.VectorSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/nosql/vectors")
@CrossOrigin(origins = "*")
public class VectorAnalyticsController {
    private final VectorSearchService vectorService;

    public VectorAnalyticsController(VectorSearchService vectorService) {
        this.vectorService = vectorService;
    }

    @PostMapping
    public ResponseEntity<DoubtVectorDocument> createVector(@RequestBody DoubtVectorDocument document) {
        return ResponseEntity.ok(vectorService.indexDoubtVector(document));
    }

    @GetMapping
    public ResponseEntity<List<DoubtVectorDocument>> getAllVectors() {
        return ResponseEntity.ok(vectorService.getAllVectors());
    }

    @GetMapping("/search")
    public ResponseEntity<List<DoubtVectorDocument>> searchByTag(@RequestParam String tag) {
        return ResponseEntity.ok(vectorService.searchByTag(tag));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<DoubtVectorDocument> updateTags(@PathVariable String id,
                                                           @RequestBody List<String> tags) {
        return ResponseEntity.ok(vectorService.updateVectorTags(id, tags));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteVector(@PathVariable String id) {
        return ResponseEntity.ok(vectorService.deleteVectorDocument(id));
    }
}
