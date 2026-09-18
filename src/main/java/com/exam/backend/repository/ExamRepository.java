package com.exam.backend.repository;

import com.exam.backend.domain.entity.Exam;
import com.exam.backend.domain.enums.ExamStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByCreatorId(Long creatorId);
    Optional<Exam> findByInvitationCode(String code);
    List<Exam> findByStatusAndEndTimeBefore(ExamStatusEnum status, LocalDateTime time);
}
