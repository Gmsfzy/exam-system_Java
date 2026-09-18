package com.exam.backend.domain.entity;

import com.exam.backend.domain.enums.ExamStatusEnum;
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
@Table(name = "exam")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Exam extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column
    private Integer duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ExamStatusEnum status = ExamStatusEnum.draft;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "invitation_code", length = 64)
    private String invitationCode;

    @Column(name = "invitation_url", length = 256)
    private String invitationUrl;
}
