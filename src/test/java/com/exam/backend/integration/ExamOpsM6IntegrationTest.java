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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 考务升级全链集成测试：
 * 发布(maxAttempts=2/best/不自动发布) → 两轮交卷(A/B 卷得分不同) → 门控 → publish_results
 * → monitor / item_analysis / export → 申诉 → 教师改分 approve → 总分重算。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExamOpsM6IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    private String teacherToken;
    private String studentToken;
    private long studentId;
    private long examId;
    private long resultId;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = registerAndLogin("m6_teacher", "teacher");
        studentToken = registerAndLogin("m6_student", "student");
        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn();
        studentId = om.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 两道单选：q1 答案 A，q2 答案 B（默认每题 10 分）
        long q1 = createQuestion("M6-1+1=?", "A.2 B.3 C.4 D.5", "A");
        long q2 = createQuestion("M6-2*2=?", "A.3 B.4 C.5 D.6", "B");

        MvcResult exam = mockMvc.perform(post("/api/exams")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"M6考务集成考试","duration":60,
                                 "startTime":"2020-01-01 00:00:00","endTime":"2099-12-31 23:59:59",
                                 "maxAttempts":2,"scoreStrategy":"best","resultsPublished":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAttempts").value(2))
                .andExpect(jsonPath("$.data.scoreStrategy").value("best"))
                .andExpect(jsonPath("$.data.resultsPublished").value(false))
                .andReturn();
        examId = om.readTree(exam.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/exams/" + examId + "/add_questions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[" + q1 + "," + q2 + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/exams/" + examId + "/invite")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[" + studentId + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/exams/" + examId + "/publish")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M6 全链：两轮次交卷 → 发布门控 → 申诉改分 → 重算总分 → 监考/分析/导出")
    void fullChain() throws Exception {
        // ==== 第一轮：全对 = 20 分 ====
        JsonNode r1 = submitRound("A", "B");
        assertEquals(20.0, r1.path("score").asDouble(), 1e-9);
        assertEquals(1, r1.path("attemptNo").asInt());
        assertEquals(false, r1.path("published").asBoolean());
        resultId = r1.path("resultId").asLong();

        // 未发布：学生成绩列表总分为 null 且 published=false
        JsonNode myRows = om.readTree(mockMvc.perform(get("/api/results/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("data");
        JsonNode gated = null;
        for (JsonNode n : myRows) {
            if (n.path("id").asLong() == resultId) gated = n;
        }
        assertTrue(gated != null, "成绩列表缺少当轮记录");
        assertEquals(false, gated.path("published").asBoolean());
        assertTrue(gated.path("score").isNull(), "未发布时总分应置 null");

        // ==== 第二轮（best 策略允许再考）：全错 = 0 分 ====
        JsonNode r2 = submitRound("B", "A");
        assertEquals(0.0, r2.path("score").asDouble(), 1e-9);
        assertEquals(2, r2.path("attemptNo").asInt());

        // 第三轮应被 maxAttempts=2 拦截（BUSINESS_ERROR → HTTP 400）
        mockMvc.perform(post("/api/exam/" + examId + "/start")
                        .with(csrf()).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());

        // ==== 教师发布成绩 ====
        mockMvc.perform(post("/api/exams/" + examId + "/publish_results")
                        .with(csrf()).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultsPublished").value(true));

        // best 策略：有效成绩为第一轮 20 分
        JsonNode me = om.readTree(mockMvc.perform(get("/api/results/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode eff = null;
        for (JsonNode n : me.path("data")) {
            if (n.path("examId").asLong() == examId && n.path("attemptNo").asInt() == 1) eff = n;
        }
        assertTrue(eff != null, "找不到第一轮成绩");
        assertEquals(20.0, eff.path("score").asDouble(), 1e-9);
        assertEquals(true, eff.path("published").asBoolean());

        // ==== 监考与试题分析 ====
        mockMvc.perform(get("/api/exams/" + examId + "/monitor")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentId").value(studentId))
                .andExpect(jsonPath("$.data[0].totalCount").value(2))
                .andExpect(jsonPath("$.data[0].attemptNo").value(2));
        mockMvc.perform(get("/api/exams/" + examId + "/item_analysis")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // ==== Excel 导出（PK 魔数校验） ====
        MvcResult xlsx = mockMvc.perform(get("/api/exams/" + examId + "/results/export")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk()).andReturn();
        byte[] bytes = xlsx.getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{0x50, 0x4B, 0x03, 0x04},
                java.util.Arrays.copyOf(bytes, 4), "导出内容应为 xlsx(zip) 流");

        // ==== 学生对第二轮 0 分发起申诉 ====
        mockMvc.perform(post("/api/results/" + resultId + "/review")
                        .with(csrf()).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"判分有误，请求复核\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("pending"));

        // 教师批准并改分为 15
        mockMvc.perform(post("/api/results/" + resultId + "/review_handle")
                        .with(csrf()).header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\",\"newScore\":15,\"reply\":\"复核属实，已加分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"));

        // 详情验证：总分重算为 15，带申诉回复
        mockMvc.perform(get("/api/results/" + resultId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(15.0))
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.reviewReply").value("复核属实，已加分"));
    }

    @Test
    @DisplayName("补考授权 grant_attempt 上调 maxAttempts")
    void grantAttempt() throws Exception {
        mockMvc.perform(post("/api/exams/" + examId + "/grant_attempt")
                        .with(csrf()).header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"extraAttempts\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAttempts").value(3));
    }

    /** 完成一轮 start→take→save_answer×2→submit，交卷后按题号顺序作答并返回 SubmitResponse */
    private JsonNode submitRound(String answer1, String answer2) throws Exception {
        mockMvc.perform(post("/api/exam/" + examId + "/start")
                        .with(csrf()).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        MvcResult take = mockMvc.perform(get("/api/exam/" + examId + "/take")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andReturn();
        JsonNode qs = om.readTree(take.getResponse().getContentAsString())
                .path("data").path("questions");
        long q1 = qs.get(0).path("questionId").asLong();
        long q2 = qs.get(1).path("questionId").asLong();
        saveAnswer(q1, answer1);
        saveAnswer(q2, answer2);
        MvcResult sub = mockMvc.perform(post("/api/exam/" + examId + "/submit")
                        .with(csrf()).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("submitted"))
                .andReturn();
        return om.readTree(sub.getResponse().getContentAsString()).path("data");
    }

    private void saveAnswer(long questionId, String answer) throws Exception {
        mockMvc.perform(post("/api/exam/" + examId + "/save_answer")
                        .with(csrf()).header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + questionId + ",\"studentAnswer\":\"" + answer + "\"}"))
                .andExpect(status().isOk());
    }

    private long createQuestion(String content, String options, String answer) throws Exception {
        String optionsJson = "[" + java.util.Arrays.stream(options.split(" "))
                .map(o -> "\"" + o + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
        MvcResult res = mockMvc.perform(post("/api/questions")
                        .with(csrf()).header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"options\":" + optionsJson
                                + ",\"answer\":\"" + answer + "\",\"type\":\"single_choice\",\"difficulty\":\"easy\"}"))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String registerAndLogin(String username, String role) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"123123\",\"role\":\"" + role + "\"}";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123123\"}"))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
