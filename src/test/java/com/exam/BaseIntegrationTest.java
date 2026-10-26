package com.exam;

import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.entity.User;
import com.exam.mapper.QuestionBankMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试基类。
 *
 * <p>跑真实的 Spring 容器 + H2（MODE=MySQL）+ MyBatis-Plus。
 * 每个测试方法都包在事务里并自动回滚，因此测试之间互不污染，
 * 也不需要手写清理 SQL。</p>
 *
 * <p>Redis 与 RabbitMQ 在 test 档被关闭（见 application-test.yml）：
 * 判分走 {@code GradingEngine} 同步路径，幂等走数据库条件更新，
 * 因此 {@code mvn test} 不需要任何中间件就能全绿。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected QuestionBankMapper bankMapper;

    @Autowired
    protected QuestionMapper questionMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User teacher;
    protected User student;
    protected QuestionBank bank;

    @BeforeEach
    void initBaseFixtures() {
        teacher = createUser("t_" + unique(), "TEACHER", 1);
        student = createUser("s_" + unique(), "STUDENT", 2);
        bank = createBank("题库_" + unique(), teacher.getId());
    }

    /** 用递增计数保证同一测试类内多次调用生成不同名字 */
    private static final java.util.concurrent.atomic.AtomicInteger SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    protected static String unique() {
        return String.valueOf(SEQ.incrementAndGet());
    }

    protected User createUser(String username, String realName, int role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return userMapper.selectById(user.getId());
    }

    protected QuestionBank createBank(String name, Long createdBy) {
        QuestionBank b = new QuestionBank();
        b.setName(name);
        b.setSubject(name);
        b.setCreatedBy(createdBy);
        bankMapper.insert(b);
        return bankMapper.selectById(b.getId());
    }

    /**
     * 造一道题。
     *
     * @param type       题型
     * @param difficulty 难度 1/2/3
     * @param content    题干
     * @param answer     标准答案
     * @param score      分值
     */
    protected Question createQuestion(Long bankId, int type, int difficulty,
                                      String content, String answer, int score) {
        Question q = new Question();
        q.setBankId(bankId);
        q.setType(type);
        q.setDifficulty(difficulty);
        q.setContent(content);
        q.setOptions("[{\"label\":\"A\",\"content\":\"选项A\"},{\"label\":\"B\",\"content\":\"选项B\"},"
                + "{\"label\":\"C\",\"content\":\"选项C\"},{\"label\":\"D\",\"content\":\"选项D\"}]");
        q.setAnswer(answer);
        q.setScore(score);
        q.setStatus(1);
        q.setCreatedBy(teacher.getId());
        questionMapper.insert(q);
        return questionMapper.selectById(q.getId());
    }
}
