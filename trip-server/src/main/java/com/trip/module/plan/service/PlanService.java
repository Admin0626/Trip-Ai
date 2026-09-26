package com.trip.module.plan.service;

import com.trip.common.result.PageResult;
import com.trip.module.plan.dto.PlanSaveDTO;
import com.trip.module.plan.vo.PlanVO;

/**
 * 自主规划服务（PLN-01~08）。
 * 保存统一走"事务内先删后插"整体替换；归属校验：查不到即 403。
 */
public interface PlanService {

    /** 创建规划（status 默认 0 草稿） */
    Long create(Long userId, PlanSaveDTO dto);

    /** 更新规划（整体替换 dayList） */
    void update(Long userId, Long planId, PlanSaveDTO dto);

    /** 我的规划分页（status 可选筛选） */
    PageResult<PlanVO> page(Long userId, long current, long size, Integer status);

    /** 规划详情（完整 dayList，归属校验） */
    PlanVO detail(Long userId, Long planId);

    /** 删除规划（归属校验，逻辑删除） */
    void delete(Long userId, Long planId);

    /** 深拷贝为新规划（title 加"（副本）"，status=0） */
    Long copy(Long userId, Long planId);

    /** 出皖文字版行程单 */
    String export(Long userId, Long planId);

    /** 以系统路线为模板生成规划（复用同一构建逻辑） */
    Long fromRoute(Long userId, Long routeId);

    /** AI 优化规划（PLN-09，第 3 批实现，本批预留返回提示） */
    String aiOptimize(Long userId, Long planId);
}