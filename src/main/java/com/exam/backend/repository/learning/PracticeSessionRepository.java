package com.exam.backend.repository.learning;

import com.exam.backend.domain.entity.learning.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    List<PracticeSession> findByUserIdOrderByStartTimeDesc(Long userId);

    List<PracticeSession> findByUserIdAndStatusOrderByStartTimeDesc(Long userId, String status);
}
