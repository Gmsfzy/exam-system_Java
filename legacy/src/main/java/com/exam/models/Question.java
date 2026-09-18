package com.exam.models;

import com.exam.enums.DifficultyEnum;
import com.exam.enums.QuestionTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

public class Question {
    private int id;
    private String content;
    private List<String> options;
    private String answer;
    private String analysis;
    private int majorId;
    private QuestionTypeEnum type;
    private DifficultyEnum difficulty;
    private LocalDateTime createdAt;

    private String majorName;

    public Question() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public int getMajorId() { return majorId; }
    public void setMajorId(int majorId) { this.majorId = majorId; }
    public QuestionTypeEnum getType() { return type; }
    public void setType(QuestionTypeEnum type) { this.type = type; }
    public DifficultyEnum getDifficulty() { return difficulty; }
    public void setDifficulty(DifficultyEnum difficulty) { this.difficulty = difficulty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
}