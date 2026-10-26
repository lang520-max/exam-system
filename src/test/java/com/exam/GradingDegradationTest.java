package com.exam;

import com.exam.common.constant.QuestionType;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static com.exam.common.constant.RecordStatus.GRADED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 判分降级：关掉 RabbitMQ 时，交卷必须【同步判分】。
 *
 * <p>背景（2026-09-16 在 2 核 2 GiB 云主机上实测发现）：{@code ExamService} 原先用
 * {@code rabbitTemplate != null} 判断要不要走 MQ，而 Spring Boot 的
 * RabbitAutoConfiguration 只要 classpath 上有 spring-boot-starter-amqp 就一定会
 * 提供 RabbitTemplate —— 于是「关掉 MQ 就同步判分」这条降级路径在真实部署里
 * 永远不生效：交卷照旧往 MQ 投消息、broker 不存在、只打一行 ERROR，
 * 客观题成绩要等每 2 分钟一次的「待判分补偿任务」才出来。
 *
 * <p>为什么原来的 262 个测试没抓到：test 档用 {@code spring.autoconfigure.exclude}
 * 排除了 RabbitAutoConfiguration，容器里没有 RabbitTemplate bean，
 * 于是走的是同步路径 —— 测试环境的 bean 拓扑与生产【不一致】，
 * 恰好把 bug 盖住了。本测试用 {@link MockBean} 把这个 bean 补回来复现生产拓扑。</p>
 */
@DisplayName("判分降级：RabbitMQ 关掉时必须同步判分")
class GradingDegradationTest extends BaseIntegrationTest {

    /**
     * ★ 这个 mock 是本测试类存在的全部理由：把生产拓扑里的 RabbitTemplate bean 补回来。
     * 若 ExamService 退回用「bean 是否存在」当判据，下面的断言会立刻失败。
     */
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRuleService examRuleService;
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private ExamRecordMapper recordMapper;

    @Test
    @DisplayName("app.rabbitmq.enabled=false：交卷立即出分，且完全不碰 RabbitTemplate")
    void gradesSynchronouslyWhenRabbitDisabled() {
        Question q1 = createQuestion(bank.getId(), QuestionType.SINGLE, 1, "单选1", "A", 10);
        Question q2 = createQuestion(bank.getId(), QuestionType.SINGLE, 2, "单选2", "B", 10);

        ExamGenerateDTO form = new ExamGenerateDTO();
        form.setTitle("降级测试卷_" + unique());
        form.setBankId(bank.getId());
        form.setDuration(60);
        form.setPassScore(12);
        form.setRules(List.of(rule(QuestionType.SINGLE, 2, 10)));
        Exam exam = examRuleService.generateExam(form, teacher.getId());
        exam.setStatus(1);
        examMapper.updateById(exam);

        ExamRecord record = examService.startExam(exam.getId(), student.getId(), "127.0.0.1");

        ExamSubmitDTO submit = new ExamSubmitDTO();
        submit.setRecordId(record.getId());
        submit.setAnswers(Map.of(q1.getId(), "A", q2.getId(), "B"));
        examService.submitExam(submit, student.getId(), "req-" + unique());

        ExamRecord after = recordMapper.selectById(record.getId());
        // 关键断言：交卷后立刻是「已出分」，而不是「待判分」等补偿任务
        assertThat(after.getStatus()).isEqualTo(GRADED);
        assertThat(after.getScore()).isEqualTo(20);
        // 而且压根没有向 MQ 投递过（投了就会像真机那样报「判分消息投递失败」）
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    private ExamGenerateDTO.RuleItem rule(int type, int count, int score) {
        ExamGenerateDTO.RuleItem r = new ExamGenerateDTO.RuleItem();
        r.setType(type);
        r.setCount(count);
        r.setScore(score);
        return r;
    }
}