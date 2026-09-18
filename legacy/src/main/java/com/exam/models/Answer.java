package com.exam.models;

public class Answer {
    private int id;
    private int sessionId;
    private int questionId;
    private String studentAnswer;
    private Boolean isCorrect;
    private Double score;
    private Double manualScore;
    private String manualComment;
    private boolean needsManualGrade;
    private Double aiScore;
    private String aiAnalysis;

    private String questionContent;
    private String correctAnswer;
    private String questionType;
    private double questionScore;

    public Answer() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }
    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Double getManualScore() { return manualScore; }
    public void setManualScore(Double manualScore) { this.manualScore = manualScore; }
    public String getManualComment() { return manualComment; }
    public void setManualComment(String manualComment) { this.manualComment = manualComment; }
    public boolean isNeedsManualGrade() { return needsManualGrade; }
    public void setNeedsManualGrade(boolean needsManualGrade) { this.needsManualGrade = needsManualGrade; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public double getQuestionScore() { return questionScore; }
    public void setQuestionScore(double questionScore) { this.questionScore = questionScore; }

    public double getEffectiveScore() {
        if (manualScore != null) {
            return manualScore;
        }
        return score != null ? score : 0.0;
    }

    public boolean isManualGraded() {
        return manualScore != null;
    }

    public Double getAiScore() { return aiScore; }
    public void setAiScore(Double aiScore) { this.aiScore = aiScore; }
    public String getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }
}