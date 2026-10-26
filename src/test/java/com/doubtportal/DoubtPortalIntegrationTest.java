package com.doubtportal;

import com.doubtportal.model.Doubt;
import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtRepository;
import com.doubtportal.repository.DoubtVectorMongoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DoubtPortalIntegrationTest {
    @Autowired
    private DoubtRepository doubtRepository;

    @Autowired
    private DoubtVectorMongoRepository vectorMongoRepository;

    @Test
    void testMySQLDoubtRepositoryIntegration() {
        Doubt doubt = new Doubt();
        doubt.setStudentId(1L);
        doubt.setSubjectId(1L);
        doubt.setTitle("Integration Test Doubt");
        doubt.setDescription("Testing MySQL Spring Data JPA Binding");
        doubt.setStatus("Pending");

        Doubt saved = doubtRepository.save(doubt);
        assertNotNull(saved.getDoubtId());
        assertEquals("Integration Test Doubt", saved.getTitle());
    }

    @Test
    void testMongoDBVectorRudIntegration() {
        DoubtVectorDocument document = new DoubtVectorDocument(
                99L, "Test Vector Title", List.of(0.1, 0.2, 0.3), List.of("JUnit", "Testing"));

        DoubtVectorDocument saved = vectorMongoRepository.save(document);
        assertNotNull(saved.getId());
        assertFalse(vectorMongoRepository.findByTagsContaining("JUnit").isEmpty());

        vectorMongoRepository.deleteById(saved.getId());
        assertFalse(vectorMongoRepository.existsById(saved.getId()));
    }
}
