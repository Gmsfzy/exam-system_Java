package com.exam.backend.service.competition;

import com.exam.backend.domain.entity.User;
import com.exam.backend.domain.entity.competition.CompetitionAnswer;
import com.exam.backend.domain.entity.competition.CompetitionParticipant;
import com.exam.backend.domain.entity.competition.UserPointsProfile;
import com.exam.backend.dto.CompetitionDto;
import com.exam.backend.repository.UserRepository;
import com.exam.backend.repository.competition.CompetitionAnswerRepository;
import com.exam.backend.repository.competition.CompetitionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情画像 (v4.0)：五维（准确率 / 速度 / 竞技力 / 稳定度 / 活跃度），均由竞赛域数据实时推导，量纲 0-100。
 */
@Service
@RequiredArgsConstructor
public class StudyProfileService {

    private static final double SPEED_TARGET_SEC = 60.0;

    private final CompetitionParticipantRepository participantRepository;
    private final CompetitionAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Transactional(readOnly = true)
    public CompetitionDto.StudyProfileView build(Long userId) {
        List<CompetitionParticipant> finished = participantRepository.findByUserId(userId).stream()
                .filter(p -> "finished".equals(p.getStatus())).toList();

        long totalAnswered = 0;
        long totalCorrect = 0;
        long totalTimeSpent = 0;
        List<Double> perCompAccuracy = new ArrayList<>();
        for (CompetitionParticipant p : finished) {
            List<CompetitionAnswer> answers = answerRepository.findByParticipantId(p.getId());
            long correct = answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
            totalAnswered += answers.size();
            totalCorrect += correct;
            totalTimeSpent += answers.stream().mapToInt(CompetitionAnswer::getTimeSpent).sum();
            if (!answers.isEmpty()) perCompAccuracy.add((double) correct / answers.size());
        }

        String season = gamificationService.currentSeason();
        UserPointsProfile prof = gamificationService.getProfile(userId);
        int pkTotal = prof.getPkWin() + prof.getPkLose() + prof.getPkDraw();

        double accuracy = totalAnswered > 0 ? (double) totalCorrect / totalAnswered : 0.0;
        double avgTime = totalAnswered > 0 ? (double) totalTimeSpent / totalAnswered : SPEED_TARGET_SEC;
        double speed = clamp01((SPEED_TARGET_SEC - avgTime) / SPEED_TARGET_SEC);
        double competitiveness = clamp01(0.6 * Math.min(1.0, prof.getPoints() / 1000.0)
                + 0.4 * (pkTotal > 0 ? (double) prof.getPkWin() / pkTotal : 0.0));
        double stability = stability(perCompAccuracy);
        double activity = clamp01((finished.size() * 8.0 + pkTotal * 5.0 + prof.getMaxStreak() * 3.0) / 100.0);

        List<CompetitionDto.ProfileDimension> dims = new ArrayList<>();
        dims.add(dim("accuracy", "准确率", accuracy));
        dims.add(dim("speed", "速度", speed));
        dims.add(dim("competitiveness", "竞技力", competitiveness));
        dims.add(dim("stability", "稳定度", stability));
        dims.add(dim("activity", "活跃度", activity));
        double overall = dims.stream().mapToDouble(CompetitionDto.ProfileDimension::value).average().orElse(0);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("finishedCount", finished.size());
        raw.put("totalAnswered", totalAnswered);
        raw.put("totalCorrect", totalCorrect);
        raw.put("avgTimeSpent", round1(avgTime));
        raw.put("pkWin", prof.getPkWin());
        raw.put("pkLose", prof.getPkLose());
        raw.put("pkDraw", prof.getPkDraw());
        raw.put("streak", prof.getStreak());
        raw.put("maxStreak", prof.getMaxStreak());
        raw.put("timedFinished", prof.getTimedFinished());

        String name = userRepository.findById(userId).map(User::getUsername).orElse("user" + userId);
        return new CompetitionDto.StudyProfileView(userId, name, season, prof.getPoints(),
                gamificationService.tierOf(prof.getPoints()).name(), round1(overall), dims, raw);
    }

    /** 稳定度：各场准确率的变异系数越小越高；不足 2 场按准确率本身给分 */
    private double stability(List<Double> accs) {
        if (accs.isEmpty()) return 0.0;
        if (accs.size() == 1) return accs.get(0);
        double mean = accs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = accs.stream().mapToDouble(a -> (a - mean) * (a - mean)).average().orElse(0);
        double std = Math.sqrt(var);
        return clamp01(mean * (1.0 - Math.min(1.0, std * 2)));
    }

    private CompetitionDto.ProfileDimension dim(String key, String label, double ratio) {
        return new CompetitionDto.ProfileDimension(key, label, round1(clamp01(ratio) * 100.0), 100.0);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
