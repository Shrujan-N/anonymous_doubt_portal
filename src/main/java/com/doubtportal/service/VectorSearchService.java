package com.doubtportal.service;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtVectorMongoRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class VectorSearchService {
    private final DoubtVectorMongoRepository vectorRepository;

    public VectorSearchService(DoubtVectorMongoRepository vectorRepository) {
        this.vectorRepository = vectorRepository;
    }

    public DoubtVectorDocument indexDoubtVector(DoubtVectorDocument document) {
        return vectorRepository.save(document);
    }

    public List<DoubtVectorDocument> getAllVectors() {
        return vectorRepository.findAll();
    }

    public List<DoubtVectorDocument> searchByTag(String tag) {
        return vectorRepository.findByTagsContaining(tag);
    }

    public DoubtVectorDocument updateVectorTags(String id, List<String> newTags) {
        DoubtVectorDocument document = vectorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vector document not found with ID: " + id));
        document.setTags(newTags);
        return vectorRepository.save(document);
    }

    public String deleteVectorDocument(String id) {
        if (!vectorRepository.existsById(id)) {
            throw new IllegalArgumentException("Vector document not found with ID: " + id);
        }
        vectorRepository.deleteById(id);
        return "Vector Document ID " + id + " successfully deleted from MongoDB.";
    }
}
