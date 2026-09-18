package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamIdOrderByIdAsc(Long teamId);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    List<TeamMember> findByUserId(Long userId);

    long countByTeamId(Long teamId);

    @Modifying
    @Query("DELETE FROM TeamMember m WHERE m.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);
}
