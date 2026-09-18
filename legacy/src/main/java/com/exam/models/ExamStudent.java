package com.exam.models;

import java.time.LocalDateTime;

public class ExamStudent {
    private int examId;
    private int studentId;
    private LocalDateTime invitedAt;

    private String studentName;

    public ExamStudent() {
    }

    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime invitedAt) { this.invitedAt = invitedAt; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
}