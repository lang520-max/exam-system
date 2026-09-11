package com.caikailang.examsystem.dto;

/**
 * 题库检索条件（配合 MyBatis 动态 SQL 使用）。
 *
 * <p>安全要点：{@code sortBy} / {@code sortDir} 由前端传入，会直接拼进 SQL 的
 * {@code ORDER BY}。{@code #{} } 占位符对 ORDER BY 无效（预编译参数不能做列名），
 * 所以这里用 {@link #getSafeSortBy()} / {@link #getSafeSortDir()} 做白名单收敛，
 * 非法值一律回落到默认排序，从根上堵住 ORDER BY 注入。</p>
 *
 * @author lang520-max
 */
public class QuestionQuery {

    /** 关键字，模糊匹配题干 */
    private String keyword;
    /** 科目精确筛选 */
    private String subject;
    /** 题型筛选：SINGLE / MULTIPLE / JUDGE */
    private String type;
    /** 难度筛选：EASY / MEDIUM / HARD */
    private String difficulty;
    /** 只看自己创建的题（教师视角），为空表示不限 */
    private Long creatorId;
    /** 排序字段：created_at / score / id */
    private String sortBy = "created_at";
    /** 排序方向：asc / desc */
    private String sortDir = "desc";
    /** 页码，从 1 开始 */
    private int pageNum = 1;
    /** 每页条数，默认 10，最大 50 */
    private int pageSize = 10;

    /** 计算 SQL 用的 OFFSET，同时约束 pageSize 上限，防止传入超大分页拖垮数据库 */
    public int getOffset() {
        int num = pageNum < 1 ? 1 : pageNum;
        return (num - 1) * getSafePageSize();
    }

    public int getSafePageSize() {
        if (pageSize < 1) {
            return 10;
        }
        return pageSize > 50 ? 50 : pageSize;
    }

    /** 排序字段白名单：不在名单内的一律用 created_at */
    public String getSafeSortBy() {
        if ("score".equals(sortBy) || "id".equals(sortBy)) {
            return sortBy;
        }
        return "created_at";
    }

    /** 排序方向白名单：只允许 asc / desc */
    public String getSafeSortDir() {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
