package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.common.exception.BusinessException;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static com.exam.common.constant.RecordStatus.GRADED;
import static com.exam.common.constant.RecordStatus.ONGOING;
import static com.exam.common.constant.RecordStatus.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 考试时限测试。
 *
 * <p>这一组测试覆盖的是一个曾经完全缺失的能力：<b>服务端交卷时限校验</b>。
 * 修复前，倒计时只存在于前端展示与 WebSocket 推送，`submitExam` 从不看时间，
 * 学生开考后挂机任意久都能交卷并照常判分（实测：45 分钟的考试拖了 3 小时仍被 200 接收）。</p>
 */
@DisplayName("考试时限")
class ExamTimeoutTest extends BaseIntegrationTest {

    /** 考试时长（分钟）。回拨超过它即为超时。 */
    private static final int DURATION_MINUTES = 45;

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
        createQuestion(bank.getId(), QuestionType.SINGLE, 1, "限时题", "A", 10);

        ExamGenerateDTO.RuleItem rule = new ExamGenerateDTO.RuleItem();
        rule.setType(QuestionType.SINGLE);
        rule.setCount(1);
        rule.setScore(10);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("限时测试_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(DURATION_MINUTES);
        form.setPassScore(6);
        form.setRules(List.of(rule));

        Exam created = examRuleService.generateExam(form, teacher.getId());
        created.setStatus(1); // 发布
        examMapper.updateById(created);
        exam = examMapper.selectById(created.getId());
    }

    /** 开考，并把 start_time 回拨到指定分钟数之前，模拟「已经考了这么久」 */
    private ExamRecord startWithElapsed(long elapsedMinutes) {
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        record.setStartTime(LocalDateTime.now().minusMinutes(elapsedMinutes));
        recordMapper.updateById(record);
        return recordMapper.selectById(record.getId());
    }

    /** 开考，并把 start_time 回拨到指定秒数之前（用于验证 30 秒宽限期的边界） */
    private ExamRecord startWithElapsedSeconds(long elapsedSeconds) {
        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");
        record.setStartTime(LocalDateTime.now().minusSeconds(elapsedSeconds));
        recordMapper.updateById(record);
        return recordMapper.selectById(record.getId());
    }

    private ExamSubmitDTO submitForm(Long recordId) {
        ExamSubmitDTO form = new ExamSubmitDTO();
        form.setRecordId(recordId);
        return form;
    }

    // ---------- 交卷时限 ----------

    @Test
    @DisplayName("时限内交卷正常通过")
    void submit_withinDeadline_succeeds() {
        ExamRecord record = startWithElapsed(5);

        var result = examService.submitExam(submitForm(record.getId()), student.getId(), "req-in-time");

        assertThat(result.get("status")).isEqualTo("PENDING_GRADE");
    }

    @Test
    @DisplayName("【关键】超出时限后交卷被拒绝")
    void submit_afterDeadline_rejected() {
        // 45 分钟的考试，已过去 180 分钟
        ExamRecord record = startWithElapsed(3 * 60);

        assertThatThrownBy(() -> examService.submitExam(
                submitForm(record.getId()), student.getId(), "req-late"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超时");
    }

    @Test
    @DisplayName("超时被拒后记录仍是「考试中」，没有被卡在「待判分」中间态")
    void rejectedSubmit_leavesRecordOngoing() {
        ExamRecord record = startWithElapsed(3 * 60);

        assertThatThrownBy(() -> examService.submitExam(
                submitForm(record.getId()), student.getId(), "req-late-state"))
                .isInstanceOf(BusinessException.class);

        // 这就是「时限校验必须放在 CAS 抢占之前」的原因：
        // 若放在之后，超时请求会先把记录改成 PENDING_GRADE 再抛异常，
        // 记录就卡在一个既没交卷、也不在考试中的状态里。
        assertThat(recordMapper.selectById(record.getId()).getStatus()).isEqualTo(ONGOING);
    }

    @Test
    @DisplayName("超时被拒后没有写入任何答案")
    void rejectedSubmit_writesNoAnswers() {
        ExamRecord record = startWithElapsed(3 * 60);

        assertThatThrownBy(() -> examService.submitExam(
                submitForm(record.getId()), student.getId(), "req-late-answers"))
                .isInstanceOf(BusinessException.class);

        assertThat(answerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, record.getId())))
                .isEmpty();
    }

