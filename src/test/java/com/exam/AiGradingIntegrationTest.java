package com.exam;

import com.exam.common.constant.Correctness;
import com.exam.common.constant.GradedBy;
import com.exam.common.constant.QuestionType;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.dto.response.ExamResultVO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.AiGradingService;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.exam.common.constant.RecordStatus.GRADED;
import static com.exam.common.constant.RecordStatus.PENDING_GRADE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AI 评分接入判分流程的集成测试。
 *
 * <p>用 {@code @MockBean} 替换 {@link AiGradingService}，因此不发起任何真实网络请求。
 * 真实的 HTTP 路径（请求体构造、响应解析、裁剪、超时降级）由
 * {@link com.exam.service.AiGradingServiceTest} 用本地假服务覆盖。</p>
 *
 * <p>这里要守住的核心语义：<b>「未评分」与「答错」必须可区分</b>。
 * 原先主观题一旦没被评上就写 0 分、并落进「答错」的显示分支，
 * 学生看到的是红色 0 分，而实际上只是没人评。</p>
 */
@DisplayName("AI 评分接入判分流程")
class AiGradingIntegrationTest extends BaseIntegrationTest {

    private static final int OBJECTIVE_FULL_SCORE = 5;
    private static final int SUBJECTIVE_FULL_SCORE = 10;

    @MockBean
    private AiGradingService aiGradingService;

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

    private Exam exam;
    private Question objectiveQuestion;
    private Question subjectiveQuestion;

    @BeforeEach
    void initExam() {
        objectiveQuestion = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "客观题", "A", OBJECTIVE_FULL_SCORE);
        subjectiveQuestion = createQuestion(bank.getId(), QuestionType.FILL, 2, "主观题", "标准答案", SUBJECTIVE_FULL_SCORE);

        ExamGenerateDTO.RuleItem r1 = new ExamGenerateDTO.RuleItem();
        r1.setType(QuestionType.SINGLE);
        r1.setCount(1);
        r1.setScore(OBJECTIVE_FULL_SCORE);

        ExamGenerateDTO.RuleItem r2 = new ExamGenerateDTO.RuleItem();
        r2.setType(QuestionType.FILL);
        r2.setCount(1);
        r2.setScore(SUBJECTIVE_FULL_SCORE);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("AI 判分测试卷_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(30);
        form.setPassScore(10);
        form.setRules(List.of(r1, r2));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(1);
        examMapper.updateById(created);
        exam = examMapper.selectById(created.getId());
    }

    /** 开考并交卷：客观题答对，主观题给出作答 */
    private Long submitExam() {
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");

        Map<Long, String> answers = new HashMap<>();
        answers.put(objectiveQuestion.getId(), "A");
        answers.put(subjectiveQuestion.getId(), "我的解答过程……");

        ExamSubmitDTO form = new ExamSubmitDTO();
        form.setRecordId(record.getId());
        form.setAnswers(answers);
        examService.submitExam(form, student.getId(), "req-" + unique());
        return record.getId();
    }

    private StudentAnswer subjectiveAnswerOf(Long recordId) {
        return answerMapper.selectSubjectiveByRecordId(recordId).stream()
                .filter(a -> a.getQuestionId().equals(subjectiveQuestion.getId()))
                .findFirst()
                .orElseThrow();
    }

    // ---------------- AI 关闭 ----------------

    @Test
    @DisplayName("AI 关闭时主观题保持「未评分」，不写 0 分")
    void aiDisabled_leavesSubjectiveUngraded() {
        when(aiGradingService.isEnabled()).thenReturn(false);

        Long recordId = submitExam();
        StudentAnswer subjective = subjectiveAnswerOf(recordId);

        assertThat(subjective.getGradedBy()).isNull();
        assertThat(subjective.getAiReason()).isNull();
        // 记录停在待判分：还有题没评
        assertThat(recordMapper.selectById(recordId).getStatus()).isEqualTo(PENDING_GRADE);
    }

