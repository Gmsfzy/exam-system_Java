package com.exam.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("注册 → 登录 → /me 全链路通过")
    void registerLoginMeFlow() throws Exception {
        // 1. 注册
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"it_teacher","password":"123123","email":"it@x.com","role":"teacher"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("teacher"))
                .andReturn();

        String token = om.readTree(reg.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // 2. 重复注册 → 409
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"it_teacher","password":"123123"}
                                """))
                .andExpect(status().isConflict());

        // 3. 参数校验失败（密码太短）→ 400
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"123"}
                                """))
                .andExpect(status().isBadRequest());

        // 4. 无 token 访问 /me → 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // 5. 带 token 访问 /me → 200
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("it_teacher"));

        // 6. 伪造 token → 401
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录成功 / 密码错误 401")
    void loginSuccessAndFailure() throws Exception {
        // 先注册一个学生
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"it_stu","password":"123123"}
                                """))
                .andExpect(status().isOk());

        // 正确密码
        MvcResult ok = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"it_stu","password":"123123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("student"))
                .andReturn();

        JsonNode node = om.readTree(ok.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(node.path("data").path("token").asText()).isNotBlank();

        // 错误密码
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"it_stu","password":"wrong-pwd"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
