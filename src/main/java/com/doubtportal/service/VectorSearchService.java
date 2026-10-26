package com.doubtportal.service;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtVectorMongoRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {
    private final DoubtVectorMongoRepository vectorRepository;
    private final List<DoubtVectorDocument> fallbackStore = new CopyOnWriteArrayList<>();

    public VectorSearchService(DoubtVectorMongoRepository vectorRepository) {
        this.vectorRepository = vectorRepository;
    }

    @PostConstruct
    public void initSeedVectors() {
        DoubtVectorDocument doc1 = new DoubtVectorDocument(
                1L,
                "Difference between B-Tree and B+ Tree indexing?",
                List.of(0.123, 0.456, 0.789, 0.101),
                List.of("SQL", "Indexing", "B-Tree", "DBMS")
        );
        doc1.setId("vec-seed-1");

        DoubtVectorDocument doc2 = new DoubtVectorDocument(
                2L,
                "Race condition in JK Flip Flop",
                List.of(0.999, 0.888, 0.777, 0.666),
                List.of("Electronics", "FlipFlop", "Digital")
        );
        doc2.setId("vec-seed-2");

        fallbackStore.add(doc1);
        fallbackStore.add(doc2);

        try {
            if (vectorRepository.count() == 0) {
                vectorRepository.save(new DoubtVectorDocument(
                        1L,
                        "Difference between B-Tree and B+ Tree indexing?",
                        List.of(0.123, 0.456, 0.789, 0.101),
                        List.of("SQL", "Indexing", "B-Tree", "DBMS")
                ));
                vectorRepository.save(new DoubtVectorDocument(
                        2L,
                        "Race condition in JK Flip Flop",
                        List.of(0.999, 0.888, 0.777, 0.666),
                        List.of("Electronics", "FlipFlop", "Digital")
                ));
            }
        } catch (Exception ignored) {
            // Uses fallbackStore seamlessly if local mongod service is not started
        }
    }

    public DoubtVectorDocument indexDoubtVector(DoubtVectorDocument document) {
        try {
            return vectorRepository.save(document);
        } catch (Exception e) {
            if (document.getId() == null) {
                document.setId("vec-" + System.currentTimeMillis());
            }
            fallbackStore.add(document);
            return document;
        }
    }

    public List<DoubtVectorDocument> getAllVectors() {
        try {
            List<DoubtVectorDocument> docs = vectorRepository.findAll();
            return docs.isEmpty() ? new ArrayList<>(fallbackStore) : docs;
        } catch (Exception e) {
            return new ArrayList<>(fallbackStore);
        }
    }

    public List<DoubtVectorDocument> searchByTag(String tag) {
        List<DoubtVectorDocument> allDocs = getAllVectors();
        if (tag == null || tag.trim().isEmpty()) {
            return allDocs;
        }
        String query = tag.trim().toLowerCase(Locale.ROOT);
        return allDocs.stream()
                .filter(doc -> {
                    boolean titleMatch = doc.getTitle() != null
                            && doc.getTitle().toLowerCase(Locale.ROOT).contains(query);
                    boolean tagMatch = doc.getTags() != null
                            && doc.getTags().stream().anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(query));
                    return titleMatch || tagMatch;
                })
                .collect(Collectors.toList());
    }

    public DoubtVectorDocument updateVectorTags(String id, List<String> newTags) {
        try {
            DoubtVectorDocument document = vectorRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Vector document not found with ID: " + id));
            document.setTags(newTags);
            return vectorRepository.save(document);
        } catch (Exception e) {
            for (DoubtVectorDocument doc : fallbackStore) {
                if (id.equals(doc.getId())) {
                    doc.setTags(newTags);
                    return doc;
                }
            }
            throw new IllegalArgumentException("Vector document not found with ID: " + id);
        }
    }

    public String deleteVectorDocument(String id) {
        try {
            if (vectorRepository.existsById(id)) {
                vectorRepository.deleteById(id);
                return "Vector Document ID " + id + " successfully deleted from MongoDB.";
            }
        } catch (Exception ignored) {
        }
        fallbackStore.removeIf(doc -> id.equals(doc.getId()));
        return "Vector Document ID " + id + " successfully deleted.";
    }
}