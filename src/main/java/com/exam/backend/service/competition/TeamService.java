package com.exam.backend.service.competition;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.Team;
import com.exam.backend.domain.entity.competition.TeamMember;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.TeamMemberRepository;
import com.exam.backend.repository.competition.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 战队服务 (v4.0)：一人一队，创建即队长并获「开疆辟土」勋章；队长退出自动转让或解散。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Transactional(readOnly = true)
    public List<CompetitionDto.TeamView> list() {
        return teamRepository.findAllByOrderByIdDesc().stream().map(t -> toView(t, null)).toList();
    }

    @Transactional
    public CompetitionDto.TeamView create(Long userId, CompetitionDto.TeamRequest req) {
        if (!memberRepository.findByUserId(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "你已在一个战队中，请先退出");
        }
        if (teamRepository.findByName(req.name().trim()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "战队名称已存在");
        }
        Team team = teamRepository.save(Team.builder()
                .name(req.name().trim()).description(req.description()).captainId(userId).build());
        memberRepository.save(TeamMember.builder()
                .teamId(team.getId()).userId(userId).role("captain").joinedAt(LocalDateTime.now()).build());
        gamificationService.grantBadge(userId, GamificationService.BADGE_TEAM_FOUNDER,
                gamificationService.currentSeason(), team.getId());
        return toView(team, userId);
    }

    @Transactional(readOnly = true)
    public CompetitionDto.TeamView mine(Long userId) {
        return memberRepository.findByUserId(userId).stream().findFirst()
                .map(m -> toView(requireTeam(m.getTeamId()), userId))
                .orElse(null);
    }

    @Transactional
    public CompetitionDto.TeamView join(Long teamId, Long userId) {
        requireTeam(teamId);
        if (!memberRepository.findByUserId(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "你已在一个战队中，请先退出");
        }
        if (memberRepository.findByTeamIdAndUserId(teamId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "你已在该战队");
        }
        memberRepository.save(TeamMember.builder()
                .teamId(teamId).userId(userId).role("member").joinedAt(LocalDateTime.now()).build());
        return toView(requireTeam(teamId), userId);
    }

    @Transactional
    public void leave(Long teamId, Long userId) {
        Team team = requireTeam(teamId);
        TeamMember me = memberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "你不在该战队"));
        memberRepository.delete(me);
        if (userId.equals(team.getCaptainId())) {
            List<TeamMember> remain = memberRepository.findByTeamIdOrderByIdAsc(teamId);
            if (remain.isEmpty()) {
                teamRepository.delete(team); // 队长退出且无人 → 解散
            } else {
                TeamMember next = remain.get(0);
                next.setRole("captain");
                memberRepository.save(next);
                team.setCaptainId(next.getUserId());
                teamRepository.save(team);
            }
        }
    }

    @Transactional
    public CompetitionDto.TeamView transfer(Long teamId, Long operatorId, CompetitionDto.TransferRequest req) {
        Team team = requireTeam(teamId);
        requireCaptain(team, operatorId);
        if (operatorId.equals(req.userId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "队长不能转让给自己");
        }
        TeamMember target = memberRepository.findByTeamIdAndUserId(teamId, req.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "目标用户不在该战队"));
        memberRepository.findByTeamIdAndUserId(teamId, operatorId).ifPresent(c -> {
            c.setRole("member");
            memberRepository.save(c);
        });
        target.setRole("captain");
        memberRepository.save(target);
        team.setCaptainId(req.userId());
        return toView(teamRepository.save(team), operatorId);
    }

    @Transactional
    public void disband(Long teamId, Long operatorId) {
        Team team = requireTeam(teamId);
        requireCaptain(team, operatorId);
        memberRepository.deleteByTeamId(teamId);
        teamRepository.delete(team);
    }

    // ==================== 内部工具 ====================

    private Team requireTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "战队不存在"));
    }

    private void requireCaptain(Team team, Long userId) {
        if (!team.getCaptainId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅队长可执行该操作");
        }
    }

    private CompetitionDto.TeamView toView(Team team, Long currentUserId) {
        List<TeamMember> members = memberRepository.findByTeamIdOrderByIdAsc(team.getId());
        Set<Long> ids = members.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        ids.add(team.getCaptainId());
        Map<Long, String> names = namesFor(ids);
        boolean mine = currentUserId != null && members.stream().anyMatch(m -> m.getUserId().equals(currentUserId));
        List<CompetitionDto.TeamMemberView> mv = members.stream().map(m -> new CompetitionDto.TeamMemberView(
                m.getUserId(), names.getOrDefault(m.getUserId(), "user" + m.getUserId()),
                m.getRole(), m.getJoinedAt())).toList();
        return new CompetitionDto.TeamView(team.getId(), team.getName(), team.getDescription(),
                team.getCaptainId(), names.getOrDefault(team.getCaptainId(), "user" + team.getCaptainId()),
                members.size(), team.getCreatedAt(), mv, mine);
    }

    private Map<Long, String> namesFor(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (User u : userRepository.findAllById(ids)) map.put(u.getId(), u.getUsername());
        return map;
    }

    public Optional<Team> findTeam(Long id) {
        return teamRepository.findById(id);
    }
}
