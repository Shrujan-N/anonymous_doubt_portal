package com.doubtportal.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "answer")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "doubt_id", nullable = false)
    private Long doubtId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "answer_text", columnDefinition = "TEXT", nullable = false)
    private String answerText;

    private Integer upvotes = 0;

    @Column(name = "is_helpful")
    private Boolean isHelpful = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Answer() {}

    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public Integer getUpvotes() { return upvotes; }
    public void setUpvotes(Integer upvotes) { this.upvotes = upvotes; }
    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean helpful) { isHelpful = helpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
