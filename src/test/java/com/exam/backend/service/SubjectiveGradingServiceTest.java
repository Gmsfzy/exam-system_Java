package com.exam.backend.service;

import com.exam.backend.domain.entity.Answer;
import com.exam.backend.domain.entity.ExamQuestion;
import com.exam.backend.domain.entity.ExamSession;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.Result;
import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.AiDto;
import com.exam.backend.repository.AnswerRepository;
import com.exam.backend.repository.ExamQuestionRepository;
import com.exam.backend.repository.ExamRepository;
import com.exam.backend.repository.ExamSessionRepository;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.ResultRepository;
import com.exam.backend.service.competition.RealtimePushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 主观题异步判分编排 {@link SubjectiveGradingService#gradeSession} 的单元回归。
 * 覆盖两条关键路径：AI 成功→GRADED 并重算成绩；AI 异常→FAILED 转人工，均不阻断收尾。
 */
@ExtendWith(MockitoExtension.class)
class SubjectiveGradingServiceTest {

    @Mock private AnswerRepository answerRepository;
    @Mock private ExamSessionRepository examSessionRepository;
    @Mock private ExamQuestionRepository examQuestionRepository;
    @Mock private ExamRepository examRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private ResultRepository resultRepository;
    @Mock private AiService aiService;
    @Mock private NotificationService notificationService;
    @Mock private RealtimePushService pushService;

    @InjectMocks private SubjectiveGradingService service;

    private static final long SESSION_ID = 1L;
    private static final long EXAM_ID = 10L;
    private static final long STUDENT_ID = 20L;
    private static final long QUESTION_ID = 100L;

    private ExamSession session() {
        return ExamSession.builder().examId(EXAM_ID).studentId(STUDENT_ID).build();
    }

    private Answer pendingAnswer() {
        Answer a = Answer.builder()
                .sessionId(SESSION_ID)
                .questionId(QUESTION_ID)
                .studentAnswer("我的主观题作答内容")
                .needsManualGrade(false)
                .build();
        a.setGradeStatus(Answer.PENDING_AI);
        return a;
    }

    private Question subjectiveQuestion() {
        Question q = Question.builder()
                .content("请简述快速排序思想")
                .type(QuestionTypeEnum.short_answer)
                .difficulty(DifficultyEnum.medium)
                .answer("分治")
                .build();
        q.setId(QUESTION_ID);
        return q;
    }

    private ExamQuestion examQuestion() {
        return ExamQuestion.builder()
                .examId(EXAM_ID).questionId(QUESTION_ID).score(10).order(1).build();
    }

    private Result result() {
        return Result.builder().examId(EXAM_ID).studentId(STUDENT_ID).sessionId(SESSION_ID)
                .totalScore(10.0).build();
    }

    private void stubCommon(Answer answer) {
        when(examSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(answerRepository.claimForGrading(eq(SESSION_ID), any(), any())).thenReturn(1);
        when(answerRepository.findBySessionIdAndGradeStatus(SESSION_ID, Answer.GRADING))
                .thenReturn(List.of(answer));
        when(examQuestionRepository.findByExamIdOrderByOrderAsc(EXAM_ID))
                .thenReturn(List.of(examQuestion()));
        when(questionRepository.findAllById(any())).thenReturn(List.of(subjectiveQuestion()));
        when(answerRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(answer));
        when(answerRepository.countUngraded(SESSION_ID)).thenReturn(0L);
        // M6 发布门控：未发布时改发“评分完成”通知，examRepository 必须可答
        lenient().when(examRepository.findById(EXAM_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("AI 判分成功：置 GRADED、写入 AI 分数、重算成绩并结束评分态")
    void gradeSession_aiSuccess_marksGraded() {
        Answer answer = pendingAnswer();
        Result result = result();
        stubCommon(answer);
        when(resultRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(result));
        when(aiService.gradeSubjective(any(Question.class), any(Answer.class), anyDouble()))
                .thenReturn(new AiDto.AiGradeResponse(8.0, "思路清晰"));

        service.gradeSession(SESSION_ID);

        assertThat(answer.getGradeStatus()).isEqualTo(Answer.GRADED);
        assertThat(answer.getAiScore()).isEqualTo(8.0);
        assertThat(answer.getNeedsManualGrade()).isFalse();
        assertThat(result.getScore()).isEqualTo(8.0);
        assertThat(result.getGrading()).isFalse();
    }

    @Test
    @DisplayName("AI 判分异常：置 FAILED 并转人工，成绩收尾不抛异常")
    void gradeSession_aiFailure_marksFailedForManual() {
        Answer answer = pendingAnswer();
        Result result = result();
        stubCommon(answer);
        when(resultRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(result));
        when(aiService.gradeSubjective(any(Question.class), any(Answer.class), anyDouble()))
                .thenThrow(new RuntimeException("AI 服务超时"));

        service.gradeSession(SESSION_ID);

        assertThat(answer.getGradeStatus()).isEqualTo(Answer.FAILED);
        assertThat(answer.getNeedsManualGrade()).isTrue();
        assertThat(result.getGrading()).isFalse();
    }

    @Test
    @DisplayName("无待判题（CAS 抢占 0 行）时直接返回，不调用 AI")
    void gradeSession_noClaim_noOp() {
        when(examSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(answerRepository.claimForGrading(eq(SESSION_ID), any(), any())).thenReturn(0);

        service.gradeSession(SESSION_ID);

        // 未抢占即返回：不应发生成绩收尾查询
        org.mockito.Mockito.verify(resultRepository, org.mockito.Mockito.never())
                .findBySessionId(any());
    }
}
