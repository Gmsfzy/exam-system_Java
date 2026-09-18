package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByOrderByIdDesc();

    Optional<Team> findByName(String name);
}
