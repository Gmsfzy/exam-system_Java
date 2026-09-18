package com.exam.backend.integration;

import com.exam.backend.domain.entity.Major;
import com.exam.backend.repository.MajorRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4 征集悬赏全链路：发布题目征集 → 他人投稿 → 发布者采纳 → 题目入库(source=bounty) → 状态闭环。
 * 附带权限与幂等校验：非发布者采纳 403、重复投稿 409、自投 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BountyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MajorRepository majorRepository;

    private final ObjectMapper om = new ObjectMapper();

    private String teacherToken;  // 发布者
    private String studentToken;  // 投稿者
    private long majorId;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = registerAndLogin("bounty_teacher", "teacher");
        studentToken = registerAndLogin("bounty_student", "student");
        majorId = majorRepository.save(Major.builder().name("软件工程").build()).getId();
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

    /** 教师发布一条题目征集，返回 bountyId */
    private long publishQuestionBounty() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/bounties")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bountyType":"question","title":"征集数据结构好题","description":"欢迎出题",
                                 "majorId":%d,"qType":"single_choice","qDifficulty":"easy","rewardPoints":20}
                                """.formatted(majorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("open"))
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long submitQuestion(long bountyId, String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/bounties/" + bountyId + "/submissions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qContent":"下列哪个是栈的特性？","qOptions":["FIFO","LIFO","随机","优先"],
                                 "qAnswer":"B","qAnalysis":"栈后进先出","qType":"single_choice","qDifficulty":"easy","qKnowledge":"栈"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("采纳闭环：发布→投稿→采纳→题目入库 source=bounty")
    void publishSubmitAcceptIngest() throws Exception {
        long bountyId = publishQuestionBounty();
        long sid = submitQuestion(bountyId, studentToken);

        // 发布者采纳
        MvcResult accepted = mockMvc.perform(post("/api/submissions/" + sid + "/accept")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptedQuestionId").isNumber())
                .andExpect(jsonPath("$.data.rewardPoints").value(20))
                .andReturn();
        long newQuestionId = om.readTree(accepted.getResponse().getContentAsString())
                .path("data").path("acceptedQuestionId").asLong();

        // 入库题目可查，来源标记 bounty
        mockMvc.perform(get("/api/questions/" + newQuestionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("bounty"))
                .andExpect(jsonPath("$.data.isPublic").value(true));

        // 悬赏关闭
        mockMvc.perform(get("/api/bounties/" + bountyId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("closed"))
                .andExpect(jsonPath("$.data.acceptedSubmissionId").value(sid));

        // 投稿人视角：我的投稿 accepted
        mockMvc.perform(get("/api/bounties/my-submissions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].submissionStatus").value("accepted"));
    }

    @Test
    @DisplayName("权限与幂等：非发布者采纳 403、重复投稿 409、自投 400")
    void guards() throws Exception {
        long bountyId = publishQuestionBounty();
        long sid = submitQuestion(bountyId, studentToken);

        // 重复投稿 → 409
        mockMvc.perform(post("/api/bounties/" + bountyId + "/submissions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qContent\":\"再投一题\",\"qAnswer\":\"A\"}"))
                .andExpect(status().isConflict());

        // 发布者给自己投稿 → 400
        mockMvc.perform(post("/api/bounties/" + bountyId + "/submissions")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qContent\":\"自己投\",\"qAnswer\":\"A\"}"))
                .andExpect(status().isBadRequest());

        // 非发布者（投稿者本人）采纳 → 403
        mockMvc.perform(post("/api/submissions/" + sid + "/accept")
                        .with(csrf())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("答案征集：无关联题与快照 → 400")
    void answerBountyValidation() throws Exception {
        mockMvc.perform(post("/api/bounties")
                        .with(csrf())
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bountyType\":\"answer\",\"title\":\"缺目标的answer悬赏\",\"rewardPoints\":10}"))
                .andExpect(status().isBadRequest());
    }
}
