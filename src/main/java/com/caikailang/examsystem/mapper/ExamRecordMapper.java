package com.caikailang.examsystem.mapper;

import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.ExamStats;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 考试记录持久层（全部方法在 {@code resources/mapper/ExamRecordMapper.xml} 中实现）。
 *
 * <p>因为 {@code exam_record} 含 {@code is_timeout} 这类「列名与属性名不同名」的布尔列，
 * XML 里用了显式 {@code <resultMap>}，不依赖自动映射，避免列名歧义。</p>
 *
 * @author 蔡开浪
 */
public interface ExamRecordMapper {

    int insert(ExamRecord record);

    /** 单条详情（含试卷标题、及格分、时长等展示字段） */
    ExamRecord findById(@Param("id") Long id);

    /** 该学生在这份试卷上的考试记录（不论是否交卷），用于「已考过不允许重复开考」 */
    ExamRecord findByPaperAndStudent(@Param("paperId") Long paperId, @Param("studentId") Long studentId);

    List<ExamRecord> findByQuery(ExamRecordQuery query);

    long countByQuery(ExamRecordQuery query);

    /**
     * 交卷的条件更新（Compare-And-Set）：
     * {@code UPDATE exam_record SET status='SUBMITTED', ... WHERE id = ? AND status = 'ONGOING'}。
     *
     * <p>返回受影响行数：0 表示这条记录已被其他请求交过卷，调用方据此拒绝重复计分。
     * 依赖数据库行锁保证原子性，比「先查后改」的写法在并发下安全。</p>
     */
    int compareAndSetSubmit(ExamRecord record);

    /** 某场考试的成绩统计：交卷人数、及格人数、平均/最高/最低分 */
    ExamStats statsByPaper(@Param("paperId") Long paperId, @Param("passScore") BigDecimal passScore);

    /** 某个学生的历史成绩统计（跨所有试卷） */
    ExamStats statsByStudent(@Param("studentId") Long studentId);

    /** 试卷是否已有考试记录，用于拦截「已有学生考过就删卷」 */
    long countByPaper(@Param("paperId") Long paperId);
}
