package com.caikailang.examsystem.mapper;

import com.caikailang.examsystem.domain.ExamPaper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 试卷持久层（全部方法在 {@code resources/mapper/ExamPaperMapper.xml} 中实现）。
 *
 * @author lang520-max
 */
public interface ExamPaperMapper {

    int insert(ExamPaper paper);

    int deleteById(@Param("id") Long id);

    ExamPaper findById(@Param("id") Long id);

    /** 教师端试卷列表：按状态 / 创建人筛选 */
    List<ExamPaper> listPapers(@Param("status") String status,
                               @Param("creatorId") Long creatorId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countPapers(@Param("status") String status, @Param("creatorId") Long creatorId);

    /**
     * 学生端可参加的考试列表：已发布的试卷，并顺带带出该学生在这份试卷上的考试记录，
     * 前端据此显示「开始考试 / 继续答题 / 已交卷 85 分」。
     */
    List<ExamPaper> listPublishedForStudent(@Param("studentId") Long studentId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    long countPublished();

    /** 更新试卷状态（草稿 → 发布 → 结束） */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
