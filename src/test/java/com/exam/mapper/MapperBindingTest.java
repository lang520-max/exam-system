package com.exam.mapper;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis 映射自检 —— 确保每个 Mapper 方法都有对应的 SQL。
 *
 * <p>这类测试价值极高但成本几乎为零：它遍历所有 Mapper 接口的每一个方法，
 * 到 MyBatis 的 Configuration 里查是否有对应的 mapped statement。
 * 只要有人加了方法忘了写 SQL / 注解，{@code mvn test} 立刻失败，
 * 而不是等到线上被点到才抛 {@code BindingException}。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MyBatis Mapper 映射自检")
class MapperBindingTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private Configuration configuration() {
        return sqlSessionFactory.getConfiguration();
    }

    @Test
    @DisplayName("所有 Mapper 接口方法都有对应的 mapped statement")
    void everyMapperMethodHasStatement() {
        Configuration configuration = configuration();
        Class<?>[] mappers = {
                UserMapper.class,
                QuestionBankMapper.class,
                QuestionMapper.class,
                ExamMapper.class,
                ExamRecordMapper.class,
                StudentAnswerMapper.class
        };

        for (Class<?> mapper : mappers) {
            String namespace = mapper.getName();
            // 确认这个 namespace 已经被 MyBatis 注册
            assertThat(configuration.hasMapper(mapper))
                    .as("Mapper 未被注册: %s", namespace)
                    .isTrue();

            for (java.lang.reflect.Method method : mapper.getDeclaredMethods()) {
                // 跳过接口默认方法、Object 继承方法
                if (method.isDefault() || method.isSynthetic()) {
                    continue;
                }
                String statementId = namespace + "." + method.getName();
                assertThat(configuration.hasStatement(statementId))
                        .as("方法 %s 没有对应的 SQL / 注解映射", statementId)
                        .isTrue();
            }
        }
    }

    /**
     * 反方向：XML 里不能有接口中不存在的方法名（拼写错误）。
     * XML 中的 statement id 格式为 namespace.methodName。
     * 这里只做一阶校验：通过 Configuration 里的 mapped statement 反查接口方法存在性。
     */
    @Test
    @DisplayName("XML 中不存在接口里未声明的方法名（防拼写错误）")
    void noOrphanStatements() {
        Configuration configuration = configuration();
        java.util.Set<String> mapperNames = java.util.Set.of(
                UserMapper.class.getName(),
                QuestionBankMapper.class.getName(),
                QuestionMapper.class.getName(),
                ExamMapper.class.getName(),
                ExamRecordMapper.class.getName(),
                StudentAnswerMapper.class.getName()
        );

        for (Object candidate : configuration.getMappedStatements()) {
            // getMappedStatements() 里混有 MyBatis 内部的 Ambiguity 占位对象
            // （短名冲突时使用），必须先按类型过滤再取 id
            if (!(candidate instanceof org.apache.ibatis.mapping.MappedStatement stmt)) {
                continue;
            }
            String[] parts = stmt.getId().split("\\.", 2);
            if (parts.length < 2 || !mapperNames.contains(parts[0])) {
                continue;
            }
            try {
                Class<?> mapper = Class.forName(parts[0]);
                mapper.getDeclaredMethod(parts[1]);
            } catch (NoSuchMethodException e) {
                throw new AssertionError("XML 中声明的 statement " + stmt.getId()
                        + " 在 Mapper 接口中找不到对应方法", e);
            } catch (ClassNotFoundException ignored) {
            }
        }
    }
}