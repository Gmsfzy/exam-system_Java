package com.exam.models;

import com.exam.enums.SessionStatusEnum;

import java.time.LocalDateTime;

public class ExamSession {
    private int id;
    private int examId;
    private int studentId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SessionStatusEnum status;
    private int switchCount;

    public ExamSession() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public SessionStatusEnum getStatus() { return status; }
    public void setStatus(SessionStatusEnum status) { this.status = status; }
    public int getSwitchCount() { return switchCount; }
    public void setSwitchCount(int switchCount) { this.switchCount = switchCount; }

    public void incrementSwitchCount() {
        this.switchCount++;
    }
}