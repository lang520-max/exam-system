package com.exam.common.constant;

/**
 * 考试状态常量
 */
public interface ExamStatus {

    /** 未发布 */
    int DRAFT = 0;
    /** 进行中 */
    int IN_PROGRESS = 1;
    /** 已结束 */
    int FINISHED = 2;

    static String getLabel(int status) {
        switch (status) {
            case DRAFT: return "未发布";
            case IN_PROGRESS: return "进行中";
            case FINISHED: return "已结束";
            default: return "未知";
        }
    }
}