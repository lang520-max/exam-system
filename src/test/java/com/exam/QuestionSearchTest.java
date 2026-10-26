package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.entity.Question;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 题库关键词搜索。
 *
 * <p>为什么单开一个测试类：题库有 300 道题，老师找一道特定题目原先只能翻页
 * （只有题库/题型/难度三个下拉），效率极低。关键词搜索是这一轮新加的能力，
 * 要锁住三件事：命中的字段范围、**大小写不敏感在两个数据库上一致**、
 * 以及与既有筛选的叠加。</p>
 *
 * <p>大小写那一条尤其值得测：MySQL 的默认排序规则不区分大小写，而 H2 的
 * {@code LIKE} 区分 —— 若直接用 {@code like()}，会出现「本地搜不到、线上搜得到」，
 * 属于最难查的一类不一致。实现里统一走 {@code LOWER(...) LIKE}。</p>
 */
@AutoConfigureMockMvc
@DisplayName("题库关键词搜索")
class QuestionSearchTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String PASSWORD = "123456";

    @Test
    @DisplayName("关键词命中题干，且大小写不敏感")
    void keywordMatchesContentIgnoringCase() throws Exception {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1,
                "Java 中的 HashMap 是线程安全的吗", "A", 5);
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "完全无关的一道题", "A", 5);

        JsonNode data = search("keyword", "java", "bankId", bank.getId());

        assertThat(data.get("total").asInt()).as("只应命中含 Java 的那一道").isEqualTo(1);
        assertThat(data.get("list").get(0).get("content").asText()).contains("HashMap");
    }

    @Test
    @DisplayName("关键词也能命中选项、解析与答案")
    void keywordMatchesOptionsAnalysisAndAnswer() throws Exception {
        Question byOption = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "题干甲", "A", 5);
        byOption.setOptions("[{\"label\":\"A\",\"content\":\"多态性\"},{\"label\":\"B\",\"content\":\"封装性\"}]");
        questionMapper.updateById(byOption);

        Question byAnalysis = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "题干乙", "A", 5);
        byAnalysis.setAnalysis("这里考察的是二叉树的遍历顺序");
        questionMapper.updateById(byAnalysis);

        Question byAnswer = createQuestion(bank.getId(), QuestionType.FILL, 1, "题干丙", "光合作用", 5);
        questionMapper.updateById(byAnswer);

        assertThat(search("keyword", "多态", "bankId", bank.getId()).get("total").asInt())
                .as("选项内容应可搜").isEqualTo(1);
        assertThat(search("keyword", "二叉树", "bankId", bank.getId()).get("total").asInt())
                .as("解析应可搜").isEqualTo(1);
        assertThat(search("keyword", "光合作用", "bankId", bank.getId()).get("total").asInt())
                .as("答案应可搜").isEqualTo(1);
    }

    @Test
    @DisplayName("关键词与题库/题型筛选叠加生效")
    void keywordCombinesWithFilters() throws Exception {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "极限的计算", "A", 5);
        createQuestion(bank.getId(), QuestionType.ESSAY, 1, "极限的证明", "因为…", 16);

        assertThat(search("keyword", "极限", "bankId", bank.getId()).get("total").asInt()).isEqualTo(2);
        assertThat(search("keyword", "极限", "type", 0, "bankId", bank.getId()).get("total").asInt())
                .as("叠加题型后只剩单选").isEqualTo(1);
        assertThat(search("keyword", "极限", "type", 0).get("total").asInt())
                .as("不限定题库时至少包含我这两道（测试库另有种子数据）")
                .isGreaterThanOrEqualTo(1);
        assertThat(search("keyword", "极限", "bankId", 999999).get("total").asInt())
                .as("别的题库里没有").isZero();
    }

    @Test
    @DisplayName("不传关键词时列表口径不变（回归）")
    void withoutKeywordListsEverything() throws Exception {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "题一", "A", 5);
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "题二", "A", 5);

        // 一律限定到本测试自己的题库：test 档的 data-test.sql 里有种子题目，
        // 不限题库的计数会把这些种子算进来（第一版断言就是这么假失败的）
        assertThat(search("bankId", bank.getId()).get("total").asInt()).isEqualTo(2);
        assertThat(search("bankId", bank.getId(), "keyword", "").get("total").asInt())
                .as("空关键词等同于不传").isEqualTo(2);
        assertThat(search("bankId", bank.getId(), "keyword", "  ").get("total").asInt())
                .as("纯空白也等同于不传").isEqualTo(2);
    }

    // ---- helpers ----

    /**
     * 以教师身份搜题，返回 data 节点（{list,total,pageNum,pageSize}）。
     *
     * <p>参数用 {@code .param(k,v)} 传，而不是拼在 URL 上：拼 URL 时
     * {@code %20} 这类转义在 MockMvc 里不会被当作 servlet 那样解码，
     * 「纯空白关键词」那条断言会因此假失败（实测踩到）。</p>
     */
    private JsonNode search(Object... kv) throws Exception {
        var req = get("/api/questions");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            req = req.param(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        MvcResult res = mockMvc.perform(req
                        .header("Authorization", "Bearer " + login(teacher.getUsername())))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private String login(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").get("accessToken").asText();
    }
}