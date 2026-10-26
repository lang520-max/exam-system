package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.common.constant.RecordStatus;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「待判分」补偿任务 —— S1 那个缺陷的自愈兜底。
 *
 * <p>模拟最坏情况：答卷已经交上来、答案也落库了，但判分<b>永远没发生</b>
 * （消息丢了，或消费者在事务提交前读到「考试中」的旧状态而幂等跳过）。
 * 此时记录会永久停在「待判分」，学生永远看不到成绩 —— 补偿任务必须把它捞回来。</p>
 */
@DisplayName("待判分补偿")
class GradingCompensationTest extends BaseIntegrationTest {

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

    @BeforeEach
    void initExam() {
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "补偿题", "A", 10);

        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(QuestionType.SINGLE);
        rule.setCount(1);
        rule.setScore(10);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("补偿测试_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(45);
        form.setPassScore(6);
        form.setRules(List.of(rule));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(1);   // 发布
        examMapper.updateById(created);
        exam = examMapper.selectById(created.getId());
    }

    @Test
    @DisplayName("长期停在「待判分」的答卷会被补偿任务重判出分")
    void stuckPendingGrade_isRegraded() {
        ExamRecord record = submitCorrectAnswer();

        // 人为退回「已交卷但没判分」的状态：状态=待判分、分数清空、
        // 交卷时间回拨 3 分钟（超过 120 秒阈值），答题行也退回未判分
        makeStuck(record.getId(), LocalDateTime.now().minusSeconds(180));
        // 用「从库里读回来的」值做基准：H2 存的时间精度与 Java 的 nanos 不同，
        // 两次都读库比对才是有意义的相等
        LocalDateTime submitTime = recordMapper.selectById(record.getId()).getEndTime();

        int regraded = examService.sweepStuckPendingGradeRecords();

        assertThat(regraded).isEqualTo(1);
        ExamRecord after = recordMapper.selectById(record.getId());
        assertThat(after.getStatus()).isEqualTo(RecordStatus.GRADED);
        assertThat(after.getScore()).as("答对了那 10 分必须被补回来").isEqualTo(10);
        assertThat(after.getEndTime())
                .as("补判出分不是学生交卷，交卷时间必须原样保留")
                .isEqualTo(submitTime);
    }

    @Test
    @DisplayName("重判不动交卷时间：主观题没评完的卷子被反复重判也不能把「用时」往后推")
    void repeatedSweep_doesNotMoveSubmitTime() {
        // 一份「客观题 + 主观题」的卷子。AI 关闭时主观题没人评分，
        // 它会一直停在「待判分」—— 于是每轮补偿扫描都会重判它。
        // 这正是真实场景：判分收尾若沿用 compareAndSetSubmit（带 end_time = NOW()），
        // 交卷时间就一次比一次新，成绩单上的「用时」会变成「从开考到现在」。
        createQuestion(bank.getId(), QuestionType.ESSAY, 1, "补偿简答题", "参考答案", 16);

        ExamGenerateDTO.RuleItem objective = new ExamGenerateDTO.RuleItem();
        objective.setType(QuestionType.SINGLE);
        objective.setCount(1);
        objective.setScore(10);

        ExamGenerateDTO.RuleItem subjective = new ExamGenerateDTO.RuleItem();
        subjective.setType(QuestionType.ESSAY);
        subjective.setCount(1);
        subjective.setScore(16);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("补偿用时测试_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(45);
        form.setPassScore(6);
        form.setRules(List.of(objective, subjective));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(1);   // 发布
        examMapper.updateById(created);
        Exam paper = examMapper.selectById(created.getId());

        ExamRecord record = examService.startExam(paper.getId(), student.getId(), "127.0.0.1");

        Map<Long, String> answers = new HashMap<>();
        for (Question q : examRuleService.getExamQuestions(paper)) {
            answers.put(q.getId(), QuestionType.isSubjective(q.getType()) ? "我的回答" : "A");
        }
        ExamSubmitDTO submitForm = new ExamSubmitDTO();
        submitForm.setRecordId(record.getId());
        submitForm.setAnswers(answers);
        examService.submitExam(submitForm, student.getId(), "req-keep-end-" + unique());

        assertThat(recordMapper.selectById(record.getId()).getStatus())
                .as("主观题没评完，记录应停在待判分")
                .isEqualTo(RecordStatus.PENDING_GRADE);

        makeStuck(record.getId(), LocalDateTime.now().minusSeconds(180));
        LocalDateTime submitTime = recordMapper.selectById(record.getId()).getEndTime();
        assertThat(submitTime).as("基准时间确实被回拨了").isBefore(LocalDateTime.now().minusSeconds(170));

        // 连跑两轮：每轮都会重判这份卷子（状态仍是待判分、交卷时间仍是 3 分钟前）
        assertThat(examService.sweepStuckPendingGradeRecords()).isEqualTo(1);
        assertThat(examService.sweepStuckPendingGradeRecords()).isEqualTo(1);

        ExamRecord after = recordMapper.selectById(record.getId());
        assertThat(after.getEndTime())
                .as("重判一次就推后一次交卷时间 —— 用时会被判分悄悄改掉")
                .isEqualTo(submitTime);
        assertThat(after.getStatus()).isEqualTo(RecordStatus.PENDING_GRADE);
        assertThat(after.getScore()).as("客观题那 10 分要补回来，主观题仍待复核").isEqualTo(10);
    }

    @Test
    @DisplayName("交卷时间确实缺失时判分收尾会补一个（「用时」不会永远为空）")
    void gradingResult_fillsMissingEndTime() {
        ExamRecord record = submitCorrectAnswer();

        // 人为造出「待判分、但交卷时间为空」的历史数据：
        // 这种记录进不了补偿任务（那条查询要求 end_time 非空），只能靠 COALESCE 兜底
        recordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getId, record.getId())
                .set(ExamRecord::getStatus, RecordStatus.PENDING_GRADE)
                .set(ExamRecord::getEndTime, null));

