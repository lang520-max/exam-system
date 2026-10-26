package com.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.constant.QuestionType;
import com.exam.common.constant.RecordStatus;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamService;
import com.exam.service.GradingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 判分相关「无事务」集成测试的夹具基类。
 *
 * <p><b>为什么刻意不继承 {@link BaseIntegrationTest}</b>：那个基类用 {@code @Transactional}
 * 让每个用例自动回滚，而这里要断言的恰恰是「AI 被调用时线程上没有活动事务」——
 * 测试自身带着的事务会让 {@code isActualTransactionActive()} 恒为 true，
 * 断言就永远不可能通过，也就永远发现不了「AI 在事务里跑」这个缺陷。
 * 换句话说：只有让用例本身不带事务，这条断言才有意义。</p>
 *
 * <p>代价是数据不会自动回滚，因此这里显式清理自己造的行（见 {@link #cleanUp()}）。
 * 夹具直接写库、只把记录置为「待判分」，不经过 {@code ExamService.submitExam}——
 * 后者自带 {@code @Transactional}，会把 AI 调用重新圈进它自己的事务里。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class GradingFixtureTestBase {

    protected static final long USER_ID = 990001L;

    /** 卷面满分（question_snapshot）刻意与题库默认分值不同：顺带守住「以卷面为准」的口径 */
    protected static final int OBJECTIVE_FULL = 8;
    protected static final int OBJECTIVE2_FULL = 6;
    protected static final int SUBJECTIVE_FULL = 12;

    @Autowired
    protected GradingEngine gradingEngine;
    @Autowired
    protected ExamService examService;
    @Autowired
    protected QuestionMapper questionMapper;
    @Autowired
    protected ExamMapper examMapper;
    @Autowired
    protected ExamRecordMapper recordMapper;
    @Autowired
    protected StudentAnswerMapper answerMapper;

    protected Long examId;
    protected Long recordId;
    protected Long objectiveQuestionId;
    protected Long subjectiveQuestionId;
    protected Long subjective2QuestionId;

    private final List<Long> createdQuestionIds = new ArrayList<>();

    /** 一份「2 客观题（一对一错）+ 2 主观题」的答卷，状态停在「待判分」 */
    @BeforeEach
    void buildPendingRecord() {
        Question objective = insertQuestion(QuestionType.SINGLE, "客观题一", "A", 3);
        Question objective2 = insertQuestion(QuestionType.SINGLE, "客观题二", "A", 3);
        Question subjective = insertQuestion(QuestionType.FILL, "主观题一", "参考答案", 3);
        // 两道主观题是必要的：只有一道时，AI 批量写分的 CASE 语句永远只覆盖一行，
        // 「多行得分互串」这类 SQL 缺陷就测不出来
        Question subjective2 = insertQuestion(QuestionType.FILL, "主观题二", "参考答案二", 3);
        objectiveQuestionId = objective.getId();
        subjectiveQuestionId = subjective.getId();
        subjective2QuestionId = subjective2.getId();

        Exam exam = new Exam();
        exam.setTitle("判分夹具试卷_" + UUID.randomUUID());
        exam.setBankId(USER_ID);
        exam.setDuration(30);
        exam.setTotalScore(OBJECTIVE_FULL + OBJECTIVE2_FULL + SUBJECTIVE_FULL * 2);
        exam.setPassScore(10);
        exam.setStatus(1);
        exam.setCreatedBy(USER_ID);
        exam.setRuleConfig("[]");
        exam.setQuestionSnapshot("[{\"questionId\":" + objective.getId() + ",\"score\":" + OBJECTIVE_FULL + "},"
                + "{\"questionId\":" + objective2.getId() + ",\"score\":" + OBJECTIVE2_FULL + "},"
                + "{\"questionId\":" + subjective.getId() + ",\"score\":" + SUBJECTIVE_FULL + "},"
                + "{\"questionId\":" + subjective2.getId() + ",\"score\":" + SUBJECTIVE_FULL + "}]");
        examMapper.insert(exam);
        examId = exam.getId();

        ExamRecord record = new ExamRecord();
        record.setExamId(examId);
        record.setUserId(USER_ID);
        record.setStartTime(LocalDateTime.now());
        // 交卷后、尚未判分：判分引擎只认这个状态
        record.setStatus(RecordStatus.PENDING_GRADE);
        recordMapper.insert(record);
        recordId = record.getId();

        insertAnswer(objective.getId(), "A");     // 答对 → 8 分
        insertAnswer(objective2.getId(), "B");    // 答错 → 0 分
        insertAnswer(subjective.getId(), "我的解答过程一……");
        insertAnswer(subjective2.getId(), "我的解答过程二……");
    }

    @AfterEach
    void cleanUp() {
        if (recordId != null) {
            answerMapper.delete(new LambdaQueryWrapper<StudentAnswer>()
                    .eq(StudentAnswer::getRecordId, recordId));
            recordMapper.deleteById(recordId);
        }
        if (examId != null) {
            examMapper.deleteById(examId);
        }
        for (Long questionId : createdQuestionIds) {
            questionMapper.deleteById(questionId);
        }
    }

    protected StudentAnswer answerOf(Long questionId) {
        return answerMapper.selectList(new LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, recordId)
                        .eq(StudentAnswer::getQuestionId, questionId))
                .stream().findFirst().orElseThrow();
    }

    private Question insertQuestion(int type, String content, String answer, int bankScore) {
        Question question = new Question();
        question.setBankId(USER_ID);
        question.setType(type);
        question.setDifficulty(1);
        question.setContent(content);
        question.setAnswer(answer);
        question.setScore(bankScore);
        question.setStatus(1);
        question.setCreatedBy(USER_ID);
        questionMapper.insert(question);
        createdQuestionIds.add(question.getId());
        return question;
    }

    private void insertAnswer(Long questionId, String answer) {
        StudentAnswer sa = new StudentAnswer();
        sa.setRecordId(recordId);
        sa.setQuestionId(questionId);
        sa.setAnswer(answer);
        sa.setScore(0);
        sa.setIsCorrect(0);
        answerMapper.insert(sa);
    }
}