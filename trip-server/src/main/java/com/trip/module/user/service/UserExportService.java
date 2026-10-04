package com.trip.module.user.service;

import com.trip.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class UserExportService {
    private final JdbcTemplate jdbc;
    @Transactional(readOnly=true)
    public byte[] export(String keyword,Integer status) {
        if(status!=null && status!=0 && status!=1 || keyword!=null && keyword.length()>100)
            throw new BizException(400,"导出筛选参数不合法");
        String where=" WHERE deleted=0";var args=new ArrayList<Object>();
        if(keyword!=null && !keyword.isBlank()) {where+=" AND (username LIKE ? OR nickname LIKE ?)";args.add("%"+keyword.strip()+"%");args.add("%"+keyword.strip()+"%");}
        if(status!=null){where+=" AND status=?";args.add(status);}
        String[] fields={"id","username","nickname","city","role","status","createTime","lastLoginTime"};
        var rows=jdbc.queryForList("SELECT id,username,nickname,city,role,status,create_time AS createTime,last_login_time AS lastLoginTime FROM sys_user"+where+" ORDER BY id DESC LIMIT 5001",args.toArray());
        if(rows.size()>5000) throw new BizException(400,"最多导出5000名用户，请缩小筛选范围");
        var out=new StringBuilder("\uFEFF用户ID,用户名,昵称,城市,角色,状态,注册时间,最后登录时间\r\n");
        for(var row:rows){for(int i=0;i<fields.length;i++){if(i>0)out.append(',');out.append(cell(row.get(fields[i])));}out.append("\r\n");}
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    static String cell(Object value) {
        String s=value==null?"":value.toString();
        String probe=s.stripLeading();
        if(!probe.isEmpty() && "=+-@".indexOf(probe.charAt(0))>=0 || s.startsWith("\t") || s.startsWith("\r") || s.startsWith("\n"))s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
}