    @Test
    @DisplayName("AI 关闭时成绩单把主观题标为「待复核」，而不是「答错」")
    void aiDisabled_marksPendingReviewNotWrong() {
        when(aiGradingService.isEnabled()).thenReturn(false);

        Long recordId = submitExam();
        ExamResultVO result = examService.getResult(recordId, student.getId());

        ExamResultVO.AnswerItem subjective = result.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals(subjectiveQuestion.getId()))
                .findFirst().orElseThrow();

        assertThat(subjective.getPendingReview()).isTrue();
        assertThat(subjective.getIsCorrect()).isFalse();
        assertThat(result.getPendingReviewCount()).isEqualTo(1);

        // 客观题不受影响
        ExamResultVO.AnswerItem objective = result.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals(objectiveQuestion.getId()))
                .findFirst().orElseThrow();
        assertThat(objective.getPendingReview()).isFalse();
        assertThat(objective.getIsCorrect()).isTrue();
        assertThat(objective.getEarnedScore()).isEqualTo(OBJECTIVE_FULL_SCORE);
    }

    // ---------------- AI 开启 ----------------

    @Test
    @DisplayName("AI 开启时主观题得到分数与理由，记录直接出分")
    void aiEnabled_scoresSubjective() {
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new AiGradingService.AiScore(8, Correctness.PARTIAL,
                        "思路正确，结论有笔误。", "test-model"));

        Long recordId = submitExam();
        StudentAnswer subjective = subjectiveAnswerOf(recordId);

        assertThat(subjective.getScore()).isEqualTo(8);
        assertThat(subjective.getIsCorrect()).isEqualTo(Correctness.PARTIAL);
        assertThat(subjective.getGradedBy()).isEqualTo(GradedBy.AI);
        assertThat(subjective.getAiReason()).isEqualTo("思路正确，结论有笔误。");
        assertThat(subjective.getAiModel()).isEqualTo("test-model");

        // 客观题 5 + 主观题 8 = 13，且没有题待复核 → 直接出分
        ExamRecord record = recordMapper.selectById(recordId);
        assertThat(record.getScore()).isEqualTo(13);
        assertThat(record.getStatus()).isEqualTo(GRADED);
    }

    @Test
    @DisplayName("AI 给出满分时该题判为「正确」")
    void aiFullMarks_markedCorrect() {
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new AiGradingService.AiScore(SUBJECTIVE_FULL_SCORE, Correctness.CORRECT,
                        "完全正确。", "test-model"));

        Long recordId = submitExam();

        assertThat(subjectiveAnswerOf(recordId).getScore()).isEqualTo(SUBJECTIVE_FULL_SCORE);
        assertThat(recordMapper.selectById(recordId).getScore())
                .isEqualTo(OBJECTIVE_FULL_SCORE + SUBJECTIVE_FULL_SCORE);
    }

    // ---------------- AI 失败降级 ----------------

    @Test
    @DisplayName("AI 调用失败时该题降级为待复核，客观题成绩不受影响")
    void aiFailure_degradesToPendingWithoutLosingObjectiveScore() {
        when(aiGradingService.isEnabled()).thenReturn(true);
        // 模拟超时/限流/返回畸形 —— 服务一律返回 null
        when(aiGradingService.grade(anyString(), anyString(), anyInt(), anyString())).thenReturn(null);

        Long recordId = submitExam();

        StudentAnswer subjective = subjectiveAnswerOf(recordId);
        assertThat(subjective.getGradedBy()).isNull();
        assertThat(subjective.getScore()).isZero();

        ExamRecord record = recordMapper.selectById(recordId);
        // 客观题的 5 分照常计入，没有被 AI 失败连带清零
        assertThat(record.getScore()).isEqualTo(OBJECTIVE_FULL_SCORE);
        assertThat(record.getStatus()).isEqualTo(PENDING_GRADE);
    }

    @Test
    @DisplayName("AI 失败时成绩单仍正确标出「待复核」")
    void aiFailure_stillReportedAsPending() {
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(anyString(), anyString(), anyInt(), anyString())).thenReturn(null);

        Long recordId = submitExam();
        ExamResultVO result = examService.getResult(recordId, student.getId());

        assertThat(result.getPendingReviewCount()).isEqualTo(1);
        assertThat(result.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals(subjectiveQuestion.getId()))
                .findFirst().orElseThrow()
                .getPendingReview()).isTrue();
    }
}