package com.caikailang.examsystem.mapper;

import com.caikailang.examsystem.domain.ExamAnswer;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 答题明细持久层（全部方法在 {@code resources/mapper/ExamAnswerMapper.xml} 中实现）。
 *
 * <p>判分结果一次性批量入库：一次考试可能有几十道题，
 * 用 {@code <foreach>} 拼一条多值 INSERT，比循环单条插入少几十次网络往返。</p>
 *
 * @author lang520-max
 */
public interface ExamAnswerMapper {

    /** 批量插入作答明细（交卷判分后调用） */
    int batchInsert(@Param("list") List<ExamAnswer> list);

    /** 一次考试的全部作答明细，按卷面顺序排列 */
    List<ExamAnswer> findByRecordId(@Param("recordId") Long recordId);

    /** 错题回顾：只取判为答错的题 */
    List<ExamAnswer> findWrongByRecordId(@Param("recordId") Long recordId);
}
