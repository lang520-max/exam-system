package com.exam.dto.response;

import com.exam.entity.Exam;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生视角的考试视图。
 *
 * <p><b>为什么不能直接返回 {@link Exam} 实体</b>：实体的
 * {@code question_snapshot} 是「这份卷子考哪几道题、每题多少分」的完整清单，
 * {@code rule_config} 则是组卷规则。它们对教师是必要信息，
 * 对考生却是<b>提前泄题</b>——学生只要在开考前调一次
 * {@code GET /api/exams/{id}} 就能拿到卷面构成。</p>
 *
 * <p>字段与实体一一对应，只剔除上面两个字段，其余（含
 * {@code published}/{@code inProgress}/{@code finished} 三个派生字段）
 * 保持不变，学生端页面无需改动。</p>
 */
@Data
public class ExamVO {

    private Long id;

    private String title;

    private String description;

    private Long bankId;

    /** 考试时长(分钟) */
    private Integer duration;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /**
     * 试卷类型（V4）：模拟卷 / 专项训练。
     *
     * <p>学生端也要显示 —— 「这是专项训练还是整套模拟卷」直接决定了学生怎么用它
     * （专项用来补短板，模拟卷用来掐时间）。它不含任何题目信息，不属于需要剔除的字段。</p>
     */
    private String paperType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0-未发布 1-进行中 2-已结束 */
    private Integer status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 派生字段：与实体同名，前端已有逻辑依赖 */
    private boolean published;

    private boolean inProgress;

    private boolean finished;

    public static ExamVO from(Exam exam) {
        if (exam == null) {
            return null;
        }
        ExamVO vo = new ExamVO();
        vo.setId(exam.getId());
        vo.setTitle(exam.getTitle());
        vo.setDescription(exam.getDescription());
        vo.setBankId(exam.getBankId());
        vo.setDuration(exam.getDuration());
        vo.setTotalScore(exam.getTotalScore());
        vo.setPassScore(exam.getPassScore());
        vo.setPaperType(exam.getPaperType());
        vo.setStartTime(exam.getStartTime());
        vo.setEndTime(exam.getEndTime());
        vo.setStatus(exam.getStatus());
        vo.setCreatedBy(exam.getCreatedBy());
        vo.setCreatedAt(exam.getCreatedAt());
        vo.setUpdatedAt(exam.getUpdatedAt());
        vo.setPublished(exam.isPublished());
        vo.setInProgress(exam.isInProgress());
        vo.setFinished(exam.isFinished());
        return vo;
    }
}