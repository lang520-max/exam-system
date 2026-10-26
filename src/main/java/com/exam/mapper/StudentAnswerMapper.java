package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.StudentAnswer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 学生答案 Mapper
 */
public interface StudentAnswerMapper extends BaseMapper<StudentAnswer> {

    /**
     * 批量插入答案
     */
    int batchInsert(@Param("list") List<StudentAnswer> list);

    /**
     * 查询某记录中的客观题答案
     */
    @Select("SELECT sa.* FROM student_answer sa " +
            "JOIN question q ON sa.question_id = q.id " +
            "WHERE sa.record_id = #{recordId} AND q.type IN (0,1,2)")
    List<StudentAnswer> selectObjectiveByRecordId(@Param("recordId") Long recordId);

    /**
     * 查询某记录中的主观题答案
     */
    @Select("SELECT sa.* FROM student_answer sa " +
            "JOIN question q ON sa.question_id = q.id " +
            "WHERE sa.record_id = #{recordId} AND q.type IN (3,4)")
    List<StudentAnswer> selectSubjectiveByRecordId(@Param("recordId") Long recordId);

    /**
     * 只覆盖作答文本（答题过程中的暂存、以及交卷时的最终答案）。
     *
     * <p>刻意不碰 {@code score / is_correct / graded_by}：这些是**评分结论**，
     * 只能由判分引擎或教师批改写入。暂存改到它们就等于「学生自己给自己打分」。</p>
     */
    @Update("UPDATE student_answer SET answer = #{answer} WHERE id = #{id}")
    int updateAnswerText(@Param("id") Long id, @Param("answer") String answer);

    /**
     * 写入评分结果（客观题自动判分 / 教师复核）。
     *
     * <p>{@code reviewed_at} 在这里表示「该题完成评分的时间」，三种评分来源都会写。</p>
     */
    @Update("UPDATE student_answer SET score = #{score}, is_correct = #{isCorrect}, " +
            "graded_by = #{gradedBy}, reviewed_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateScore(@Param("id") Long id,
                    @Param("score") Integer score,
                    @Param("isCorrect") Integer isCorrect,
                    @Param("gradedBy") String gradedBy);

    /**
     * 写入 AI 评分结果 —— 额外落库评分理由与所用模型。
     *
     * <p>理由要落库是因为它是学生唯一的「为什么得这个分」的解释；
     * 模型名落库是为了排查「换了模型同一份答案分数就变了」这类问题。</p>
     */
    @Update("UPDATE student_answer SET score = #{score}, is_correct = #{isCorrect}, " +
            "graded_by = 'AI', ai_reason = #{reason}, ai_model = #{model}, " +
            "reviewed_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateAiScore(@Param("id") Long id,
                      @Param("score") Integer score,
                      @Param("isCorrect") Integer isCorrect,
                      @Param("reason") String reason,
                      @Param("model") String model);

    /**
     * 教师复核评分 —— 额外记录复核人。
     *
     * <p>{@code graded_by} 写成 TEACHER，这样成绩单上能区分
     * 「AI 给的分」与「教师确认/改过的分」。</p>
     */
    @Update("UPDATE student_answer SET score = #{score}, is_correct = #{isCorrect}, " +
            "graded_by = 'TEACHER', reviewed_by = #{reviewedBy}, reviewed_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateTeacherScore(@Param("id") Long id,
                           @Param("score") Integer score,
                           @Param("isCorrect") Integer isCorrect,
                           @Param("reviewedBy") Long reviewedBy);

    /**
     * 客观题批量写分 —— 一次 UPDATE 覆盖整卷客观题（SQL 见 StudentAnswerMapper.xml）。
     *
     * <p>为什么不用逐题 {@link #updateScore}：一份卷子几十道客观题就是几十次数据库往返，
     * 而这些往返全都发生在判分事务内，直接放大了事务持有时长（连接池压力正是由此而来）。
     * 用 {@code CASE id WHEN ... THEN ...} 而不是把语句拆成多条：各题得分不同，
     * 一条语句既只走一次往返，又天然原子 —— 不会出现「前一半写了、后一半没写」的中间态。</p>
     */
    int batchUpdateGradedScores(@Param("list") List<StudentAnswer> list);

    /**
     * AI 评分批量写分 —— 与 {@link #batchUpdateGradedScores} 同理，
     * 额外写入评分理由 {@code ai_reason} 与所用模型 {@code ai_model}。
     */
    int batchUpdateAiScores(@Param("list") List<StudentAnswer> list);

    /**
     * 某份答卷里尚未评分的题数。
     *
     * <p>判据是 {@code graded_by IS NULL} 而不是分数是否为 0：
     * 「0 分」是一个评分结论，而「未评分」没有结论，两者不能混为一谈。</p>
     */
    @Select("SELECT COUNT(*) FROM student_answer WHERE record_id = #{recordId} AND graded_by IS NULL")
    int countPendingByRecordId(@Param("recordId") Long recordId);

    /**
     * 汇总得分
     */
    @Select("SELECT COALESCE(SUM(score), 0) FROM student_answer WHERE record_id = #{recordId}")
    int sumScoreByRecordId(@Param("recordId") Long recordId);
}