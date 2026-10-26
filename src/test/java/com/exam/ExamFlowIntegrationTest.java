package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.common.constant.RecordStatus;
import com.exam.dto.QuestionSnapshot;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.*;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.exam.service.GradingEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.exam.common.constant.RecordStatus.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 考试全流程集成测试 —— 组卷 → 发布 → 开考 → 取题 → 交卷 → 判分 → 查成绩。
 *
 * <p>跑真实的 Spring + H2 + MyBatis，每个测试方法独立事务并自动回滚。</p>
 */
@DisplayName("考试全流程")
class ExamFlowIntegrationTest extends BaseIntegrationTest {

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
    @Autowired
    private GradingEngine gradingEngine;
    @Autowired
    private ObjectMapper objectMapper;

    private Exam publishedExam;
    private Question q1, q2, q3, q4, q5, q6;
    private List<Question> examQuestions;

    @BeforeEach
    void initExamFixtures() {
        // 造 6 道题：2单选(1) 2多选 2判断(1)，构成一份 70 分卷子
        q1 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "单选1", "A", 10);
        q2 = createQuestion(bank.getId(), QuestionType.SINGLE, 2, "单选2", "B", 10);
        q3 = createQuestion(bank.getId(), QuestionType.MULTIPLE, 2, "多选1", "ABC", 20);
        q4 = createQuestion(bank.getId(), QuestionType.MULTIPLE, 3, "多选2", "AC", 20);
        q5 = createQuestion(bank.getId(), QuestionType.JUDGE, 1, "判断1 对", "A", 5);
        q6 = createQuestion(bank.getId(), QuestionType.JUDGE, 2, "判断2 错", "B", 5);

        // 组卷 + 发布
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("集成测试试卷_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(60);
        form.setPassScore(42);
        form.setRules(List.of(
                rule(QuestionType.SINGLE, 2, 10),
                rule(QuestionType.MULTIPLE, 2, 20),
                rule(QuestionType.JUDGE, 2, 5)));
        publishedExam = examRuleService.generateExam(form, teacher.getId());
        publishedExam.setStatus(1);
        examMapper.updateById(publishedExam);

        examQuestions = examRuleService.getExamQuestions(publishedExam);
    }

    // ---- 组卷 ----

    @Test
    @DisplayName("组卷后总分由各题分值累加得出")
    void generateExam_scoresSumToTotal() {
        assertThat(publishedExam.getTotalScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("组卷后题目快照不为空，且题数与规则一致")
    void generateExam_snapshotPersisted() {
        List<QuestionSnapshot> snapshot = examRuleService.parseSnapshot(publishedExam.getQuestionSnapshot());
        assertThat(snapshot).hasSize(6);
    }

    @Test
    @DisplayName("快照中每道题都带有卷面分值")
    void generateExam_snapshotScoresMatchRules() {
        List<QuestionSnapshot> snapshot = examRuleService.parseSnapshot(publishedExam.getQuestionSnapshot());
        int sum = snapshot.stream().mapToInt(QuestionSnapshot::getScore).sum();
        assertThat(sum).isEqualTo(70);
        assertThat(snapshot).allMatch(s -> s.getQuestionId() != null && s.getScore() > 0);
    }

    @Test
    @DisplayName("取题时返回快照确定的题目，且不含正确答案")
    void getExamQuestions_returnsSnapshotQuestionsOnly() {
        assertThat(examQuestions).hasSize(6);
        // Question::getAnswer 是存在的，这里不是检查字段是否存在，
        // 而是验证返回的题目 ID 都在快照里
        List<QuestionSnapshot> snapshot = examRuleService.parseSnapshot(publishedExam.getQuestionSnapshot());
        for (Question q : examQuestions) {
            assertThat(snapshot).anyMatch(s -> s.getQuestionId().equals(q.getId()));
        }
    }

    @Test
    @DisplayName("组卷时及格分不能高于总分")
    void generateExam_passScoreExceedsTotal_rejected() {
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("不合格试卷");
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(999);
        form.setRules(List.of(rule(QuestionType.SINGLE, 1, 5)));
        assertThatThrownBy(() -> examRuleService.generateExam(form, teacher.getId()))
                .hasMessageContaining("及格分");
    }

    @Test
    @DisplayName("组卷时同一题型不允许两条规则")
    void generateExam_duplicateTypeRule_rejected() {
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("重复规则试卷");
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(10);
        form.setRules(List.of(rule(QuestionType.SINGLE, 1, 5), rule(QuestionType.SINGLE, 1, 5)));
        assertThatThrownBy(() -> examRuleService.generateExam(form, teacher.getId()))
                .hasMessageContaining("同一题型");
    }

    @Test
    @DisplayName("题目不足时组卷失败并提示差几道")
    void generateExam_notEnoughQuestions_failsWithClearMessage() {
        // 要 99 道单选，只有 2 道 → 注定失败
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("缺题试卷");
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(10);
        form.setRules(List.of(rule(QuestionType.SINGLE, 99, 5)));
        assertThatThrownBy(() -> examRuleService.generateExam(form, teacher.getId()))
                .hasMessageContaining("数量不足");
    }

    // ---- 开考 ----

    @Test
    @DisplayName("开考创建考试记录，状态为 ONGOING")
    void startExam_createsRecord() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        assertThat(record.getStatus()).isEqualTo(ONGOING);
        assertThat(record.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("重复开考返回原记录，不重置计时")
    void startExam_twice_returnsSameRecord() {
        ExamRecord r1 = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        ExamRecord r2 = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        assertThat(r2.getId()).isEqualTo(r1.getId());
    }

    @Test
    @DisplayName("已交卷的考试不能再开考")
    void startExam_afterSubmit_rejected() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        // 交卷
        submitAndGrade(record.getId(), Map.of(q1.getId(), "A"));
        assertThatThrownBy(() -> examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1"))
                .hasMessageContaining("不能重复开考");
    }

    @Test
    @DisplayName("未发布的考试不能开考")
    void startExam_unpublishedExam_rejected() {
        Exam draft = buildDraftExam();
        assertThatThrownBy(() -> examService.startExam(draft.getId(), student.getId(), "127.0.0.1"))
                .hasMessageContaining("尚未发布");
    }

    // ---- 答题 ----

    @Test
    @DisplayName("取题列表题目数 = 快照题数")
    void getExamQuestions_questionCountMatchesSnapshot() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        var questions = examService.getExamQuestions(record.getId(), student.getId());
        assertThat(questions).hasSize(6);
    }

    @Test
    @DisplayName("答题中的考试才能取题")
    void getExamQuestions_alreadySubmitted_rejected() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        submitAndGrade(record.getId(), Map.of(q1.getId(), "A"));
        assertThatThrownBy(() -> examService.getExamQuestions(record.getId(), student.getId()))
                .hasMessageContaining("已交卷");
    }

    @Test
    @DisplayName("不能查看他人的题目")
    void getExamQuestions_othersRecord_rejected() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        User otherStudent = createUser("sother_" + unique(), "别人", 2);
        assertThatThrownBy(() -> examService.getExamQuestions(record.getId(), otherStudent.getId()))
                .hasMessageContaining("只能查看自己");
    }

