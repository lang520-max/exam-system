package com.caikailang.examsystem.mapper;

import com.caikailang.examsystem.domain.ExamPaperQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 试卷题目关联持久层。
 *
 * <p>语句简单，全部用注解实现，因此没有对应的 XML 文件。</p>
 *
 * @author 蔡开浪
 */
public interface ExamPaperQuestionMapper {

    @Insert("INSERT INTO exam_paper_question (paper_id, question_id, score, sort_order) "
        + "VALUES (#{paperId}, #{questionId}, #{score}, #{sortOrder})")
    int insert(ExamPaperQuestion paperQuestion);

    /**
     * 取一份试卷的全部题目（含题干与选项快照），按卷面顺序排列。
     *
     * <p>一次 JOIN 取回，考试页与判分逻辑都直接用它，避免逐题查询造成 N+1。</p>
     */
    @Select("SELECT pq.id, pq.paper_id, pq.question_id, pq.score, pq.sort_order, "
        + "q.type, q.subject, q.knowledge_point, q.content, "
        + "q.option_a, q.option_b, q.option_c, q.option_d, q.answer, q.difficulty "
        + "FROM exam_paper_question pq "
        + "JOIN question q ON pq.question_id = q.id "
        + "WHERE pq.paper_id = #{paperId} "
        + "ORDER BY pq.sort_order ASC, pq.id ASC")
    List<ExamPaperQuestion> findByPaperId(@Param("paperId") Long paperId);

    @Select("SELECT COUNT(*) FROM exam_paper_question WHERE paper_id = #{paperId}")
    int countByPaperId(@Param("paperId") Long paperId);

    /** 题目是否已被某份试卷引用：被引用的题目不允许从题库删除（外键保护 + 友好提示） */
    @Select("SELECT COUNT(*) FROM exam_paper_question WHERE question_id = #{questionId}")
    int countByQuestionId(@Param("questionId") Long questionId);

    @Delete("DELETE FROM exam_paper_question WHERE paper_id = #{paperId}")
    int deleteByPaperId(@Param("paperId") Long paperId);
}
