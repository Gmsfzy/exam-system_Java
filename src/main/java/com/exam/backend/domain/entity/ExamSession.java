package com.exam.backend.domain.entity;

import com.exam.backend.domain.converter.JsonLongListConverter;
import com.exam.backend.domain.converter.JsonMapConverter;
import com.exam.backend.domain.enums.SessionStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;
import java.util.Map;

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

    // ==== M6 多轮次 / 个人卷字段 ====

    /** 第几轮作答，从 1 开始 */
    @Column(name = "attempt_no")
    @Builder.Default
    private Integer attemptNo = 1;

    /** 本轮交卷后的会话得分 */
    @Column
    private Double score;

    /** random 组卷模式下固化的个人卷题序；空列表=按试卷全量出题 */
    @Convert(converter = JsonLongListConverter.class)
    @Column(name = "assigned_question_ids", columnDefinition = "TEXT")
    private List<Long> assignedQuestionIds;

    /** 选项乱序映射：questionId(字符串) -> {字母序: 原字母序}；空=未乱序 */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "option_map", columnDefinition = "TEXT")
    private Map<String, Object> optionMap;

    public int attemptNoEffective() {
        return attemptNo == null || attemptNo < 1 ? 1 : attemptNo;
    }
}
