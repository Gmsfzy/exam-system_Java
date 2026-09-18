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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 自主学习全链路：刷题闭环 → 错题收录 → 计划推进 → 学习报告。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LearningApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = registerAndLogin("learn_teacher", "teacher");
        studentToken = registerAndLogin("learn_student", "student");
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

    /** 教师创建并发布一道单选题 */
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
    @DisplayName("刷题闭环：start 不含答案 → 答错进错题本 → submit 聚合 → history/报告可见")
    void practiceLoopAndWrongNotebook() throws Exception {
        createPublicSingleChoice("测试水果题 A", "A");
        createPublicSingleChoice("测试水果题 B", "B");

        // 开始练习
        MvcResult started = mockMvc.perform(post("/api/learning/practice/start")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":10,\"title\":\"集成测试练习\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.questions").isArray())
                .andReturn();
        JsonNode startData = om.readTree(started.getResponse().getContentAsString()).path("data");
        long sessionId = startData.path("sessionId").asLong();
        JsonNode questions = startData.path("questions");
        long firstQid = questions.get(0).path("questionId").asLong();
        // 抽题视图不下发答案
        mockMvc.perform(get("/api/learning/practice/" + sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist());

        // 答错第一题（正确答案是 A/B，故意选 C）→ 即时反馈 + 错题收录
        mockMvc.perform(post("/api/learning/practice/" + sessionId + "/answer")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + firstQid + ",\"studentAnswer\":\"C\",\"timeSpentSec\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isCorrect").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").isString());

        JsonNode wrongs = om.readTree(mockMvc.perform(get("/api/learning/wrong-records")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(wrongs.size()).isEqualTo(1);
        assertThat(wrongs.get(0).path("questionId").asLong()).isEqualTo(firstQid);
        assertThat(wrongs.get(0).path("sourceType").asText()).isEqualTo("practice");

        // 再答错一次同一题：wrongCount 幂等累加为 2
        mockMvc.perform(post("/api/learning/practice/" + sessionId + "/answer")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + firstQid + ",\"studentAnswer\":\"D\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/learning/wrong-records")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.data[0].wrongCount").value(2));

        // 提交整套练习
        mockMvc.perform(post("/api/learning/practice/" + sessionId + "/submit")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionsCount").value(questions.size()))
                .andExpect(jsonPath("$.data.correctCount").value(0));

        // 重复提交 → 400
        mockMvc.perform(post("/api/learning/practice/" + sessionId + "/submit")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());

        // 历史与报告
        mockMvc.perform(get("/api/learning/practice/history")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.data[0].status").value("completed"));
        mockMvc.perform(get("/api/learning/report/overview")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.data.totalQuestions").value(questions.size()))
                .andExpect(jsonPath("$.data.wrongPendingCount").value(1));
        mockMvc.perform(get("/api/learning/report/daily").param("days", "7")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.data.length()").value(7));

        // 标记掌握 → 待攻克清零；删除记录
        long recordId = om.readTree(mockMvc.perform(get("/api/learning/wrong-records")
                        .header("Authorization", "Bearer " + studentToken))
                .andReturn().getResponse().getContentAsString()).path("data").get(0).path("id").asLong();
        mockMvc.perform(post("/api/learning/wrong-records/" + recordId + "/master")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.data.isMastered").value(true));
        mockMvc.perform(delete("/api/learning/wrong-records/" + recordId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学习计划：创建 → 练习完成自动推进 → 达标 completed → 暂停受限")
    void planProgress() throws Exception {
        createPublicSingleChoice("计划关联题", "A");

        MvcResult planRes = mockMvc.perform(post("/api/learning/plans")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"攻克 1 题\",\"targetCount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"))
                .andReturn();
        long planId = om.readTree(planRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 一题练习并答对提交
        MvcResult started = mockMvc.perform(post("/api/learning/practice/start")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = om.readTree(started.getResponse().getContentAsString()).path("data");
        long sessionId = data.path("sessionId").asLong();
        JsonNode q0 = data.path("questions").get(0);
        // 不依赖抽题顺序：用单题作答返回的即时反馈中的正确答案再答一次，确保判对
        MvcResult ansRes = mockMvc.perform(post("/api/learning/practice/" + sessionId + "/answer")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + q0.path("questionId").asLong() + ",\"studentAnswer\":\"A\"}"))
                .andExpect(status().isOk()).andReturn();
        String correctAnswer = om.readTree(ansRes.getResponse().getContentAsString())
                .path("data").path("correctAnswer").asText();
        if (!"A".equals(correctAnswer)) {
            mockMvc.perform(post("/api/learning/practice/" + sessionId + "/answer")
                            .with(csrf())
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":" + q0.path("questionId").asLong()
                                    + ",\"studentAnswer\":\"" + correctAnswer + "\"}"))
                    .andExpect(jsonPath("$.data.isCorrect").value(true));
        }
        mockMvc.perform(post("/api/learning/practice/" + sessionId + "/submit")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // 计划应自动完成
        mockMvc.perform(get("/api/learning/plans/" + planId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.data.completedCount").value(1))
                .andExpect(jsonPath("$.data.status").value("completed"));

        // 已完成计划不可暂停
        mockMvc.perform(post("/api/learning/plans/" + planId + "/pause")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/learning/plans/" + planId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("归属与认证：不能操作他人会话/记录，未登录 401")
    void ownershipEnforced() throws Exception {
        createPublicSingleChoice("归属测试题", "A");
        MvcResult started = mockMvc.perform(post("/api/learning/practice/start")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isOk()).andReturn();
        long sessionId = om.readTree(started.getResponse().getContentAsString())
                .path("data").path("sessionId").asLong();

        String other = registerAndLogin("learn_other", "student");
        mockMvc.perform(get("/api/learning/practice/" + sessionId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/learning/practice/" + sessionId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/learning/practice/history"))
                .andExpect(status().isUnauthorized());
    }
}
