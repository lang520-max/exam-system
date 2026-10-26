package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.dto.response.FaceLogVO;
import com.exam.entity.FaceVerifyLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人脸核验留痕 Mapper。
 *
 * <p>这里的查询是**开考门禁的判据**：{@code ExamService.startExam()} 会先调
 * {@link #countRecentPass} —— 查库而不是查内存，服务重启或将来多实例部署都不会失效。</p>
 */
public interface FaceVerifyLogMapper extends BaseMapper<FaceVerifyLog> {

    /**
     * 近期是否已核验通过（含教师人工放行）。
     *
     * <p>给一个时间窗（默认 5 分钟）是刻意的：核验通过后拖很久再开考，
     * 中途可能已经换了人，门禁的意义就没了。</p>
     */
    @Select("SELECT COUNT(*) FROM face_verify_log WHERE user_id = #{userId} AND exam_id = #{examId} " +
            "AND result IN ('PASS', 'OVERRIDE_PASS') AND created_at >= #{since}")
    int countRecentPass(@Param("userId") Long userId,
                        @Param("examId") Long examId,
                        @Param("since") LocalDateTime since);

    /**
     * 本次考试的人脸失败尝试次数（用于「重试 N 次后禁考」）。
     *
     * <p>只统计 FAIL / LIVENESS_FAIL —— **不统计 UNAVAILABLE**：
     * 厂商接口超时/欠费是运维问题，不该算成学生核验失败把人锁死；
     * 那种情况由「拦下 + 教师放行」处理。</p>
     */
    @Select("SELECT COUNT(*) FROM face_verify_log WHERE user_id = #{userId} AND exam_id = #{examId} " +
            "AND scene = 'EXAM_START' AND result IN ('FAIL', 'LIVENESS_FAIL')")
    int countFailedAttempts(@Param("userId") Long userId, @Param("examId") Long examId);

    /**
     * 最近一条核验记录的结果（用于给学生端显示「上次为什么没过」）。
     */
    @Select("SELECT * FROM face_verify_log WHERE user_id = #{userId} AND exam_id = #{examId} " +
            "ORDER BY id DESC LIMIT 1")
    FaceVerifyLog selectLatest(@Param("userId") Long userId, @Param("examId") Long examId);

    /**
     * 某场考试的人脸核验流水（教师端查看 + 一键放行用）。
     *
     * <p>列别名一律用下划线：H2 会把别名转成大写，用 camelCase 别名会静默取到 null
     * （见交接文档 §7.3）。</p>
     */
    @Select("SELECT l.id, l.user_id, u.username, u.real_name, l.exam_id, l.record_id, l.scene, " +
            "l.result, l.confidence, l.liveness, l.retry_no, l.ip_address, l.detail, l.operator_id, l.created_at " +
            "FROM face_verify_log l JOIN sys_user u ON u.id = l.user_id " +
            "WHERE l.exam_id = #{examId} ORDER BY l.id DESC LIMIT #{limit}")
    List<FaceLogVO> selectByExam(@Param("examId") Long examId, @Param("limit") int limit);
}