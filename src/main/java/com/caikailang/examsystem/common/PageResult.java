package com.caikailang.examsystem.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果封装（配合 MyBatis 的 LIMIT / OFFSET 查询使用）。
 *
 * @param <T> 列表元素类型
 * @author 蔡开浪
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list = new ArrayList<T>();
    /** 总记录数 */
    private long total;
    /** 页码，从 1 开始 */
    private int pageNum;
    /** 每页条数 */
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list == null ? new ArrayList<T>() : list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /** 总页数（至少 1 页，避免前端出现 0 页） */
    public int getPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) ((total + pageSize - 1) / pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
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
