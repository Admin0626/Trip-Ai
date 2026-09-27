package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin/ai/recommend")
@RequiredArgsConstructor
public class AdminAiLogController {
    private final JdbcTemplate jdbc;
    @GetMapping("log")
    public R<PageResult<Map<String,Object>>> page(@RequestParam(defaultValue="1") long current,
            @RequestParam(defaultValue="20") long size, @RequestParam(required=false) Long userId) {
        if (current < 1 || current > 1000000 || size < 1 || size > 100 || (userId != null && userId < 1)) throw new BizException(400, "分页或用户参数超出范围");
        String where = userId == null ? "" : " WHERE user_id=?";
        var args = new ArrayList<Object>(); if (userId != null) args.add(userId);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM llm_call_log" + where, Long.class, args.toArray());
        args.add(size); args.add((current-1)*size);
        var records = jdbc.queryForList("SELECT id,user_id AS userId,scene,model,cost_ms AS costMs,success,is_fallback AS isFallback,create_time AS createTime FROM llm_call_log"
                + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray());
        var result = new PageResult<Map<String,Object>>();
        result.setRecords(records); result.setTotal(total == null ? 0 : total); result.setCurrent(current); result.setSize(size);
        result.setPages((result.getTotal()+size-1)/size); return R.ok(result);
    }
}
