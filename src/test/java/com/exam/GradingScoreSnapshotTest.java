package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.exam.common.constant.RecordStatus.GRADED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 判分分值口径回归测试 —— 「题库默认分值 ≠ 卷面分值」时必须以卷面为准。
 *
 * <p><b>为什么单独为一个分值口径写测试</b>：这是一个真实踩过的坑。
 * 判分最初读的是 {@code question.score}（题库默认分值），而组卷时教师可以按规则
 * 把某题在本卷中定为别的分值。只要两者不一致，一份<b>全对</b>的卷子就会拿到
 * 「题库分值之和」而不是试卷总分——例如卷面 70 分却只得 40 分，
 * 学生成绩、及格判定、成绩统计全部错位。</p>
 *
 * <p>这个 bug 之所以能躲过最初的集成测试，是因为那些测试里
 * 「题库分值」与「卷面分值」恰好相等（造题时用了同一个数），
 * 掩盖了口径差异。因此这里<b>刻意让两者不同</b>。</p>
 */
@DisplayName("判分分值口径 · 回归")
class GradingScoreSnapshotTest extends BaseIntegrationTest {

    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamRecordMapper recordMapper;
    @Autowired
    private StudentAnswerMapper answerMapper;

    @Test
    @DisplayName("题库默认 5 分、卷面 20 分时，全对必须得 20 分（按卷面）")
    void gradingUsesSnapshotScoreNotBankScore() {
        // 题库默认分值刻意设小：5 分
        Question q = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "分值口径题", "A", 5);

        // 卷面把这道题定为 20 分
        Exam exam = generatePublishedExam(q.getId(), 20);

        assertThat(exam.getTotalScore()).as("试卷总分应取规则里的 20 分").isEqualTo(20);

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        Map<Long, String> answers = new LinkedHashMap<>();
        answers.put(q.getId(), "A"); // 全对
        submit(record.getId(), answers, student);

        ExamRecord graded = recordMapper.selectById(record.getId());
        assertThat(graded.getStatus()).isEqualTo(GRADED);
        assertThat(graded.getScore())
                .as("全对得分必须等于卷面分值 20，而不是题库默认的 5")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("成绩单里每题的满分取自卷面快照")
    void resultItemFullScoreComesFromSnapshot() {
        Question q = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "分值展示题", "A", 5);
        Exam exam = generatePublishedExam(q.getId(), 20);

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        submit(record.getId(), Map.of(q.getId(), "A"), student);

