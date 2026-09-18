package com.exam.backend.integration;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExamApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = registerAndLogin("exam_teacher", "teacher");
        studentToken = registerAndLogin("exam_student", "student");
    }

    private String registerAndLogin(String username, String role) throws Exception {
        String body = role == null
                ? "{\"username\":\"" + username + "\",\"password\":\"123123\"}"
                : "{\"username\":\"" + username + "\",\"password\":\"123123\",\"role\":\"" + role + "\"}";
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn(); // 用户可能已存在（create-drop 下不会），忽略结果
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    @DisplayName("教师：创建考试 → 列表可见 → 详情可读")
    void teacherCreateAndViewExam() throws Exception {
        // 创建
        MvcResult created = mockMvc.perform(post("/api/exams")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"集成测试考试","description":"IT","duration":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.title").value("集成测试考试"))
                .andReturn();

        long examId = om.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 列表
        mockMvc.perform(get("/api/exams")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(examId));

        // 详情
        mockMvc.perform(get("/api/exams/" + examId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duration").value(45));
    }

    @Test
    @DisplayName("学生：禁止创建考试（403），空考试列表返回 200")
    void studentCannotCreateExam() throws Exception {
        mockMvc.perform(post("/api/exams")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"学生想偷偷建考试","duration":30}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/exams")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("创建考试同时支持空格与 ISO 两种日期格式")
    void createExam_acceptsBothDateTimeFormats() throws Exception {
        // yyyy-MM-dd HH:mm:ss（前端 Element Plus 常用）
        mockMvc.perform(post("/api/exams")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"空格格式","duration":30,"startTime":"2026-10-01 09:00:00","endTime":"2026-10-01 10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("空格格式"));

        // ISO-8601（Jackson 默认）
        mockMvc.perform(post("/api/exams")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"ISO格式","duration":30,"startTime":"2026-10-02T09:00:00","endTime":"2026-10-02T10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("ISO格式"));

        // 非法日期 → 400
        mockMvc.perform(post("/api/exams")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"错误格式","duration":30,"startTime":"not-a-date"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未认证访问考试列表 → 401")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get("/api/exams"))
                .andExpect(status().isUnauthorized());
    }
}
