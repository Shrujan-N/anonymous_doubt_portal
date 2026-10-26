package com.doubtportal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "doubt_vectors")
public class DoubtVectorDocument {
    @Id
    private String id;
    private Long doubtId;
    private String title;
    private List<Double> vectorEmbedding;
    private List<String> tags;

    public DoubtVectorDocument() {}

    public DoubtVectorDocument(Long doubtId, String title, List<Double> vectorEmbedding, List<String> tags) {
        this.doubtId = doubtId;
        this.title = title;
        this.vectorEmbedding = vectorEmbedding;
        this.tags = tags;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Double> getVectorEmbedding() { return vectorEmbedding; }
    public void setVectorEmbedding(List<Double> vectorEmbedding) { this.vectorEmbedding = vectorEmbedding; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}