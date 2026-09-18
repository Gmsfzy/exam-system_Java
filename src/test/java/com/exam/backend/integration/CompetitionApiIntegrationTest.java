package com.exam.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 限时积分赛全链路：教师建赛→选题→发布 → 学生报名→开始→作答→交卷 → 榜单可见。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CompetitionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = registerAndLogin("comp_teacher", "teacher");
        studentToken = registerAndLogin("comp_student", "student");
    }

    private String registerAndLogin(String username, String role) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"123123\",\"role\":\"" + role + "\"}";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private long createPublicSingleChoice(String content, String answer) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/questions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s","options":["Apple","Banana","Cherry","Durian"],
                                 "answer":"%s","analysis":"解析","knowledge":"水果","type":"single_choice","difficulty":"easy"}
                                """.formatted(content, answer)))
                .andExpect(status().isOk())
                .andReturn();
        long id = om.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(post("/api/questions/" + id + "/toggle_public")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    @DisplayName("限时赛闭环：建赛→发布→报名→答题→交卷→榜单")
    void timedCompetitionFullLoop() throws Exception {
        long qid = createPublicSingleChoice("竞赛水果题", "A");

        // 教师创建竞赛（时间窗覆盖当前，全量模式 drawCount=0）
        String start = LocalDateTime.now().minusMinutes(5).format(FMT);
        String end = LocalDateTime.now().plusHours(1).format(FMT);
        MvcResult compRes = mockMvc.perform(post("/api/competitions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"水果竞速赛","description":"限时答题","startTime":"%s","endTime":"%s",
                                 "duration":15,"drawCount":0,"allowPk":false,"scoringRule":{"base_ratio":0.7,"speed_ratio":0.3}}
                                """.formatted(start, end)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();
        long compId = om.readTree(compRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 选题
        mockMvc.perform(post("/api/competitions/" + compId + "/questions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[" + qid + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.added").value(1));

        // 发布
        mockMvc.perform(post("/api/competitions/" + compId + "/publish")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.totalScore").value(10.0));

        // 学生报名
        mockMvc.perform(post("/api/competitions/" + compId + "/join")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("joined"));

        // 开始答题（触发 published→ongoing）
        MvcResult started = mockMvc.perform(post("/api/competitions/" + compId + "/start")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions").isArray())
                .andReturn();
        JsonNode startData = om.readTree(started.getResponse().getContentAsString()).path("data");
        long cqId = startData.path("questions").get(0).path("cqId").asLong();
        // 抽题视图不下发答案
        assertThat(startData.path("questions").get(0).has("answer")).isFalse();

        // 答对
        mockMvc.perform(post("/api/competitions/" + compId + "/answer")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cqId\":" + cqId + ",\"answer\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isCorrect").value(true))
                .andExpect(jsonPath("$.data.correctAnswer").value("A"));

        // 重复作答被拒
        mockMvc.perform(post("/api/competitions/" + compId + "/answer")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cqId\":" + cqId + ",\"answer\":\"A\"}"))
                .andExpect(status().isBadRequest());

        // 交卷
        mockMvc.perform(post("/api/competitions/" + compId + "/finish")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").isNumber());

        // 榜单可见
        mockMvc.perform(get("/api/competitions/" + compId + "/leaderboard")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].username").value("comp_student"))
                .andExpect(jsonPath("$.data.myRank").value(1));
    }

    @Test
    @DisplayName("学生禁止访问教师管理端（403）")
    void studentCannotManage() throws Exception {
        mockMvc.perform(post("/api/competitions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"学生建赛\",\"duration\":15}"))
                .andExpect(status().isForbidden());
    }
}
