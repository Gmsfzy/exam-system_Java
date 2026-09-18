package com.exam.backend.domain.entity;

import com.exam.backend.domain.enums.SessionStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_session")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamSession extends BaseEntity {

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private SessionStatusEnum status = SessionStatusEnum.in_progress;

    @Column(name = "switch_count", nullable = false)
    @Builder.Default
    private Integer switchCount = 0;
}
