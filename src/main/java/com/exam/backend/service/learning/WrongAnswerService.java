package com.exam.backend.service.learning;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.entity.learning.WrongAnswerRecord;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import com.exam.backend.dto.LearningDto;
import com.exam.backend.repository.QuestionRepository;
import com.exam.backend.repository.learning.WrongAnswerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 错题本子模块 (v4.0 自学)：列表 / 掌握 / 删除 + 考试错题收录钩子。
 * 唯一约束 (user_id, question_id)：重复收录时 wrongCount 幂等累加。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WrongAnswerService {

    private final WrongAnswerRecordRepository wrongAnswerRecordRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<LearningDto.WrongRecordView> list(Long userId, String sourceType, Boolean mastered) {
        List<WrongAnswerRecord> records = wrongAnswerRecordRepository.findByUserIdOrderByLastWrongAtDesc(userId).stream()
                .filter(r -> sourceType == null || sourceType.isBlank() || sourceType.equals(r.getSourceType()))
                .filter(r -> mastered == null || mastered.equals(r.getIsMastered()))
                .toList();
        if (records.isEmpty()) return List.of();

        List<Long> qids = records.stream().map(WrongAnswerRecord::getQuestionId).distinct().toList();
        Map<Long, Question> qmap = questionRepository.findAllById(qids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        return records.stream().map(r -> {
            Question q = qmap.get(r.getQuestionId());
            return new LearningDto.WrongRecordView(
                    r.getId(), r.getQuestionId(), r.getSourceType(), r.getSourceId(),
                    r.getWrongAnswer(), r.getCorrectAnswer(),
                    r.getIsMastered(), r.getWrongCount(), r.getLastWrongAt(), r.getMasteredAt(),
                    q == null ? null : q.getContent(),
                    q == null ? null : q.getOptions(),
                    q == null ? null : q.getType(),
                    q == null ? null : q.getDifficulty(),
                    q == null ? null : q.getKnowledge(),
                    // 练习场景已即时反馈过答案，解析只对本人错题下发
                    q == null ? null : q.getAnalysis());
        }).toList();
    }

    @Transactional
    public LearningDto.WrongRecordView markMastered(Long userId, Long id) {
        WrongAnswerRecord record = requireOwned(userId, id);
        if (!Boolean.TRUE.equals(record.getIsMastered())) {
            record.setIsMastered(true);
            record.setMasteredAt(LocalDateTime.now());
            wrongAnswerRecordRepository.save(record);
        }
        return new LearningDto.WrongRecordView(
                record.getId(), record.getQuestionId(), record.getSourceType(), record.getSourceId(),
                record.getWrongAnswer(), record.getCorrectAnswer(),
                record.getIsMastered(), record.getWrongCount(), record.getLastWrongAt(), record.getMasteredAt(),
                null, null, null, null, null, null);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        WrongAnswerRecord record = requireOwned(userId, id);
        wrongAnswerRecordRepository.delete(record);
    }

    /**
     * 收录单条错题（练习/考试/竞赛通用）。
     * 已存在则 wrongCount+1 并刷新最后错误信息；否则新建。
     */
    @Transactional
    public void recordWrong(Long userId, Question question, String wrongAnswer,
                            String sourceType, Long sourceId) {
        if (question == null) return;
        LocalDateTime now = LocalDateTime.now();
        WrongAnswerRecord record = wrongAnswerRecordRepository
                .findByUserIdAndQuestionId(userId, question.getId())
                .orElseGet(() -> WrongAnswerRecord.builder()
                        .userId(userId)
                        .questionId(question.getId())
                        .wrongCount(0)
                        .build());
        record.setSourceType(sourceType);
        record.setSourceId(sourceId);
        record.setWrongAnswer(wrongAnswer);
        record.setCorrectAnswer(question.getAnswer());
        record.setWrongCount((record.getWrongCount() == null ? 0 : record.getWrongCount()) + 1);
        record.setLastWrongAt(now);
        // 再次答错时回退掌握状态
        record.setIsMastered(false);
        record.setMasteredAt(null);
        wrongAnswerRecordRepository.save(record);
    }

    /**
     * 考试提交后错题收录钩子（文档 4.17 业务钩子，AI 批改之后执行）。
     * 客观题 isCorrect=false；主观题得分低于满分即视为错题。
     */
    @Transactional
    public int syncFromExam(Long userId, Long examId,
                            List<com.exam.backend.domain.entity.Answer> answers,
                            Map<Long, Question> questionMap,
                            Map<Long, Double> maxScoreMap) {
        int count = 0;
        for (var a : answers) {
            Question q = questionMap.get(a.getQuestionId());
            if (q == null) continue;
            boolean wrong;
            if (QuestionTypeEnum.isSubjective(q.getType())) {
                double max = maxScoreMap.getOrDefault(q.getId(), 0.0);
                Double effective = a.effectiveScore();
                // 未作答（无有效得分）同样视为错题
                wrong = max > 0 && (effective == null || effective < max);
            } else {
                wrong = Boolean.FALSE.equals(a.getIsCorrect());
            }
            if (wrong) {
                recordWrong(userId, q, a.getStudentAnswer(), "exam", examId);
                count++;
            }
        }
        log.info("syncFromExam: user={} exam={} wrongRecorded={}", userId, examId, count);
        return count;
    }

    private WrongAnswerRecord requireOwned(Long userId, Long id) {
        WrongAnswerRecord record = wrongAnswerRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "错题记录不存在"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该错题记录");
        }
        return record;
    }
}
