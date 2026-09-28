package com.trip.module.user.controller;

import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import com.trip.module.catalog.CatalogSaveDTO;
import com.trip.security.AuthSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final JdbcTemplate jdbc;
    private final AuthSessionService sessions;

    @GetMapping("/page")
    public R<PageResult<Map<String,Object>>> page(@RequestParam(defaultValue="1") long current,
            @RequestParam(defaultValue="20") long size, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) Integer status) {
        if(current<1||current>1000000||size<1||size>100||status!=null&&status!=0&&status!=1)
            throw new BizException(400,"分页或状态参数不合法");
        String where=" WHERE deleted=0";var args=new ArrayList<Object>();
        if(keyword!=null&&!keyword.isBlank()){
            if(keyword.length()>100)throw new BizException(400,"搜索词最多100字");
            where+=" AND (username LIKE ? OR nickname LIKE ?)";args.add("%"+keyword.strip()+"%");args.add("%"+keyword.strip()+"%");
        }
        if(status!=null){where+=" AND status=?";args.add(status);}
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user"+where,Long.class,args.toArray()));
        args.add(size);args.add((current-1)*size);
        var result=new PageResult<Map<String,Object>>();result.setCurrent(current);result.setSize(size);result.setTotal(total);result.setPages((total+size-1)/size);
        result.setRecords(jdbc.queryForList("SELECT id,username,nickname,city,role,status,create_time AS createTime,last_login_time AS lastLoginTime FROM sys_user"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",args.toArray()));
        return R.ok(result);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public R<Void> status(@PathVariable long id,@Valid @RequestBody CatalogSaveDTO.StatusInput input){
        var rows=jdbc.queryForList("SELECT role,status FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(rows.isEmpty())throw new BizException(404,"用户不存在");
        if(!"USER".equals(rows.get(0).get("role")))throw new BizException(409,"管理员账号受保护，不能在此启停");
        if(((Number)rows.get(0).get("status")).intValue()!=input.status()){
            // Revocation fails closed. Login takes the same row lock, so it cannot recreate a session during disabling.
            sessions.revokeAll(id);
            jdbc.update("UPDATE sys_user SET status=?,update_time=NOW() WHERE id=?",input.status(),id);
        }
        return R.ok();
    }
}
