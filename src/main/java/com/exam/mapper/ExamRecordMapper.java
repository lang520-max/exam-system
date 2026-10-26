package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.ExamRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 考试记录 Mapper
 */
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /**
     * 条件更新交卷（防重复交卷核心）
     * 只有状态为 ONGOING(0) 才能改为目标状态
     */
    @Update("UPDATE exam_record SET status = #{newStatus}, end_time = NOW(), " +
            "score = #{score} WHERE id = #{id} AND status = #{oldStatus}")
    int compareAndSetSubmit(@Param("id") Long id,
                            @Param("score") Integer score,
                            @Param("oldStatus") int oldStatus,
                            @Param("newStatus") int newStatus);

    /**
     * 判分收尾的条件更新：写总分、推进状态，<b>但不动交卷时间</b>。
     *
     * <p>与 {@link #compareAndSetSubmit} 的唯一区别就是 {@code end_time}：
     * 那条语句的语义是「学生按下交卷」，把 {@code end_time} 置为 {@code NOW()} 是对的；
     * 而判分收尾<b>不是学生的动作</b>，沿用它等于把交卷时间改写成「判分发生的时刻」。</p>
     *
     * <p><b>这个差别在真实场景里会露出很明显的破绽</b>：AI 关闭时，含主观题的卷子
     * 会一直停在「待判分」（等教师复核），而补偿任务
     * {@code ExamService.sweepStuckPendingGradeRecords()} 每轮重判都会走到这里。
     * 若沿用 {@code compareAndSetSubmit}，这样一条记录<b>每被重判一次，
     * 交卷时间就往后推一次</b> —— 成绩单上的「用时」于是变成「从开考到现在」。
     * 2026-09-18 在演示库上实测到：真实用时 56 分钟的两条记录被刷成
     * 1123 / 1114 分钟（前一轮会话把这个现象记成「补偿扫描的坑」，
     * 根因其实在这里）。</p>
     *
     * <p>{@code COALESCE} 保留兜底：确实没有交卷时间时（人为修过的历史数据）
     * 仍然补一个 {@code NOW()}，免得「用时」永远为空。</p>
     */
    @Update("UPDATE exam_record SET status = #{newStatus}, score = #{score}, " +
            "end_time = COALESCE(end_time, NOW()) WHERE id = #{id} AND status = #{oldStatus}")
    int compareAndSetGradingResult(@Param("id") Long id,
                                   @Param("score") Integer score,
                                   @Param("oldStatus") int oldStatus,
                                   @Param("newStatus") int newStatus);

    /**
     * 只改状态的条件更新（不动 end_time / score）。
     *
     * <p>与 {@link #compareAndSetSubmit} 的区别：那条语句会把 {@code end_time} 置为
     * {@code NOW()}，语义是「学生提交了答卷」。定时清理超时记录<b>并不是学生提交</b>，
     * 复用那条语句会凭空写上一个假的交卷时间。</p>
     */
    @Update("UPDATE exam_record SET status = #{newStatus} WHERE id = #{id} AND status = #{oldStatus}")
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("oldStatus") int oldStatus,
                            @Param("newStatus") int newStatus);

    /**
     * 教师批改后重算：直接写入总分与状态。
     *
     * <p>不沿用 {@link #compareAndSetSubmit}：那条语句的条件是
     * {@code status = 待判分}，而教师要能改<b>已经出分</b>的卷子
     * （例如 AI 给了分但教师认为不对），此时状态是「已出分」，
     * 条件更新会静默不生效——分数改了、总分却没变，是最难查的一类 bug。</p>
     */
    @Update("UPDATE exam_record SET score = #{score}, status = #{status} WHERE id = #{id}")
    int updateScoreAndStatus(@Param("id") Long id,
                             @Param("score") Integer score,
                             @Param("status") Integer status);

    /**
     * 查出「已超过考试时长、但仍停留在考试中」的记录 ID。
     *
     * <p>截止时刻由 {@code start_time + exam.duration} <b>在 SQL 里算出</b>，
     * 而不是把全部记录捞进内存再逐条比较——后者在记录量大时会把整张表读进 JVM。</p>
     *
     * <p>{@code TIMESTAMPADD} 在 MySQL 8 与 H2 上都支持，因此同一条语句
     * 在 prod（MySQL）与 dev/test（H2）行为一致。</p>
     *
     * <p><b>宽限秒数由调用方传入</b>（{@code ExamService.SUBMIT_GRACE_SECONDS}）：
     * 交卷允许超时若干秒内仍然有效，这里若一到 deadline 就扫成超时，
     * 学生在宽限期内交卷会收到 409「考试已超时」、答案全部作废 ——
     * 宽限期就形同虚设。宽限值只写在 Java 一处，避免 SQL 里再抄一个 30。</p>
     */
    @Select("SELECT r.id FROM exam_record r JOIN exam e ON e.id = r.exam_id " +
            "WHERE r.status = 0 " +
            "AND r.start_time IS NOT NULL " +
            "AND e.duration IS NOT NULL " +
            "AND TIMESTAMPADD(SECOND, #{graceSeconds}, " +
            "        TIMESTAMPADD(MINUTE, e.duration, r.start_time)) < NOW()")
    List<Long> selectExpiredOngoingIds(@Param("graceSeconds") long graceSeconds);

    /**
     * 查出「已交卷、但长时间停在待判分」的记录 ID（兜底重判用）。
     *
     * <p>用 {@code end_time} 作为「交卷时刻」：{@link #compareAndSetSubmit} 在交卷时
     * 会写入它，而超时清理那条语句刻意不写（超时记录状态是 3，也不在这个查询范围里）。</p>
     *
     * <p>{@code status = 1} 直接写成字面量而不是引用常量：注解里的值必须是编译期常量，
     * 且 SQL 与 Java 常量之间跨引擎（H2/MySQL）都成立。</p>
     */
    @Select("SELECT id FROM exam_record " +
            "WHERE status = 1 " +
            "AND end_time IS NOT NULL " +
            "AND end_time < TIMESTAMPADD(SECOND, -#{stuckSeconds}, NOW())")
    List<Long> selectStuckPendingGradeIds(@Param("stuckSeconds") long stuckSeconds);

    /**
     * 统计某场考试的成绩。
     *
     * <p><b>「已交卷」口径是 {@code status IN (1, 2)} 而不是 {@code status >= 2}</b>：
     * PENDING_GRADE(1) 与 GRADED(2) 才代表学生真的交了卷；
     * TIMEOUT(3) 是「超时未交卷」，把它算进已交卷人数会虚增完成率。</p>
     *
     * <p>列别名写成下划线风格，由 MyBatis 的「下划线转驼峰」映射到
     * {@link com.exam.dto.ExamRecordStats} 的属性上，避免依赖列标签大小写。</p>
     */
    @Select("SELECT COUNT(*) AS total_count, " +
            "COALESCE(SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END), 0) AS submitted_count, " +
            "COALESCE(AVG(CASE WHEN status IN (1, 2) THEN score END), 0) AS avg_score, " +
            "COALESCE(MAX(CASE WHEN status IN (1, 2) THEN score END), 0) AS max_score, " +
            "COALESCE(MIN(CASE WHEN status IN (1, 2) THEN score END), 0) AS min_score " +
            "FROM exam_record WHERE exam_id = #{examId}")
    com.exam.dto.ExamRecordStats statsByExamId(@Param("examId") Long examId);

    /**
     * 统计及格人数。
     *
     * <p>限定 {@code status = 2}（已出分）：超时记录分数为 null，
     * 本来就不会被 {@code score >= passScore} 命中，这里写明是为了让口径一目了然。</p>
     */
    @Select("SELECT COUNT(*) FROM exam_record WHERE exam_id = #{examId} " +
            "AND status = 2 AND score >= #{passScore}")
    int countPassed(@Param("examId") Long examId, @Param("passScore") Integer passScore);
}