package com.exam;

import com.exam.dto.response.ExamStatsVO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.service.ExamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分数分布（统计弹窗里的直方图数据）。
 *
 * <p>分段口径是<b>得分率</b>而不是绝对分，所以这里特意用「满分 50」的卷子
 * 验证一遍：45 分在 50 分卷里是 90%（最高段），在 100 分卷里是 45%（最低段）。</p>
 */
@DisplayName("考试统计 · 分数分布")
class ExamStatsDistributionTest extends BaseIntegrationTest {

    @Autowired
    private ExamService examService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamRecordMapper recordMapper;

    @Test
    @DisplayName("按得分率分 5 段，且只有第一段属于「不及格」")
    void distribution_bucketsByRate() {
        Exam exam = newExam(100, 60);
        addRecord(exam.getId(), student, 10, 2);
        addRecord(exam.getId(), createUser("s_" + unique(), "学生", 2), 65, 2);
        addRecord(exam.getId(), createUser("s_" + unique(), "学生", 2), 75, 2);
        addRecord(exam.getId(), createUser("s_" + unique(), "学生", 2), 95, 2);

        ExamStatsVO stats = examService.getStats(exam.getId());
        List<ExamStatsVO.ScoreBucket> d = stats.getDistribution();

        assertThat(d).hasSize(5);
        assertThat(d).extracting(ExamStatsVO.ScoreBucket::getLabel)
                .containsExactly("0-59%", "60-69%", "70-79%", "80-89%", "90-100%");
        assertThat(d).extracting(ExamStatsVO.ScoreBucket::getCount)
                .containsExactly(1, 1, 1, 0, 1);
        assertThat(d).extracting(ExamStatsVO.ScoreBucket::isPassed)
                .containsExactly(false, true, true, true, true);
        assertThat(stats.getSubmittedCount()).isEqualTo(4);
        assertThat(stats.getPassCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("满分不是 100 时按得分率分段（50 分卷的 45 分落在 90-100% 段）")
    void distribution_usesRateNotAbsoluteScore() {
        Exam exam = newExam(50, 30);
        addRecord(exam.getId(), student, 45, 2);   // 90%
        addRecord(exam.getId(), createUser("s_" + unique(), "学生", 2), 25, 2);  // 50%

        List<ExamStatsVO.ScoreBucket> d = examService.getStats(exam.getId()).getDistribution();
        assertThat(d).extracting(ExamStatsVO.ScoreBucket::getCount)
                .containsExactly(1, 0, 0, 0, 1);
    }

    @Test
    @DisplayName("未出分的记录不计入分布（考试中 / 超时都不算）")
    void distribution_ignoresUnscoredRecords() {
        Exam exam = newExam(100, 60);
        addRecord(exam.getId(), student, null, 0);                                  // 考试中
        addRecord(exam.getId(), createUser("s_" + unique(), "学生", 2), null, 3);   // 超时（不伪造分数）
        addRecord(exam.getId(), createUser("s_" + unique(), "学生", 2), 80, 2);     // 已出分

        List<ExamStatsVO.ScoreBucket> d = examService.getStats(exam.getId()).getDistribution();
        // 只有那 1 条有分的记录进分布：混进未出分的记录会让「不及格」凭空多出一截
        assertThat(d).extracting(ExamStatsVO.ScoreBucket::getCount)
                .containsExactly(0, 0, 0, 1, 0);
    }

    @Test
    @DisplayName("无人交卷时返回 5 个空桶，而不是 null")
    void distribution_withNoSubmissions_returnsEmptyBuckets() {
        Exam exam = newExam(100, 60);

        List<ExamStatsVO.ScoreBucket> d = examService.getStats(exam.getId()).getDistribution();
        assertThat(d).hasSize(5);
        assertThat(d).allSatisfy(b -> assertThat(b.getCount()).isZero());
    }

    // ==================== 辅助 ====================

    private Exam newExam(int totalScore, int passScore) {
        Exam e = new Exam();
        e.setTitle("统计测试卷_" + unique());
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
    private void addRecord(Long examId, User who, Integer score, int status) {
        ExamRecord r = new ExamRecord();
        r.setExamId(examId);
        r.setUserId(who.getId());
        r.setStartTime(LocalDateTime.now().minusMinutes(30));
        r.setEndTime(score == null ? null : LocalDateTime.now());
        r.setScore(score);
        r.setStatus(status);
        recordMapper.insert(r);
    }
}