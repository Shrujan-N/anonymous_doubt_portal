package com.doubtportal.repository;

import com.doubtportal.model.DoubtVectorDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DoubtVectorMongoRepository extends MongoRepository<DoubtVectorDocument, String> {
    List<DoubtVectorDocument> findByTagsContaining(String tag);
}