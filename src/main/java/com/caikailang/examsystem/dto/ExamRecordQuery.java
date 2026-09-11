package com.caikailang.examsystem.dto;

/**
 * 考试记录查询条件（教师查某场考试的成绩、学生查自己的历史成绩都用它）。
 *
 * @author lang520-max
 */
public class ExamRecordQuery {

    /** 试卷 ID（教师按场次查成绩时使用） */
    private Long paperId;
    /** 学生 ID，由服务端按 Session 身份强制回填，不接受前端直接指定 */
    private Long studentId;
    /** 状态筛选：ONGOING / SUBMITTED，为空表示全部 */
    private String status;
    /** 排序字段：submit_time / score / id */
    private String sortBy = "submit_time";
    /** 排序方向：asc / desc */
    private String sortDir = "desc";
    private int pageNum = 1;
    private int pageSize = 10;

    public int getOffset() {
        int num = pageNum < 1 ? 1 : pageNum;
        return (num - 1) * getSafePageSize();
    }

    public int getSafePageSize() {
        if (pageSize < 1) {
            return 10;
        }
        return pageSize > 100 ? 100 : pageSize;
    }

    /** 排序字段白名单，防 ORDER BY 注入 */
    public String getSafeSortBy() {
        if ("score".equals(sortBy) || "id".equals(sortBy) || "start_time".equals(sortBy)) {
            return sortBy;
        }
        return "submit_time";
    }

    /** 排序方向白名单 */
    public String getSafeSortDir() {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    public Long getPaperId() {
        return paperId;
    }

    public void setPaperId(Long paperId) {
        this.paperId = paperId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
