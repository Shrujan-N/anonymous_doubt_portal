package com.doubtportal.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_answer")
public class TeacherAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "teacher_answer_id")
    private Long teacherAnswerId;

    @Column(name = "doubt_id", nullable = false)
    private Long doubtId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "answer_text", columnDefinition = "TEXT", nullable = false)
    private String answerText;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public TeacherAnswer() {}

    public Long getTeacherAnswerId() { return teacherAnswerId; }
    public void setTeacherAnswerId(Long teacherAnswerId) { this.teacherAnswerId = teacherAnswerId; }
    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
