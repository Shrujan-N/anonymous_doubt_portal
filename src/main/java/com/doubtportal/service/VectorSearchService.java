package com.doubtportal.service;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtVectorMongoRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {
    private static final int EMBEDDING_DIMENSIONS = 32;

    private final DoubtVectorMongoRepository vectorRepository;
    private final List<DoubtVectorDocument> fallbackStore = new CopyOnWriteArrayList<>();

    public VectorSearchService(DoubtVectorMongoRepository vectorRepository) {
        this.vectorRepository = vectorRepository;
    }

    /**
     * Deterministic 32-dimensional L2-normalized embedding using weighted token + 3-char shingle hashing.
     * Matches database/04_nosql_setup.js exactly so CLI-seeded MongoDB documents and Java inserts align.
     */
    public List<Double> computeEmbedding(String text) {
        double[] vec = new double[EMBEDDING_DIMENSIONS];
        if (text == null || text.isBlank()) {
            return Arrays.stream(vec).boxed().collect(Collectors.toList());
        }

        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+ ]", " ").trim();
        if (cleaned.isEmpty()) {
            return Arrays.stream(vec).boxed().collect(Collectors.toList());
        }

        String[] tokens = cleaned.split("\\s+");
        List<String> features = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            features.add(tok);
            weights.add(3.0);
            if (tok.length() >= 3) {
                for (int i = 0; i <= tok.length() - 3; i++) {
                    features.add(tok.substring(i, i + 3));
                    weights.add(1.0);
                }
            }
        }

        for (int fIdx = 0; fIdx < features.size(); fIdx++) {
            String feat = features.get(fIdx);
            double weight = weights.get(fIdx);
            long h1 = 2166136261L;
            long h2 = 5381L;
            for (int i = 0; i < feat.length(); i++) {
                int c = feat.charAt(i);
                h1 = ((h1 ^ c) * 16777619L) & 0xFFFFFFFFL;
                h2 = ((h2 * 33L) + c) & 0xFFFFFFFFL;
            }
            long m1 = (h1 ^ (h1 >>> 16)) & 0xFFFFFFFFL;
            long m2 = (h2 ^ (h2 >>> 16)) & 0xFFFFFFFFL;
            int bucket = (int) (m1 % EMBEDDING_DIMENSIONS);
            double sign = (m2 % 2L == 0L) ? 1.0 : -1.0;
            vec[bucket] += sign * weight;
        }

        double normSq = 0.0;
        for (double v : vec) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        List<Double> result = new ArrayList<>(EMBEDDING_DIMENSIONS);
        for (double v : vec) {
            double normalized = norm == 0.0 ? 0.0 : Math.round((v / norm) * 1_000_000.0) / 1_000_000.0;
            result.add(normalized);
        }
        return result;
    }

    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double va = a.get(i) != null ? a.get(i) : 0.0;
            double vb = b.get(i) != null ? b.get(i) : 0.0;
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return Math.round((dot / (Math.sqrt(normA) * Math.sqrt(normB))) * 10000.0) / 10000.0;
    }

    @PostConstruct
    public void initSeedVectors() {
        List<DoubtVectorDocument> seeds = List.of(
                createSeedDoc("vec-seed-1", 1L,
                        "Difference between B-Tree and B+ Tree indexing?",
                        "Why are B+ trees preferred for disk storage and range scans in relational databases?",
                        List.of("SQL", "Indexing", "B-Tree", "DBMS", "High")),
                createSeedDoc("vec-seed-2", 2L,
                        "Race condition in JK Flip Flop",
                        "How does a master-slave JK flip-flop setup prevent the race-around condition?",
                        List.of("Electronics", "FlipFlop", "Digital", "Medium")),
                createSeedDoc("vec-seed-3", 3L,
                        "Bankers algorithm vs deadlock detection",
                        "When should an operating system prefer deadlock avoidance over periodic cycle detection?",
                        List.of("OperatingSystems", "Deadlock", "Concurrency", "High")),
                createSeedDoc("vec-seed-4", 4L,
                        "Why is BCNF stricter than 3NF?",
                        "Can every 3NF relation be decomposed into BCNF without losing functional dependencies?",
                        List.of("SQL", "Normalization", "DBMS", "Medium")),
                createSeedDoc("vec-seed-5", 5L,
                        "TCP slow start vs congestion avoidance",
                        "How does congestion window cwnd grow before and after hitting ssthresh?",
                        List.of("Networks", "TCP-IP", "ExamPrep", "Low"))
        );

        fallbackStore.clear();
        fallbackStore.addAll(seeds);

        try {
            if (vectorRepository.count() == 0) {
                for (DoubtVectorDocument seed : seeds) {
                    DoubtVectorDocument mongoDoc = new DoubtVectorDocument(
                            seed.getDoubtId(),
                            seed.getTitle(),
                            seed.getVectorEmbedding(),
                            seed.getTags()
                    );
                    vectorRepository.save(mongoDoc);
                }
            }
        } catch (Exception ignored) {
            // Fallback store remains active when local mongod service is not started
        }
    }

    private DoubtVectorDocument createSeedDoc(String id, Long doubtId, String title, String description, List<String> tags) {
        String combined = title + " " + description + " " + String.join(" ", tags);
        DoubtVectorDocument doc = new DoubtVectorDocument(doubtId, title, computeEmbedding(combined), new ArrayList<>(tags));
        doc.setId(id);
        return doc;
    }

    public DoubtVectorDocument indexDoubtVector(DoubtVectorDocument document) {
        if (document.getTitle() == null || document.getTitle().isBlank()) {
            throw new IllegalArgumentException("Topic title is required.");
        }
        if (document.getDoubtId() != null && document.getDoubtId() <= 0) {
            document.setDoubtId(null);
        }
        List<String> tags = document.getTags() != null ? new ArrayList<>(document.getTags()) : new ArrayList<>();
        document.setTags(tags);
        if (document.getVectorEmbedding() == null || document.getVectorEmbedding().isEmpty()) {
            document.setVectorEmbedding(computeEmbedding(document.getTitle() + " " + String.join(" ", tags)));
        }
        if (document.getActive() == null) {
            document.setActive(true);
        }
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(Instant.now());
        }

        try {
            if (document.getDoubtId() != null && document.getDoubtId() > 0) {
                List<DoubtVectorDocument> existing = vectorRepository.findAllByDoubtId(document.getDoubtId());
                if (!existing.isEmpty()) {
                    DoubtVectorDocument target = existing.get(0);
                    target.setTitle(document.getTitle());
                    target.setTags(document.getTags());
                    target.setVectorEmbedding(document.getVectorEmbedding());
                    target.setActive(true);
                    DoubtVectorDocument saved = vectorRepository.save(target);
                    upsertFallback(saved);
                    return saved;
                }
            }
            DoubtVectorDocument saved = vectorRepository.save(document);
            upsertFallback(saved);
            return saved;
        } catch (Exception e) {
            if (document.getId() == null || document.getId().isBlank()) {
                document.setId("vec-" + System.currentTimeMillis() + "-" + fallbackStore.size());
            }
            upsertFallback(document);
            return document;
        }
    }

    private void upsertFallback(DoubtVectorDocument doc) {
        fallbackStore.removeIf(existing ->
                (doc.getId() != null && doc.getId().equals(existing.getId())) ||
                        (doc.getDoubtId() != null && doc.getDoubtId() > 0 && doc.getDoubtId().equals(existing.getDoubtId()))
        );
        fallbackStore.add(copyDoc(doc));
    }

    private DoubtVectorDocument copyDoc(DoubtVectorDocument src) {
        DoubtVectorDocument copy = new DoubtVectorDocument(
                src.getDoubtId(),
                src.getTitle(),
                src.getVectorEmbedding() != null ? new ArrayList<>(src.getVectorEmbedding()) : List.of(),
                src.getTags() != null ? new ArrayList<>(src.getTags()) : List.of()
        );
        copy.setId(src.getId());
        copy.setActive(src.getActive());
        copy.setCreatedAt(src.getCreatedAt());
        return copy;
    }

    public List<DoubtVectorDocument> getAllVectors() {
        try {
            List<DoubtVectorDocument> docs = vectorRepository.findAll();
            return docs.stream()
                    .filter(d -> d.getActive() == null || d.getActive())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return fallbackStore.stream()
                    .filter(d -> d.getActive() == null || d.getActive())
                    .map(this::copyDoc)
                    .collect(Collectors.toList());
        }
    }

    public List<DoubtVectorDocument> searchByTag(String tag) {
        List<DoubtVectorDocument> allDocs = getAllVectors();
        if (tag == null || tag.trim().isEmpty()) {
            return allDocs;
        }
        String query = tag.trim().toLowerCase(Locale.ROOT);
        List<Double> queryVec = computeEmbedding(query);

        return allDocs.stream()
                .filter(doc -> {
                    boolean titleMatch = doc.getTitle() != null
                            && doc.getTitle().toLowerCase(Locale.ROOT).contains(query);
                    boolean tagMatch = doc.getTags() != null
                            && doc.getTags().stream().anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(query));
                    return titleMatch || tagMatch;
                })
                .peek(doc -> doc.setSimilarityScore(cosineSimilarity(queryVec, doc.getVectorEmbedding())))
                .sorted(Comparator.comparing(
                        (DoubtVectorDocument d) -> d.getSimilarityScore() != null ? d.getSimilarityScore() : 0.0
                ).reversed())
                .collect(Collectors.toList());
    }

    public List<DoubtVectorDocument> semanticSearch(String query) {
        List<DoubtVectorDocument> allDocs = getAllVectors();
        if (query == null || query.trim().isEmpty()) {
            return allDocs;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        List<Double> queryVec = computeEmbedding(normalizedQuery);

        List<DoubtVectorDocument> scored = new ArrayList<>();
        for (DoubtVectorDocument doc : allDocs) {
            double sim = cosineSimilarity(queryVec, doc.getVectorEmbedding());
            boolean keywordHit = (doc.getTitle() != null && doc.getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                    || (doc.getTags() != null && doc.getTags().stream().anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(normalizedQuery)));
            if (sim > 0.22 || keywordHit) {
                doc.setSimilarityScore(sim);
                scored.add(doc);
            }
        }

        scored.sort(Comparator.comparing(
                (DoubtVectorDocument d) -> d.getSimilarityScore() != null ? d.getSimilarityScore() : 0.0
        ).reversed());
        return scored;
    }

    public DoubtVectorDocument updateVectorTags(String id, List<String> newTags) {
        return updateVectorTags(id, newTags, null);
    }

    public DoubtVectorDocument updateVectorTags(String id, List<String> newTags, String newTitle) {
        List<String> cleanTags = newTags == null ? List.of() : newTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        try {
            Optional<DoubtVectorDocument> opt = vectorRepository.findById(id);
            if (opt.isPresent()) {
                DoubtVectorDocument doc = opt.get();
                if (newTitle != null && !newTitle.isBlank()) {
                    doc.setTitle(newTitle.trim());
                }
                doc.setTags(cleanTags);
                doc.setVectorEmbedding(computeEmbedding(doc.getTitle() + " " + String.join(" ", cleanTags)));
                DoubtVectorDocument saved = vectorRepository.save(doc);
                upsertFallback(saved);
                return saved;
            }
        } catch (Exception ignored) {
        }

        for (DoubtVectorDocument doc : fallbackStore) {
            if (id.equals(doc.getId())) {
                if (newTitle != null && !newTitle.isBlank()) {
                    doc.setTitle(newTitle.trim());
                }
                doc.setTags(cleanTags);
                doc.setVectorEmbedding(computeEmbedding(doc.getTitle() + " " + String.join(" ", cleanTags)));
                return doc;
            }
        }
        throw new IllegalArgumentException("Topic entry not found with ID: " + id);
    }

    public void deleteByDoubtId(Long doubtId) {
        if (doubtId == null) return;
        try {
            vectorRepository.deleteByDoubtId(doubtId);
        } catch (Exception ignored) {
        }
        fallbackStore.removeIf(doc -> doubtId.equals(doc.getDoubtId()));
    }

    public String deleteVectorDocument(String id) {
        boolean deleted = false;
        try {
            if (vectorRepository.existsById(id)) {
                vectorRepository.deleteById(id);
                deleted = true;
            }
        } catch (Exception ignored) {
        }
        boolean removedFallback = fallbackStore.removeIf(doc -> id.equals(doc.getId()));
        if (!deleted && !removedFallback) {
            throw new IllegalArgumentException("Topic entry not found with ID: " + id);
        }
        return "Topic entry removed from search index.";
    }
}
