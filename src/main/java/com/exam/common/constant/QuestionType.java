package com.exam.common.constant;

/**
 * 题型常量
 */
public interface QuestionType {

    /** 单选题 */
    int SINGLE = 0;
    /** 多选题 */
    int MULTIPLE = 1;
    /** 判断题 */
    int JUDGE = 2;
    /** 填空题 */
    int FILL = 3;
    /** 简答题（主观题） */
    int ESSAY = 4;

    static String getLabel(int type) {
        switch (type) {
            case SINGLE: return "单选题";
            case MULTIPLE: return "多选题";
            case JUDGE: return "判断题";
            case FILL: return "填空题";
            case ESSAY: return "简答题";
            default: return "未知";
        }
    }

    /** 是否为客观题（自动判分） */
    static boolean isObjective(int type) {
        return type == SINGLE || type == MULTIPLE || type == JUDGE;
    }

    /** 是否为主观题（需人工复核） */
    static boolean isSubjective(int type) {
        return type == FILL || type == ESSAY;
    }
}