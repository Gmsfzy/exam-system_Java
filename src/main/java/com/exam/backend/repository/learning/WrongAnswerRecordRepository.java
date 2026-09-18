package com.exam.backend.repository.learning;

import com.exam.backend.domain.entity.learning.WrongAnswerRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WrongAnswerRecordRepository extends JpaRepository<WrongAnswerRecord, Long> {

    List<WrongAnswerRecord> findByUserIdOrderByLastWrongAtDesc(Long userId);

    Optional<WrongAnswerRecord> findByUserIdAndQuestionId(Long userId, Long questionId);

    long countByUserIdAndIsMasteredFalse(Long userId);
}
