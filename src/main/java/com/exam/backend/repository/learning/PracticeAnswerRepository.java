package com.exam.backend.repository.learning;

import com.exam.backend.domain.entity.learning.PracticeAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, Long> {

    List<PracticeAnswer> findBySessionIdOrderByIdAsc(Long sessionId);

    Optional<PracticeAnswer> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    void deleteBySessionId(Long sessionId);
}