    // ---------- 定时清理 ----------

    @Test
    @DisplayName("定时清理把超时未交卷的记录标记为「超时」")
    void sweep_marksExpiredRecordsTimeout() {
        ExamRecord record = startWithElapsed(3 * 60);

        int swept = examService.sweepTimeoutRecords();

        assertThat(swept).isGreaterThanOrEqualTo(1);
        ExamRecord after = recordMapper.selectById(record.getId());
        assertThat(after.getStatus()).isEqualTo(TIMEOUT);
        // 学生根本没交卷：不该凭空写出交卷时间或分数
        assertThat(after.getEndTime()).isNull();
        assertThat(after.getScore()).isNull();
    }

    @Test
    @DisplayName("清理不会误伤仍在时限内的记录")
    void sweep_doesNotTouchLiveRecords() {
        ExamRecord live = startWithElapsed(5);

        examService.sweepTimeoutRecords();

        assertThat(recordMapper.selectById(live.getId()).getStatus()).isEqualTo(ONGOING);
    }

    @Test
    @DisplayName("清理不会覆盖已经交卷的记录")
    void sweep_doesNotOverwriteSubmittedRecords() {
        ExamRecord record = startWithElapsed(5);
        examService.submitExam(submitForm(record.getId()), student.getId(), "req-done");
        int statusAfterSubmit = recordMapper.selectById(record.getId()).getStatus();
        assertThat(statusAfterSubmit).isGreaterThanOrEqualTo(GRADED);

        // 即使随后把开考时间回拨到很久以前，也不该把已出分的记录改成超时
        ExamRecord backdated = recordMapper.selectById(record.getId());
        backdated.setStartTime(LocalDateTime.now().minusHours(5));
        recordMapper.updateById(backdated);

        examService.sweepTimeoutRecords();

        assertThat(recordMapper.selectById(record.getId()).getStatus()).isEqualTo(statusAfterSubmit);
    }

    @Test
    @DisplayName("刚过 deadline、但仍在 30 秒交卷宽限内的记录不会被扫成超时")
    void sweep_leavesRecordsInsideGracePeriod() {
        // 考试 45 分钟：回拨 45 分 10 秒 → 刚超时 10 秒，还在 30 秒宽限内。
        // 扫描 SQL 此前不含宽限，会在这一刻把记录 CAS 成「超时」，
        // 学生随后在宽限期内交卷就收到 409、答案全部作废 —— 宽限期形同虚设。
        ExamRecord record = startWithElapsedSeconds(DURATION_MINUTES * 60 + 10);

        int swept = examService.sweepTimeoutRecords();

        assertThat(swept).isZero();
        assertThat(recordMapper.selectById(record.getId()).getStatus()).isEqualTo(ONGOING);
    }

    @Test
    @DisplayName("超出宽限期的记录照常被扫成超时")
    void sweep_marksRecordsBeyondGracePeriod() {
        // 超时 40 秒 > 30 秒宽限
        ExamRecord record = startWithElapsedSeconds(DURATION_MINUTES * 60 + 40);

        int swept = examService.sweepTimeoutRecords();

        assertThat(swept).isEqualTo(1);
        assertThat(recordMapper.selectById(record.getId()).getStatus()).isEqualTo(TIMEOUT);
    }

    @Test
    @DisplayName("已被清理为超时后再交卷，提示明确是超时而不是「重复提交」")
    void submit_afterSweep_reportsTimeout() {
        ExamRecord record = startWithElapsed(3 * 60);
        examService.sweepTimeoutRecords();

        assertThatThrownBy(() -> examService.submitExam(
                submitForm(record.getId()), student.getId(), "req-after-sweep"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超时");
    }
}