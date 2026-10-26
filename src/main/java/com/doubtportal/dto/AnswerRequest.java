package com.doubtportal.dto;

public class AnswerRequest {
    private Long doubtId;
    private Long userId;
    private String answerText;
    private Boolean isTeacher;

    public AnswerRequest() {}

    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public Boolean getIsTeacher() { return isTeacher; }
    public void setIsTeacher(Boolean teacher) { isTeacher = teacher; }
}
