package com.caikailang.examsystem;

import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.GradeItem;
import com.caikailang.examsystem.dto.GradeResult;
import com.caikailang.examsystem.dto.QuestionQuery;
import com.caikailang.examsystem.service.GradeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动判分规则的纯单元测试。
 *
 * <p>不启动 Spring、不连数据库，只测 {@link GradeCalculator} 这一个类，
 * 因此跑得飞快（毫秒级），也最容易定位问题。判分规则是考试系统的命门，
 * 这里把每一条规则都钉成一条用例。</p>
 *
 * @author lang520-max
 */
class GradeCalculatorTest {

    private final GradeCalculator calculator = new GradeCalculator();

    /* ------------------------------------------------------------------ */
    /* 构造测试题目的小工具                                                */
    /* ------------------------------------------------------------------ */

    private Question single(Long id, String answer, String score) {
        Question q = new Question();
        q.setId(id);
        q.setType(Question.TYPE_SINGLE);
        q.setContent("单选题" + id);
        q.setOptionA("选项A");
        q.setOptionB("选项B");
        q.setOptionC("选项C");
        q.setOptionD("选项D");
        q.setAnswer(answer);
        q.setScore(new BigDecimal(score));
        return q;
    }

    private Question multiple(Long id, String answer, String score) {
        Question q = single(id, answer, score);
        q.setType(Question.TYPE_MULTIPLE);
        q.setContent("多选题" + id);
        return q;
    }

    private Question judge(Long id, String answer, String score) {
        Question q = new Question();
        q.setId(id);
        q.setType(Question.TYPE_JUDGE);
        q.setContent("判断题" + id);
        q.setAnswer(answer);
        q.setScore(new BigDecimal(score));
        return q;
    }

    /* ------------------------------------------------------------------ */
    /* 单选题                                                              */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("判分-单选：答案命中得满分")
    void singleChoice_exactMatch_getsFullScore() {
        GradeItem item = calculator.grade(single(1L, "A", "4.0"), "A");

        assertTrue(item.isCorrect(), "选对应当判为正确");
        assertEquals(0, new BigDecimal("4.0").compareTo(item.getEarnedScore()), "应得满分 4 分");
    }

