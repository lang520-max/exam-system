package com.caikailang.examsystem.mapper;

import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.dto.QuestionQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题库持久层。
 *
 * <p>本接口的<b>全部</b>方法都在 {@code resources/mapper/QuestionMapper.xml} 中实现。
 * 采用「一个 Mapper 要么全注解、要么全 XML」的约定，避免混用时漏写 statement，
 * 运行期抛 {@code BindingException: Invalid bound statement (not found)}。</p>
 *
 * @author 蔡开浪
 */
public interface QuestionMapper {

    int insert(Question question);

    int update(Question question);

    int deleteById(@Param("id") Long id);

    Question findById(@Param("id") Long id);

    /** 动态检索：关键字 / 科目 / 题型 / 难度 / 创建人 + 白名单排序 + 分页 */
    List<Question> search(QuestionQuery query);

    long countByQuery(QuestionQuery query);

    /**
     * 随机抽题：按科目 + 题型抽 {@code limit} 道题的 ID。
     *
     * <p>SQL 用 {@code ORDER BY RAND()}（MySQL 与 H2 兼容），抽题结果在数据库侧决定，
     * 不需要把整个题库拉回 JVM 再打乱。</p>
     */
    List<Long> findRandomIds(@Param("subject") String subject,
                             @Param("type") String type,
                             @Param("limit") int limit);

    /** 按 ID 批量取题（组卷时按勾选的题目批量载入，避免 N+1 查询） */
    List<Question> findByIds(@Param("ids") List<Long> ids);

    /** 题库中已存在的科目清单，供页面下拉框与随机组卷选择 */
    List<String> listSubjects();
}
