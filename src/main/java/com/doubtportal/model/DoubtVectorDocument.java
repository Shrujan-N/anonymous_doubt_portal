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

    public String getId() { return id; }
    public Long getDoubtId() { return doubtId; }
    public String getTitle() { return title; }
    public List<Double> getVectorEmbedding() { return vectorEmbedding; }
    public List<String> getTags() { return tags; }
}