    @Test
    @DisplayName("判分-单选：答案不符得 0 分")
    void singleChoice_wrongAnswer_getsZero() {
        GradeItem item = calculator.grade(single(1L, "A", "4.0"), "C");

        assertFalse(item.isCorrect());
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getEarnedScore()), "选错应为 0 分");
    }

    @Test
    @DisplayName("判分-单选：忽略大小写与首尾空白")
    void singleChoice_ignoresCaseAndWhitespace() {
        GradeItem item = calculator.grade(single(1L, "A", "4.0"), "  a ");

        assertTrue(item.isCorrect(), "「 a 」应等价于「A」");
        assertEquals("A", item.getStudentAnswer(), "落库的应是规范化之后的答案");
    }

    /* ------------------------------------------------------------------ */
    /* 判断题                                                              */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("判分-判断：A/B 精确匹配得分")
    void judge_matchesExactOption() {
        assertTrue(calculator.grade(judge(1L, "A", "2.0"), "A").isCorrect());
        assertTrue(calculator.grade(judge(2L, "B", "2.0"), "B").isCorrect());
        assertFalse(calculator.grade(judge(3L, "A", "2.0"), "B").isCorrect());
    }

    @Test
    @DisplayName("判分-判断：对/错/正确/T/F 等写法统一归一化")
    void judge_normalizesChineseAndBooleanText() {
        assertEquals("A", GradeCalculator.normalize(Question.TYPE_JUDGE, "对"));
        assertEquals("A", GradeCalculator.normalize(Question.TYPE_JUDGE, "正确"));
        assertEquals("A", GradeCalculator.normalize(Question.TYPE_JUDGE, "T"));
        assertEquals("A", GradeCalculator.normalize(Question.TYPE_JUDGE, "√"));
        assertEquals("B", GradeCalculator.normalize(Question.TYPE_JUDGE, "错"));
        assertEquals("B", GradeCalculator.normalize(Question.TYPE_JUDGE, "错误"));
        assertEquals("B", GradeCalculator.normalize(Question.TYPE_JUDGE, "F"));
        assertEquals("B", GradeCalculator.normalize(Question.TYPE_JUDGE, "×"));

        // 学生答「正确」，标准答案存的是 A，也应判对
        assertTrue(calculator.grade(judge(1L, "A", "2.0"), "正确").isCorrect());
    }

    /* ------------------------------------------------------------------ */
    /* 多选题：全对才得分                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("判分-多选：全部选对得满分")
    void multiple_allCorrect_getsFullScore() {
        GradeItem item = calculator.grade(multiple(1L, "ABC", "8.0"), "ABC");

        assertTrue(item.isCorrect());
        assertEquals(0, new BigDecimal("8.0").compareTo(item.getEarnedScore()));
    }

    @Test
    @DisplayName("判分-多选：选项顺序不影响判分结果")
    void multiple_orderDoesNotMatter() {
        assertTrue(calculator.grade(multiple(1L, "ABC", "8.0"), "CBA").isCorrect(),
            "「CBA」与「ABC」是同一组选项，应判对");
        assertTrue(calculator.grade(multiple(1L, "ABD", "8.0"), "d, b, a").isCorrect(),
            "带分隔符与大小写混写也应判对");
    }

    @Test
    @DisplayName("判分-多选：少选不得分（不给半分）")
    void multiple_partialSelection_getsZero() {
        GradeItem item = calculator.grade(multiple(1L, "ABC", "8.0"), "AB");

        assertFalse(item.isCorrect(), "少选一个选项应判错");
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getEarnedScore()),
            "规则是多选全对才得分，少选必须 0 分");
    }

    @Test
    @DisplayName("判分-多选：多选不得分")
    void multiple_extraSelection_getsZero() {
        GradeItem item = calculator.grade(multiple(1L, "ABC", "8.0"), "ABCD");

        assertFalse(item.isCorrect(), "多选一个选项应判错");
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getEarnedScore()));
    }

    @Test
    @DisplayName("判分-多选：错选不得分；重复勾选同一选项去重后不影响结果")
    void multiple_wrongOrDuplicatedSelection() {
        GradeResult result = new GradeResult();
        result.addItem(calculator.grade(multiple(1L, "ABC", "8.0"), "ABD"));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalScore()), "错选一个选项应为 0 分");

        assertTrue(calculator.grade(multiple(1L, "AB", "8.0"), "AAB").isCorrect(),
            "同一选项被勾了两次等价于勾了一次，去重后仍应判对");
    }

    /* ------------------------------------------------------------------ */
    /* 未作答与整卷判分                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("判分-未作答：null 与空白串都按 0 分处理")
    void blankAnswer_getsZero() {
        GradeItem nullAnswer = calculator.grade(single(1L, "A", "4.0"), null);
        assertFalse(nullAnswer.isCorrect());
        assertNull(nullAnswer.getStudentAnswer(), "未作答应落库为 null");
        assertEquals(0, BigDecimal.ZERO.compareTo(nullAnswer.getEarnedScore()));

        GradeItem blankAnswer = calculator.grade(single(1L, "A", "4.0"), "   ");
        assertFalse(blankAnswer.isCorrect());
        assertEquals(0, BigDecimal.ZERO.compareTo(blankAnswer.getEarnedScore()));
    }

    @Test
    @DisplayName("判分-整卷：按题目分值累加总分并统计答对题数")
    void gradeWholePaper_sumsScoreAndCountsCorrect() {
        List<Question> paper = new ArrayList<Question>();
        paper.add(single(1L, "A", "10.0"));
        paper.add(multiple(2L, "ABC", "20.0"));
        paper.add(judge(3L, "B", "10.0"));
        paper.add(single(4L, "D", "10.0"));

        Map<Long, String> answers = new HashMap<Long, String>();
        answers.put(1L, "A");     // 对：10
        answers.put(2L, "AB");    // 多选少选：0
        answers.put(3L, "B");     // 对：10
        // 第 4 题不传 = 未作答：0

        GradeResult result = calculator.grade(paper, answers);

        assertEquals(0, new BigDecimal("20.0").compareTo(result.getTotalScore()), "应得 20 分");
        assertEquals(0, new BigDecimal("50.0").compareTo(result.getFullScore()), "试卷满分应为 50 分");
        assertEquals(2, result.getCorrectCount());
        assertEquals(4, result.getTotalCount(), "未作答的题也要计入总题数");
        assertEquals(4, result.getItems().size(), "每道题都要有一条判分明细，成绩单不能缺题");
    }

    @Test
    @DisplayName("判分-整卷：及格判定用 compareTo，60.0 分应判为及格（等价于 60）")
    void gradeResult_passJudgement_usesCompareToNotEquals() {
        List<Question> paper = new ArrayList<Question>();
        paper.add(single(1L, "A", "60.0"));

        Map<Long, String> answers = new HashMap<Long, String>();
        answers.put(1L, "A");

        GradeResult result = calculator.grade(paper, answers);
        assertEquals(0, new BigDecimal("60.0").compareTo(result.getTotalScore()));

        assertTrue(result.isPassed(new BigDecimal("60")),
            "BigDecimal 用 equals 比较会因精度不同判 false，必须用 compareTo");
        assertFalse(result.isPassed(new BigDecimal("60.5")));
        assertFalse(result.isPassed(null), "及格线缺失时应判不及格而不是抛异常");
    }

    /* ------------------------------------------------------------------ */
    /* 排序白名单：ORDER BY 注入防护                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("安全-题库排序：非法排序字段与方向一律回落到默认值")
    void questionQuery_sortWhitelistBlocksInjection() {
        QuestionQuery evil = new QuestionQuery();
        evil.setSortBy("id; DROP TABLE question--");
        evil.setSortDir("asc, (SELECT 1)");
        assertEquals("created_at", evil.getSafeSortBy(), "非法排序字段必须回落到默认值");
        assertEquals("desc", evil.getSafeSortDir(), "非法排序方向必须回落到默认值");

        QuestionQuery ok = new QuestionQuery();
        ok.setSortBy("score");
        ok.setSortDir("ASC");
        assertEquals("score", ok.getSafeSortBy(), "白名单内的字段应放行");
        assertEquals("asc", ok.getSafeSortDir());

        QuestionQuery huge = new QuestionQuery();
        huge.setPageSize(99999);
        assertEquals(50, huge.getSafePageSize(), "每页条数上限为 50");
        huge.setPageNum(3);
        huge.setPageSize(10);
        assertEquals(20, huge.getOffset(), "offset = (页码 - 1) × 每页条数");
    }

    @Test
    @DisplayName("安全-成绩排序：非法排序字段回落，越界分页被收敛")
    void examRecordQuery_sortWhitelistBlocksInjection() {
        ExamRecordQuery evil = new ExamRecordQuery();
        evil.setSortBy("score; DELETE FROM exam_record");
        evil.setSortDir("desc nulls first");
        assertEquals("submit_time", evil.getSafeSortBy());
        assertEquals("desc", evil.getSafeSortDir());

        ExamRecordQuery ok = new ExamRecordQuery();
        ok.setSortBy("score");
        assertEquals("score", ok.getSafeSortBy());

        ExamRecordQuery huge = new ExamRecordQuery();
        huge.setPageSize(100000);
        assertEquals(100, huge.getSafePageSize());
    }

    @Test
    @DisplayName("领域-考试记录：截止时间与及格判定由领域方法统一给出")
    void examRecord_deadlineAndPassedFlag() {
        ExamRecord record = new ExamRecord();
        record.setStartTime(new Date(1_700_000_000_000L));
        record.setDurationMinutes(60);
        record.setScore(new BigDecimal("72.0"));
        record.setPassScore(new BigDecimal("60.0"));
        record.setPaperTotalScore(new BigDecimal("100.0"));

        assertEquals(1_700_000_000_000L + 60L * 60L * 1000L, record.getDeadline().getTime(),
            "截止时刻 = 开考时间 + 考试时长");
        assertEquals(Long.valueOf(record.getDeadline().getTime()), record.getDeadlineMillis());

        assertFalse(record.isPassed(), "未交卷不能算及格");
        record.setStatus(ExamRecord.STATUS_SUBMITTED);
        assertTrue(record.isPassed(), "72 分 >= 及格线 60 分");
        assertEquals(72, record.getScorePercent(), "72 / 100 = 72%");
    }
}
