package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.common.exception.BusinessException;
import com.exam.dto.QuestionSnapshot;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamImportDTO;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.mapper.ExamMapper;
import com.exam.service.ExamImportService;
import com.exam.service.ExamRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 专题/类型（V4）：题目的知识点 + 试卷的类型。
 *
 * <p>需求原话是「增加试卷类型，例如高数的一元函数积分学的专项训练，还有英语的语法练习」。
 * 这里锁住三件事：</p>
 * <ol>
 *   <li>按专题抽题**只抽该专题的题**，不够时**不跨专题借题**（借了就不叫专项了）；</li>
 *   <li>试卷类型能落库、默认值是「模拟卷」（老客户端不传时行为不变）；</li>
 *   <li>导入 JSON 也能带 category / paperType。</li>
 * </ol>
 */
@AutoConfigureMockMvc
@DisplayName("专题与试卷类型（V4）")
class CategoryAndPaperTypeTest extends BaseIntegrationTest {

    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamImportService examImportService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("按专题组卷：卷面只含该专题的题（混合难度）")
    void generateExamByCategoryPicksOnlyThatCategory() {
        // 积分专题：3 道（难度刻意不同，检验「难度之间借调」不会借出专题）
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 1, "积分一", "A", 5), "一元函数积分学");
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 2, "积分二", "A", 5), "一元函数积分学");
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 3, "积分三", "A", 5), "一元函数积分学");
        // 干扰项：别的专题
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 1, "极限一", "A", 5), "极限与连续");
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 2, "极限二", "A", 5), "极限与连续");

        ExamGenerateDTO special = generateForm("一元函数积分学专项训练",
                rule(QuestionType.SINGLE, 3, 5, "一元函数积分学"));
        special.setPaperType("专项训练");
        Exam exam = examRuleService.generateExam(special, teacher.getId());

        List<Long> ids = snapshotIds(exam);
        assertThat(ids).hasSize(3);
        for (Long id : ids) {
            Question q = questionMapper.selectById(id);
            assertThat(q.getCategory())
                    .as("专项卷里混进了「" + q.getContent() + "」——它属于 " + q.getCategory())
                    .isEqualTo("一元函数积分学");
        }
        assertThat(exam.getPaperType()).isEqualTo("专项训练");
    }

    @Test
    @DisplayName("专题题量不足：明确报错，绝不跨专题借题")
    void insufficientCategoryFailsInsteadOfBorrowing() {
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 1, "积分一", "A", 5), "一元函数积分学");
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "极限一", "A", 5);   // 别的专题

        assertThatThrownBy(() -> examRuleService.generateExam(
                generateForm("题量不足的专项卷", rule(QuestionType.SINGLE, 3, 5, "一元函数积分学")), teacher.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一元函数积分学")
                .hasMessageContaining("不会跨专题借题");
    }

    @Test
    @DisplayName("不传试卷类型时默认「模拟卷」（老客户端行为不变）")
    void paperTypeDefaultsToMock() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "普通题", "A", 5);

        ExamGenerateDTO form = generateForm("不带类型的卷子", rule(QuestionType.SINGLE, 1, 5, null));
        form.setPaperType(null);
        Exam exam = examRuleService.generateExam(form, teacher.getId());

        assertThat(exam.getPaperType()).isEqualTo("模拟卷");
        assertThat(examMapper.selectById(exam.getId()).getPaperType()).as("确实落库了").isEqualTo("模拟卷");
    }

    @Test
    @DisplayName("不带知识点的规则照旧抽全库（V4 之前的行为不变）")
    void ruleWithoutCategoryStillPicksAcrossBank() {
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 1, "积分一", "A", 5), "一元函数积分学");
        withCategory(createQuestion(bank.getId(), QuestionType.SINGLE, 1, "极限一", "A", 5), "极限与连续");
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "无标签题", "A", 5);

        Exam exam = examRuleService.generateExam(
                generateForm("普通模拟卷", rule(QuestionType.SINGLE, 2, 5, null)), teacher.getId());

        assertThat(snapshotIds(exam)).as("不限专题时应能抽到不同专题的题").hasSize(2);
        assertThat(exam.getPaperType()).isEqualTo("模拟卷");
    }

    @Test
    @DisplayName("导入试卷：题目带 category、试卷带 paperType")
    void importCarriesCategoryAndPaperType() throws Exception {
        String json = """
                {
                  "bankId": %d,
                  "title": "导入的语法专项卷",
                  "paperType": "专项训练",
                  "duration": 60,
                  "questions": [
                    {"type": 0, "score": 10, "category": "语法", "content": "导入题一",
                     "options": {"A": "x", "B": "y"}, "answer": "A", "analysis": "解析一"}
                  ]
                }
                """.formatted(bank.getId());

        ExamImportDTO form = objectMapper.readValue(json, ExamImportDTO.class);
        Map<String, Object> result = examImportService.importPapers(form, teacher.getId());
        assertThat(result.get("importedExams")).isEqualTo(1);

        Exam exam = examMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Exam>()
                .eq(Exam::getTitle, "导入的语法专项卷"));
        assertThat(exam).isNotNull();
        assertThat(exam.getPaperType()).isEqualTo("专项训练");

        Question imported = questionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getContent, "导入题一"));
        assertThat(imported.getCategory()).as("导入时给的知识点要落库，否则专项练习抽不到它").isEqualTo("语法");
    }

    @Test
    @DisplayName("导入试卷不写 category 时留空（不产生一个空名字的假专题）")
    void importWithoutCategoryLeavesNull() throws Exception {
        String json = """
                {
                  "bankId": %d,
                  "title": "导入的无标签卷",
                  "duration": 60,
                  "questions": [
                    {"type": 2, "score": 5, "content": "判断题一", "answer": "A", "analysis": "解析"}
                  ]
                }
                """.formatted(bank.getId());

        examImportService.importPapers(objectMapper.readValue(json, ExamImportDTO.class), teacher.getId());

        Question imported = questionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getContent, "判断题一"));
        assertThat(imported.getCategory()).isNull();
        Exam exam = examMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Exam>()
                .eq(Exam::getTitle, "导入的无标签卷"));
        assertThat(exam.getPaperType()).as("不传类型时默认模拟卷").isEqualTo("模拟卷");
    }

    // ---- helpers ----

    private Question withCategory(Question q, String category) {
        q.setCategory(category);
        questionMapper.updateById(q);
        return questionMapper.selectById(q.getId());
    }

    private ExamGenerateDTO.RuleItem rule(int type, int count, int score, String category) {
        ExamGenerateDTO.RuleItem item = new ExamGenerateDTO.RuleItem();
        item.setType(type);
        item.setCount(count);
        item.setScore(score);
        item.setCategory(category);
        return item;
    }

    private ExamGenerateDTO generateForm(String title, ExamGenerateDTO.RuleItem rule) {
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle(title + "_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(60);
        // 注意：这里**不**默认设置 paperType —— 除了专项卷那一条，其余用例
        // 要验的正是「不传类型时的默认行为」，统一设上就把被测行为绕过去了
        form.setRules(List.of(rule));
        return form;
    }

    private List<Long> snapshotIds(Exam exam) {
        List<Long> ids = new ArrayList<>();
        try {
            for (QuestionSnapshot s : objectMapper.readValue(exam.getQuestionSnapshot(), QuestionSnapshot[].class)) {
                ids.add(s.getQuestionId());
            }
        } catch (Exception e) {
            throw new IllegalStateException("快照解析失败", e);
        }
        return ids;
    }
}