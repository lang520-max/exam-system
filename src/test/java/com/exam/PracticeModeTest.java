package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.dto.response.PracticeResultVO;
import com.exam.dto.response.QuestionVO;
import com.exam.entity.Question;
import com.exam.mapper.ExamRecordMapper;
import com.exam.service.PracticeService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自由练习模式。
 *
 * <p>练习是「只读 + 现场判分」：<b>不写 exam_record</b>，否则一次练习就是一条无意义的
 * 成绩记录，会把及格率与分数分布全部污染。这一点用「练习前后记录数不变」锁死。</p>
 *
 * <p>另一条必须锁的是**答案不能随题目下发**：抽题接口只给题干与选项，
 * 提交之后 {@code /check} 才返回正确答案与解析 —— 否则一轮练习的答案可以一次性拿走。</p>
 */
@AutoConfigureMockMvc
@DisplayName("自由练习模式")
class PracticeModeTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PracticeService practiceService;
    @Autowired
    private ExamRecordMapper recordMapper;

    private static final String PASSWORD = "123456";

    @Test
    @DisplayName("抽题接口不下发答案与解析")
    void sampleDoesNotLeakAnswers() throws Exception {
        Question q = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "练习题干", "A", 5);
        q.setAnalysis("这段解析绝不能在抽题时就给出去");
        questionMapper.updateById(q);

        // 限定到本测试自己的题库 + 数量上限抽满：test 档的 data-test.sql 有种子题目，
        // 不限题库时随机抽 5 道很可能抽不到我刚建的那道（第一版断言就是这么假失败的）
        MvcResult res = mockMvc.perform(get("/api/practice/questions?count=50&bankId=" + bank.getId())
                        .header("Authorization", "Bearer " + login(student.getUsername())))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).as("抽题响应里不能出现 answer 字段").doesNotContain("\"answer\"");
        assertThat(body).as("抽题响应里不能出现 analysis 字段").doesNotContain("\"analysis\"");
        assertThat(body).as("更不能出现解析正文").doesNotContain("绝不能在抽题时就给出去");
        assertThat(body).contains("练习题干");
    }

    @Test
    @DisplayName("提交后才给出正确答案与解析")
    void checkRevealsAnswerAfterSubmit() {
        Question q = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "对答案题", "B", 5);
        q.setAnalysis("选 B 的理由");
        questionMapper.updateById(q);

        PracticeResultVO ok = practiceService.check(q.getId(), "B");
        assertThat(ok.getJudged()).isTrue();
        assertThat(ok.getCorrect()).isTrue();
        assertThat(ok.getCorrectAnswer()).isEqualTo("B");
        assertThat(ok.getAnalysis()).isEqualTo("选 B 的理由");

        PracticeResultVO bad = practiceService.check(q.getId(), "C");
        assertThat(bad.getCorrect()).isFalse();
    }

    @Test
    @DisplayName("判分口径与考试一致：大小写/修饰符/多选顺序都不影响判定")
    void judgingMatchesExamRules() {
        Question single = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "大小写题", "A", 5);
        assertThat(practiceService.check(single.getId(), " a ").getCorrect())
                .as("「 a 」应当等同于「A」——与 GradingEngine 同一套 normalize").isTrue();

        Question multi = createQuestion(bank.getId(), QuestionType.MULTIPLE, 1, "多选题", "AC", 5);
        assertThat(practiceService.check(multi.getId(), "C,A").getCorrect())
                .as("多选书写顺序不影响判定").isTrue();
        assertThat(practiceService.check(multi.getId(), "A").getCorrect())
                .as("少选算错").isFalse();

        Question judge = createQuestion(bank.getId(), QuestionType.JUDGE, 1, "判断题", "A", 3);
        assertThat(practiceService.check(judge.getId(), "正确").getCorrect())
                .as("判断题「正确」等同于 A").isTrue();
    }

    @Test
    @DisplayName("填空/简答不假装判对错，只给参考答案")
    void subjectiveIsNotAutoJudged() {
        Question fill = createQuestion(bank.getId(), QuestionType.FILL, 1, "填空题", "光合作用", 5);
        PracticeResultVO r = practiceService.check(fill.getId(), "光合作用");

        assertThat(r.getJudged()).as("没有可靠的自动判据就不该给判定").isFalse();
        assertThat(r.getCorrect()).as("judged=false 时 correct 必须是 null，不能是 false").isNull();
        assertThat(r.getCorrectAnswer()).isEqualTo("光合作用");
    }

    @Test
    @DisplayName("练习不写 exam_record（成绩统计不受污染）")
    void practiceDoesNotCreateExamRecord() throws Exception {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "不落库题", "A", 5);
        long before = recordMapper.selectCount(null);

        String token = login(student.getUsername());
        MvcResult res = mockMvc.perform(get("/api/practice/questions?count=50&bankId=" + bank.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        long qid = list.get(0).get("id").asLong();

        mockMvc.perform(post("/api/practice/check")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":" + qid + ",\"answer\":\"A\"}"))
                .andExpect(status().isOk());

        assertThat(recordMapper.selectCount(null))
                .as("练完一轮不该多出任何考试记录")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("题库列表只返回有题的库，并带可用题量")
    void banksOnlyIncludeNonEmptyOnes() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "有题的库", "A", 5);
        createBank("空题库_" + unique(), teacher.getId());

        var banks = practiceService.listBanks();
        assertThat(banks).anyMatch(b -> b.getId().equals(bank.getId()) && b.getTotal() >= 1);
        assertThat(banks).as("空题库不该出现在练习下拉里（点进去只会是空白）")
                .noneMatch(b -> b.getName().startsWith("空题库_"));
    }

    @Test
    @DisplayName("抽题受题型/难度筛选与数量上限约束")
    void sampleRespectsFiltersAndLimit() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "单选一", "A", 5);
        createQuestion(bank.getId(), QuestionType.SINGLE, 2, "单选二", "A", 5);
        createQuestion(bank.getId(), QuestionType.JUDGE, 1, "判断题", "A", 3);

        List<QuestionVO> singles = practiceService.sample(bank.getId(), QuestionType.SINGLE, null, null, 10);
        assertThat(singles).hasSize(2).allMatch(q -> q.getType() == QuestionType.SINGLE);

        List<QuestionVO> easySingles = practiceService.sample(bank.getId(), QuestionType.SINGLE, 1, null, 10);
        assertThat(easySingles).hasSize(1);

        assertThat(practiceService.sample(bank.getId(), null, null, null, 999))
                .as("数量上限 50 会夹紧，而不是把整个题库拉走")
                .hasSizeLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("按知识点抽题：只返回该专题的题（专项练习的基础）")
    void sampleByCategory() {
        Question a1 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "积分题一", "A", 5);
        a1.setCategory("一元函数积分学");
        questionMapper.updateById(a1);
        Question a2 = createQuestion(bank.getId(), QuestionType.SINGLE, 2, "积分题二", "A", 5);
        a2.setCategory("一元函数积分学");
        questionMapper.updateById(a2);
        Question b1 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "极限题", "A", 5);
        b1.setCategory("极限与连续");
        questionMapper.updateById(b1);
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "没标知识点的题", "A", 5);

        List<QuestionVO> pool = practiceService.sample(bank.getId(), null, null, "一元函数积分学", 20);
        assertThat(pool).as("只应拿到该专题的 2 道").hasSize(2);
        assertThat(pool).extracting(QuestionVO::getContent)
                .allMatch(c -> c.startsWith("积分题"));

        assertThat(practiceService.sample(bank.getId(), null, null, "不存在的专题", 20))
                .as("不存在的专题应当抽不到题，而不是退化成「不限专题」")
                .isEmpty();
    }

    @Test
    @DisplayName("知识点下拉只列库里真正用到的标签，并带题量")
    void listCategoriesReflectsLibrary() {
        Question q1 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "题一", "A", 5);
        q1.setCategory("语法");
        questionMapper.updateById(q1);
        Question q2 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "题二", "A", 5);
        q2.setCategory("语法");
        questionMapper.updateById(q2);

        var list = practiceService.listCategories(bank.getId());
        assertThat(list).anyMatch(c -> "语法".equals(c.getCategory()) && c.getTotal() == 2);
        assertThat(list).as("没标知识点的题不应聚成一个空名字的专题")
                .noneMatch(c -> c.getCategory() == null || c.getCategory().isBlank());
    }

    // ---- helpers ----

    private String login(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").get("accessToken").asText();
    }
}