package com.trip.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页响应体（02-接口文档 §1.3）：records / total / current / size / pages
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> pr = new PageResult<>();
        pr.setRecords(page.getRecords());
        pr.setTotal(page.getTotal());
        pr.setCurrent(page.getCurrent());
        pr.setSize(page.getSize());
        pr.setPages(page.getPages());
        return pr;
    }
}