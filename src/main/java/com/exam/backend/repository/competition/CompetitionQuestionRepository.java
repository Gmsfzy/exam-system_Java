package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.CompetitionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionQuestionRepository extends JpaRepository<CompetitionQuestion, Long> {

    List<CompetitionQuestion> findByCompetitionIdOrderByOrderAsc(Long competitionId);

    Optional<CompetitionQuestion> findByIdAndCompetitionId(Long id, Long competitionId);

    long countByCompetitionId(Long competitionId);

    void deleteByCompetitionId(Long competitionId);
}
