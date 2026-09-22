package com.exam.backend.repository;

import com.exam.backend.domain.entity.Result;
import com.exam.backend.domain.enums.ScoreStrategyEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByStudentId(Long studentId);
    List<Result> findByExamId(Long examId);

    /** M6 多轮次：同一考试+学生的全部轮次成绩（按创建顺序） */
    List<Result> findByExamIdAndStudentIdOrderByIdAsc(Long examId, Long studentId);

    List<Result> findBySessionId(Long sessionId);

    /** 取「有效成绩」：best 策略取最高分轮次（同分取最新），last 策略取最新轮次 */
    default Optional<Result> findEffective(Long examId, Long studentId, ScoreStrategyEnum strategy) {
        List<Result> all = findByExamIdAndStudentIdOrderByIdAsc(examId, studentId);
        if (all.isEmpty()) return Optional.empty();
        if (strategy == ScoreStrategyEnum.best) {
            return all.stream().max(Comparator.comparingDouble(
                    r -> r.getScore() == null ? 0.0 : r.getScore()));
        }
        return Optional.of(all.get(all.size() - 1));
    }
}
