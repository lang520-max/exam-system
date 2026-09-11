package com.caikailang.examsystem;

import com.caikailang.examsystem.mapper.ExamAnswerMapper;
import com.caikailang.examsystem.mapper.ExamPaperMapper;
import com.caikailang.examsystem.mapper.ExamPaperQuestionMapper;
import com.caikailang.examsystem.mapper.ExamRecordMapper;
import com.caikailang.examsystem.mapper.QuestionMapper;
import com.caikailang.examsystem.mapper.UserMapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis 映射自检。
 *
 * <p>「Mapper 接口里声明了方法、XML（或注解）里却没有对应 statement」是 MyBatis 项目里
 * 最典型的运行期事故：编译能过、启动也不报错，只有真正调用到那个方法时才抛
 * {@code BindingException: Invalid bound statement (not found)}。</p>
 *
 * <p>这条用例遍历所有 Mapper 接口的<b>每一个</b>方法，逐个到 MyBatis 的
 * {@link Configuration} 里查有没有对应的 statement。只要有人加了方法忘了写 SQL，
 * 跑 {@code mvn test} 就会立刻失败，而不是等到上线后某个接口被点到才炸。</p>
 *
 * @author 蔡开浪
 */
@SpringBootTest
@ActiveProfiles("test")
class MapperBindingTest {

    /** 项目里全部 Mapper 接口，新增 Mapper 时记得登记在这里 */
    private static final Class<?>[] MAPPERS = {
        UserMapper.class,
        QuestionMapper.class,
        ExamPaperMapper.class,
        ExamPaperQuestionMapper.class,
        ExamRecordMapper.class,
        ExamAnswerMapper.class
    };

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("MyBatis 绑定：每个 Mapper 方法都有对应的 mapped statement，不会出现 Invalid bound statement")
    void everyMapperMethod_shouldHaveMappedStatement() {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        List<String> missing = new ArrayList<String>();
        int checked = 0;

        for (Class<?> mapper : MAPPERS) {
            assertTrue(mapper.isInterface(), mapper.getName() + " 应当是接口");

            for (Method method : mapper.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                checked++;
                String statementId = mapper.getName() + "." + method.getName();
                if (!configuration.hasStatement(statementId)) {
                    missing.add(statementId);
                }
            }
        }

        assertTrue(missing.isEmpty(),
            "以下 Mapper 方法没有对应的 SQL 语句，调用时会抛 BindingException: " + missing);
        assertTrue(checked >= 30,
            "应当扫描到全部 Mapper 方法（当前登记了 " + MAPPERS.length + " 个 Mapper），实际只扫到 " + checked + " 个");
    }

    @Test
    @DisplayName("MyBatis 绑定：反向检查，XML 里不存在没有接口方法对应的多余 statement")
    void mappedStatements_shouldAllBelongToKnownMapperMethods() throws Exception {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        List<String> orphan = new ArrayList<String>();
        int count = 0;

        for (Class<?> mapper : MAPPERS) {
            for (String name : configuration.getMappedStatementNames()) {
                if (!name.startsWith(mapper.getName() + ".")) {
                    continue;
                }
                count++;
                String methodName = name.substring(mapper.getName().length() + 1);
                if (!hasMethod(mapper, methodName)) {
                    orphan.add(name);
                }
            }
        }

        assertTrue(orphan.isEmpty(), "XML 里有接口中不存在的方法名，可能是拼写错误：" + orphan);
        assertTrue(count >= 30, "注册到 MyBatis 的 statement 数量偏少，实际 " + count);
    }

    private boolean hasMethod(Class<?> mapper, String methodName) {
        for (Method method : mapper.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
