package com.exam.backend.repository.learning;

import com.exam.backend.domain.entity.learning.StudyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {

    List<StudyLog> findByUserIdOrderByStudiedAtDesc(Long userId);

    List<StudyLog> findByUserIdAndStudiedAtGreaterThanEqualOrderByStudiedAtDesc(
            Long userId, LocalDateTime from);
}