        recordMapper.compareAndSetGradingResult(
                record.getId(), 10, RecordStatus.PENDING_GRADE, RecordStatus.GRADED);

        ExamRecord after = recordMapper.selectById(record.getId());
        assertThat(after.getStatus()).isEqualTo(RecordStatus.GRADED);
        assertThat(after.getScore()).isEqualTo(10);
        assertThat(after.getEndTime()).as("缺交卷时间要补上，而不是留空").isNotNull();
    }

    @Test
    @DisplayName("刚交卷、还在正常判分窗口内的记录不会被补偿任务抢着重判")
    void freshPendingGrade_isLeftAlone() {
        ExamRecord record = submitCorrectAnswer();
        // 状态是待判分，但交卷时间就是「刚刚」——正常判分还没跑完，不该插手
        makeStuck(record.getId(), LocalDateTime.now());

        int regraded = examService.sweepStuckPendingGradeRecords();

        assertThat(regraded).isZero();
        assertThat(recordMapper.selectById(record.getId()).getStatus())
                .isEqualTo(RecordStatus.PENDING_GRADE);
    }

    // ==================== 辅助 ====================

    /** 正常交卷（test 档无 MQ，会走同步判分），返回记录 */
    private ExamRecord submitCorrectAnswer() {
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        Long questionId = examRuleService.getExamQuestions(exam).get(0).getId();

        ExamSubmitDTO form = new ExamSubmitDTO();
        form.setRecordId(record.getId());
        form.setAnswers(Map.of(questionId, "A"));   // 标准答案就是 A
        examService.submitExam(form, student.getId(), "req-compensate-" + unique());

        return recordMapper.selectById(record.getId());
    }

    /** 把记录与答题行退回「未判分」的样子，模拟判分消息丢失 */
    private void makeStuck(Long recordId, LocalDateTime endTime) {
        recordMapper.update(null, new LambdaUpdateWrapper<ExamRecord>()
                .eq(ExamRecord::getId, recordId)
                .set(ExamRecord::getStatus, RecordStatus.PENDING_GRADE)
                .set(ExamRecord::getScore, null)
                .set(ExamRecord::getEndTime, endTime));

        answerMapper.update(null, new LambdaUpdateWrapper<StudentAnswer>()
                .eq(StudentAnswer::getRecordId, recordId)
                .set(StudentAnswer::getScore, 0)
                .set(StudentAnswer::getIsCorrect, 0)
                .set(StudentAnswer::getGradedBy, null));

        assertThat(answerMapper.selectList(new LambdaQueryWrapper<StudentAnswer>()
                .eq(StudentAnswer::getRecordId, recordId))).isNotEmpty();
    }
}