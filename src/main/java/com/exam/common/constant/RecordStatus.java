package com.exam.common.constant;

/**
 * 考试记录状态
 */
public interface RecordStatus {

    /** 考试中 */
    int ONGOING = 0;
    /** 待判分 */
    int PENDING_GRADE = 1;
    /** 已出分 */
    int GRADED = 2;
    /** 超时 */
    int TIMEOUT = 3;

    static String getLabel(int status) {
        switch (status) {
            case ONGOING: return "考试中";
            case PENDING_GRADE: return "待判分";
            case GRADED: return "已出分";
            case TIMEOUT: return "超时";
            default: return "未知";
        }
    }
}