        var result = examService.getResult(record.getId(), student.getId());
        assertThat(result.getAnswers()).hasSize(1);
        assertThat(result.getAnswers().get(0).getScore())
                .as("展示的满分应为卷面 20 分")
                .isEqualTo(20);
        assertThat(result.getAnswers().get(0).getEarnedScore()).isEqualTo(20);
        assertThat(result.getAnswers().get(0).getIsCorrect()).isTrue();
        assertThat(result.getScore()).isEqualTo(exam.getTotalScore());
        assertThat(result.getPassed()).isTrue();
    }

    @Test
    @DisplayName("多题混合：总分 = 各题卷面分值之和，全对即满分")
    void fullMarkEqualsExamTotalScoreAcrossMixedScores() {
        // 题库默认分值统一是 5，卷面分值刻意各不相同
        Question q1 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "混合单选", "A", 5);
        Question q2 = createQuestion(bank.getId(), QuestionType.MULTIPLE, 2, "混合多选", "ABC", 5);
        Question q3 = createQuestion(bank.getId(), QuestionType.JUDGE, 1, "混合判断", "B", 5);

        Exam exam = generatePublishedExam(
                List.of(new Rule(q1.getId(), QuestionType.SINGLE, 15),
                        new Rule(q2.getId(), QuestionType.MULTIPLE, 25),
                        new Rule(q3.getId(), QuestionType.JUDGE, 10)));

        assertThat(exam.getTotalScore()).isEqualTo(50);

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        Map<Long, String> answers = new LinkedHashMap<>();
        answers.put(q1.getId(), "A");
        answers.put(q2.getId(), "ABC");
        answers.put(q3.getId(), "B");
        submit(record.getId(), answers, student);

        ExamRecord graded = recordMapper.selectById(record.getId());
        assertThat(graded.getScore())
                .as("全对必须等于试卷总分 50（15+25+10），而不是题库的 5+5+5")
                .isEqualTo(50);
    }

    @Test
    @DisplayName("成绩单要带上选项与解析 —— 只有题干加字母，选择题等于看不到原题")
    void resultCarriesOptionsAndAnalysis() {
        Question q = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "选项与解析题", "A", 5);
        q.setAnalysis("解析：由定义可知 A 正确。");
        questionMapper.updateById(q);

        Exam exam = generatePublishedExam(q.getId(), 5);
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        submit(record.getId(), Map.of(q.getId(), "B"), student);   // 故意答错，去复习错题

        var item = examService.getResult(record.getId(), student.getId()).getAnswers().get(0);
        assertThat(item.getOptions())
                .as("选项必须随成绩单返回：否则学生只看到「我的作答 B / 正确答案 A」，"
                        + "根本不知道 B 和 A 分别写了什么")
                .isNotNull()
                .contains("\"label\":\"A\"");
        assertThat(item.getAnalysis()).isEqualTo("解析：由定义可知 A 正确。");
        assertThat(item.getIsCorrect()).isFalse();
    }

    @Test
    @DisplayName("客观题的对错以 is_correct 为准：得分被折算过也不能把答对显示成没全对")
    void objectiveCorrectnessUsesFlagNotEarnedScore() {
        Question q = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "折算过的历史数据", "A", 5);
        Exam exam = generatePublishedExam(q.getId(), 20);
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        submit(record.getId(), Map.of(q.getId(), "A"), student);   // 答对：AUTO 判 is_correct=1

        // 模拟历史数据：判分结论是「对」，但得分比卷面满分小
        // （云端演示库真实出现过：旧的 100 分制记录被按比例折算，5 分题存成 3 分）
        answerMapper.update(null, new LambdaUpdateWrapper<StudentAnswer>()
                .eq(StudentAnswer::getRecordId, record.getId())
                .set(StudentAnswer::getScore, 12));

        var item = examService.getResult(record.getId(), student.getId()).getAnswers().get(0);
        assertThat(item.getPendingReview()).isFalse();
        assertThat(item.getIsCorrect())
                .as("判分结论就是「答对」，不能因为分数小于卷面满分而变成「没全对」——"
                        + "否则「只看错题」会把答对的题留在错题列表里（第二轮 Bug 13）")
                .isTrue();
        assertThat(item.getEarnedScore()).isEqualTo(12);
    }

    // ---- helpers ----

    private record Rule(Long questionId, int type, int score) {
    }

    /** 造一份只含单个已知题目、并按指定分值计分的已发布试卷 */
    private Exam generatePublishedExam(Long questionId, int score) {
        return generatePublishedExam(List.of(new Rule(questionId, QuestionType.SINGLE, score)));
    }

    /**
     * 生成一份包含指定题目的试卷。
     *
     * <p>这里绕开随机抽题：先按规则抽（规则数量=1，题库里该题型也可能有多道），
     * 但为了断言精确，直接手工构造题目快照写库，让「考哪几道题」完全确定。</p>
     */
    private Exam generatePublishedExam(List<Rule> rules) {
        Exam exam = new Exam();
        exam.setTitle("分值口径试卷_" + unique());
        exam.setBankId(bank.getId());
        exam.setDuration(30);
        int total = rules.stream().mapToInt(Rule::score).sum();
        exam.setTotalScore(total);
        exam.setPassScore(Math.max(1, total / 2));
        exam.setStatus(1);
        exam.setCreatedBy(teacher.getId());

        StringBuilder snapshot = new StringBuilder("[");
        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            if (i > 0) {
                snapshot.append(",");
            }
            snapshot.append("{\"questionId\":").append(r.questionId())
                    .append(",\"score\":").append(r.score()).append("}");
        }
        snapshot.append("]");
        exam.setQuestionSnapshot(snapshot.toString());
        exam.setRuleConfig("[]");

        examMapper.insert(exam);
        return examMapper.selectById(exam.getId());
    }

    private void submit(Long recordId, Map<Long, String> answers, User as) {
        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setRecordId(recordId);
        dto.setAnswers(answers);
        examService.submitExam(dto, as.getId(), "snap-" + unique());
    }
}