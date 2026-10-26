package com.exam;

import com.exam.common.constant.RecordStatus;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生个人成绩概览（{@code /api/dashboard/my-stats}）的口径。
 *
 * <p>这个接口与教师端统计（{@code ExamRecordMapper.statsByExamId}）用的是同一批
 * {@code exam_record}，两边的「已交卷」定义必须一致：{@code status IN (1, 2)}。
 * 此前概览写的是 {@code status >= 2}，把 TIMEOUT(3)「超时未交卷」也算成了已交卷 ——
 * 同一份数据两个完成人数，学生挂机超时反而显得更像完成了考试。</p>
 */
@AutoConfigureMockMvc
@DisplayName("学生概览 · 已交卷口径")
class DashboardMyStatsTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamRecordMapper recordMapper;

    private static final String PASSWORD = "123456";

    @Test
    @DisplayName("超时(3)与考试中(0)都不算已交卷，待判分(1)算")
    void myStats_countsOnlySubmittedRecords() throws Exception {
        // exam_record 上有 (exam_id, user_id) 唯一键，因此一名学生的多条记录要挂在不同试卷上
        addRecord(newExam(100, 60), student, null, RecordStatus.TIMEOUT);
        addRecord(newExam(100, 60), student, null, RecordStatus.ONGOING);
        addRecord(newExam(100, 60), student, null, RecordStatus.PENDING_GRADE);
        addRecord(newExam(100, 60), student, 80, RecordStatus.GRADED);

        JsonNode data = fetchStats(student);

        assertThat(data.path("totalExams").asInt()).as("考了几场：含未交卷的").isEqualTo(4);
        assertThat(data.path("submittedCount").asInt())
                .as("已交卷 = 待判分 + 已出分，超时不算").isEqualTo(2);
        assertThat(data.path("avgScore").asDouble())
                .as("平均分只算已交卷且有分的记录").isEqualTo(80.0);
        assertThat(data.path("maxScore").asInt()).isEqualTo(80);
        assertThat(data.path("passedCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("超时记录即便带了分数，也不计入平均分 / 最高分")
    void myStats_ignoresScoredTimeoutRecord() throws Exception {
        addRecord(newExam(100, 60), student, 90, RecordStatus.GRADED);
        // 脏数据（例如人工补写）：状态是超时却留着分数。
        // 若平均分按「分数非空」筛选而不看状态，90 与 10 会被一起平均成 50。
        addRecord(newExam(100, 60), student, 10, RecordStatus.TIMEOUT);

        JsonNode data = fetchStats(student);

        assertThat(data.path("submittedCount").asInt()).isEqualTo(1);
        assertThat(data.path("avgScore").asDouble()).isEqualTo(90.0);
        assertThat(data.path("maxScore").asInt()).isEqualTo(90);
    }

    @Test
    @DisplayName("一条记录都没有时返回 0 而不是 null")
    void myStats_withoutRecords_returnsZeros() throws Exception {
        User fresh = createUser("s_" + unique(), "新同学", 2);

        JsonNode data = fetchStats(fresh);

        assertThat(data.path("totalExams").asInt()).isZero();
        assertThat(data.path("submittedCount").asInt()).isZero();
        assertThat(data.path("avgScore").asDouble()).isZero();
        assertThat(data.path("maxScore").asInt()).isZero();
        assertThat(data.path("passedCount").asInt()).isZero();
    }

    @Test
    @DisplayName("待判分（有状态、无分数）计入已交卷，但不拉低平均分")
    void myStats_pendingGradeCountsAsSubmittedWithoutScore() throws Exception {
        addRecord(newExam(100, 60), student, 88, RecordStatus.GRADED);
        addRecord(newExam(100, 60), student, null, RecordStatus.PENDING_GRADE);

        JsonNode data = fetchStats(student);

        assertThat(data.path("submittedCount").asInt()).isEqualTo(2);
        assertThat(data.path("avgScore").asDouble())
                .as("把待判分的 null 当成 0 分会让平均分腰斩").isEqualTo(88.0);
    }

    // ==================== 辅助 ====================

    private JsonNode fetchStats(User who) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/my-stats")
                        .header("Authorization", "Bearer " + tokenOf(who.getUsername())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private Exam newExam(int totalScore, int passScore) {
        Exam e = new Exam();
        e.setTitle("概览测试卷_" + unique());
        e.setBankId(bank.getId());
        e.setDuration(60);
        e.setTotalScore(totalScore);
        e.setPassScore(passScore);
        e.setStatus(0);
        e.setCreatedBy(teacher.getId());
        examMapper.insert(e);
        return examMapper.selectById(e.getId());
    }

    /** 造一条考试记录；score 为 null 表示「未出分」 */
    private void addRecord(Exam exam, User who, Integer score, int status) {
        ExamRecord r = new ExamRecord();
        r.setExamId(exam.getId());
        r.setUserId(who.getId());
        r.setStartTime(LocalDateTime.now().minusMinutes(30));
        r.setEndTime(score == null ? null : LocalDateTime.now());
        r.setScore(score);
        r.setStatus(status);
        recordMapper.insert(r);
    }

    /** 走真实登录换 JWT：与浏览器里跑的链路一致 */
    private String tokenOf(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asText();
    }
}