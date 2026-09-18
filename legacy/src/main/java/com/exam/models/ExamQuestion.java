package com.exam.models;

public class ExamQuestion {
    private int examId;
    private int questionId;
    private double score;
    private int order;

    private String questionContent;
    private String questionType;

    public ExamQuestion() {
    }

    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }
    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
}