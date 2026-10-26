package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.Question;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目 Mapper
 */
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 抽题计数：题型 + **可选的**难度 + **可选的知识点**（V4）。
     *
     * <p>难度与知识点传 {@code null} 表示「不限」——组卷规则里知识点是可选项，
     * 而按难度分配名额时又要分三次数，所以用一条动态 SQL 覆盖全部组合，
     * 避免「按难度抽」与「按专题抽」变成两套会各自漂移的代码。</p>
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM question WHERE bank_id = #{bankId} AND type = #{type} AND status = 1" +
            "<if test='difficulty != null'> AND difficulty = #{difficulty}</if>" +
            "<if test='category != null'> AND category = #{category}</if>" +
            "</script>")
    int countForPick(@Param("bankId") Long bankId,
                     @Param("type") Integer type,
                     @Param("difficulty") Integer difficulty,
                     @Param("category") String category);

    /**
     * 抽题：题型 + 难度（可选）+ 知识点（可选），随机取 limit 道。
     */
    @Select("<script>" +
            "SELECT id FROM question WHERE bank_id = #{bankId} AND type = #{type} AND status = 1" +
            "<if test='difficulty != null'> AND difficulty = #{difficulty}</if>" +
            "<if test='category != null'> AND category = #{category}</if>" +
            " ORDER BY RAND() LIMIT #{limit}" +
            "</script>")
    List<Long> selectRandomForPick(@Param("bankId") Long bankId,
                                   @Param("type") Integer type,
                                   @Param("difficulty") Integer difficulty,
                                   @Param("category") String category,
                                   @Param("limit") int limit);

    /**
     * 自由练习抽题：题库 / 题型 / 难度 / **知识点**都可选，随机取 limit 道，只取启用中的题。
     *
     * <p>四个筛选项允许为 null，所以用 {@code <script>} 动态拼条件（而不是字符串拼接）：
     * 这些参数来自页面上用户的选择，必须保持参数化。</p>
     */
    @Select("<script>" +
            "SELECT * FROM question WHERE status = 1" +
            "<if test='bankId != null'> AND bank_id = #{bankId}</if>" +
            "<if test='type != null'> AND type = #{type}</if>" +
            "<if test='difficulty != null'> AND difficulty = #{difficulty}</if>" +
            "<if test='category != null'> AND category = #{category}</if>" +
            " ORDER BY RAND() LIMIT #{limit}" +
            "</script>")
    List<Question> selectRandomForPractice(@Param("bankId") Long bankId,
                                           @Param("type") Integer type,
                                           @Param("difficulty") Integer difficulty,
                                           @Param("category") String category,
                                           @Param("limit") int limit);

    /**
     * 各题库「启用中的题目数」——练习页的题库下拉要显示「高等数学题库（100 题）」。
     *
     * <p>别名写成下划线风格并由强类型 DTO 承接：H2 会把列标签转成大写，
     * 用 {@code Map} 接会拿到 null（本项目早期踩过这个坑）。</p>
     */
    @Select("SELECT bank_id AS bank_id, COUNT(*) AS total FROM question " +
            "WHERE status = 1 GROUP BY bank_id")
    List<com.exam.dto.BankQuestionCount> countByBank();

    /**
     * 各知识点的启用题目数（V4）——专题下拉的数据源。
     *
     * @param bankId 可选，null 表示全部题库
     */
    @Select("<script>" +
            "SELECT category, COUNT(*) AS total FROM question" +
            " WHERE status = 1 AND category IS NOT NULL AND category != ''" +
            "<if test='bankId != null'> AND bank_id = #{bankId}</if>" +
            " GROUP BY category ORDER BY category" +
            "</script>")
    List<com.exam.dto.CategoryCount> countByCategory(@Param("bankId") Long bankId);

    /**
     * 批量查询题目（用于组卷后获取完整题目信息）
     */
    @Select("<script>" +
            "SELECT * FROM question WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Question> selectBatchIds(@Param("ids") List<Long> ids);
}