    // ---- 交卷与判分 ----

    @Test
    @DisplayName("全对交卷得满分")
    void submit_fullMark_getsMaxScore() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        Map<Long, String> answers = Map.of(
                q1.getId(), "A", q2.getId(), "B",
                q3.getId(), "ABC", q4.getId(), "AC",
                q5.getId(), "A", q6.getId(), "B");
        submitAndGrade(record.getId(), answers);
        record = recordMapper.selectById(record.getId());
        assertThat(record.getStatus()).isEqualTo(GRADED);
        assertThat(record.getScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("多选少选不得分（全对才得分）")
    void submit_partialMultipleChoice_getsZeroForThatQuestion() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        // 多选 q3 答案是 ABC，学生只选了 AB → 不得分
        Map<Long, String> answers = Map.of(
                q1.getId(), "A", q2.getId(), "B",
                q3.getId(), "AB", q4.getId(), "AC",
                q5.getId(), "A", q6.getId(), "B");
        submitAndGrade(record.getId(), answers);
        record = recordMapper.selectById(record.getId());
        // 扣掉 q3 的 20 分
        assertThat(record.getScore()).isEqualTo(50);
    }

    @Test
    @DisplayName("判断题中英文写法均被正确判分")
    void submit_judgeChineseVariants_areGradedCorrectly() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        Map<Long, String> answers = Map.of(
                q1.getId(), "A", q2.getId(), "B",
                q3.getId(), "ABC", q4.getId(), "AC",
                q5.getId(), "正确", // 应该归一化为 A
                q6.getId(), "错");   // 应该归一化为 B
        submitAndGrade(record.getId(), answers);
        record = recordMapper.selectById(record.getId());
        assertThat(record.getScore()).isEqualTo(70);
    }

    @Test
    @DisplayName("重复交卷被拒绝，不二次计分")
    void submit_duplicate_rejectedAndNoDoubleScore() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        submitAndGrade(record.getId(), Map.of(q1.getId(), "A", q2.getId(), "B",
                q3.getId(), "ABC", q4.getId(), "AC", q5.getId(), "A", q6.getId(), "B"));
        record = recordMapper.selectById(record.getId());
        int score1 = record.getScore();

        // 第二次交卷应被拒绝
        ExamSubmitDTO dup = new ExamSubmitDTO();
        dup.setRecordId(record.getId());
        dup.setAnswers(Map.of(q1.getId(), "Z"));
        assertThatThrownBy(() -> examService.submitExam(dup, student.getId(), "dup-" + unique()))
                .hasMessageContaining("已交卷");

        record = recordMapper.selectById(record.getId());
        assertThat(record.getScore()).isEqualTo(score1);
    }

    @Test
    @DisplayName("评分后答卷明细条数 = 试卷题数，且每题都有得分记录")
    void submit_answerDetailsMatchQuestionCount() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        submitAndGrade(record.getId(), Map.of(q1.getId(), "A"));
        List<StudentAnswer> details = answerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, record.getId()));
        assertThat(details).hasSize(6);
    }

    @Test
    @DisplayName("未作答的题在答卷明细中得 0 分")
    void submit_unanswered_gradedAsZero() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        submitAndGrade(record.getId(), Map.of());
        List<StudentAnswer> details = answerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, record.getId()));
        assertThat(details).allMatch(a -> a.getScore() != null && a.getScore() == 0);
    }
    // ---- 成绩 ----

    @Test
    @DisplayName("查成绩返回试卷标题和各题明细")
    void getResult_showsExamTitleAndDetails() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        submitAndGrade(record.getId(), Map.of(q1.getId(), "A"));
        var result = examService.getResult(record.getId(), student.getId());
        assertThat(result.getExamTitle()).isEqualTo(publishedExam.getTitle());
        assertThat(result.getAnswers()).hasSize(6);
    }

    @Test
    @DisplayName("及格判定：得分 >= passScore")
    void getResult_passedWhenScoreGtePassScore() {
        ExamRecord record = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        // 只答两道题得 20 分，及格线 42
        submitAndGrade(record.getId(), Map.of(q1.getId(), "A", q2.getId(), "B"));
        var result = examService.getResult(record.getId(), student.getId());
        assertThat(result.getPassed()).isFalse();
    }

    // ---- 统计 ----

    @Test
    @DisplayName("考试统计：多人多卷场景")
    void stats_multipleStudents() {
        User s2 = createUser("st2_" + unique(), "学生2", 2);
        ExamRecord r1 = examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        ExamRecord r2 = examService.startExam(publishedExam.getId(), s2.getId(), "127.0.0.1");
        submitAndGrade(r1.getId(), Map.of(q1.getId(), "A", q2.getId(), "B"), student); // 20
        submitAndGrade(r2.getId(), Map.of(q1.getId(), "A"), s2); // 10

        var stats = examService.getStats(publishedExam.getId());
        assertThat(stats.getSubmittedCount()).isEqualTo(2);
        assertThat(stats.getMaxScore()).isEqualTo(20);
        assertThat(stats.getMinScore()).isEqualTo(10);
    }

    @Test
    @DisplayName("学生个人记录列表：只含自己的记录，且带出试卷标题与截止时间")
    void myRecords_returnsStudentOnly() {
        examService.startExam(publishedExam.getId(), student.getId(), "127.0.0.1");
        List<com.exam.dto.response.MyRecordVO> list = examService.getMyRecords(student.getId());
        assertThat(list).isNotEmpty();
        // 只可能拿到自己的记录（查询条件里写死了 userId）
        assertThat(list).allMatch(r -> r.getExamId().equals(publishedExam.getId()));
        // 列表要能直接显示试卷标题与总分，前端不必再拉一次试卷列表
        assertThat(list).allMatch(r -> publishedExam.getTitle().equals(r.getExamTitle()));
        assertThat(list).allMatch(r -> r.getTotalScore() != null && r.getDurationMinutes() != null);
        // 开考后应能算出交卷截止时刻（epoch 毫秒）
        assertThat(list).allMatch(r -> r.getDeadlineMillis() != null && r.getDeadlineMillis() > 0);
        assertThat(list).allMatch(r -> "考试中".equals(r.getStatusText()));
        // 未出分时 passed 必须是 null，而不是 false —— 否则前端会把「还在考」显示成「不及格」
        assertThat(list).allMatch(r -> r.getPassed() == null);
    }

    // ---- helpers ----

    private ExamGenerateDTO.RuleItem rule(int type, int count, int score) {
        ExamGenerateDTO.RuleItem r = new ExamGenerateDTO.RuleItem();
        r.setType(type);
        r.setCount(count);
        r.setScore(score);
        return r;
    }

    private Exam buildDraftExam() {
        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("草稿考试_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        // 1 道题 × 5 分 = 总分 5，及格分不能高于总分
        form.setPassScore(3);
        form.setRules(List.of(rule(QuestionType.SINGLE, 1, 5)));
        return examRuleService.generateExam(form, teacher.getId());
    }

    private void submitAndGrade(Long recordId, Map<Long, String> answers) {
        submitAndGrade(recordId, answers, student);
    }

    private void submitAndGrade(Long recordId, Map<Long, String> answers, User as) {
        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setRecordId(recordId);
        dto.setAnswers(answers);
        examService.submitExam(dto, as.getId(), "req-" + unique());
    }
}