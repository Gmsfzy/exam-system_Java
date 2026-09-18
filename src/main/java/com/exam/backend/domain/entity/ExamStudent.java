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
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "exam_student")
@IdClass(ExamStudent.PK.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamStudent {

    @Id
    @Column(name = "exam_id")
    private Long examId;

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    public static class PK implements Serializable {
        private Long examId;
        private Long studentId;
        public PK() {}
        public PK(Long e, Long s) { this.examId = e; this.studentId = s; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK p)) return false;
            return Objects.equals(examId, p.examId) && Objects.equals(studentId, p.studentId);
        }
        @Override public int hashCode() { return Objects.hash(examId, studentId); }
    }
}
