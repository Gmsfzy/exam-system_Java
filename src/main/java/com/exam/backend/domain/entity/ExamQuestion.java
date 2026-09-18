package com.exam.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "exam_question")
@IdClass(ExamQuestion.PK.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamQuestion {

    @Id
    @Column(name = "exam_id")
    private Long examId;

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @Column
    private Integer score;

    @Column(name = "\"order\"")
    private Integer order;

    public static class PK implements Serializable {
        private Long examId;
        private Long questionId;
        public PK() {}
        public PK(Long e, Long q) { this.examId = e; this.questionId = q; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK p)) return false;
            return Objects.equals(examId, p.examId) && Objects.equals(questionId, p.questionId);
        }
        @Override public int hashCode() { return Objects.hash(examId, questionId); }
    }
}
