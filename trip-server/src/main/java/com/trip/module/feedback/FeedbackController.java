package com.trip.module.feedback;

import com.trip.common.exception.BizException;
import com.trip.common.result.*;
import com.trip.module.ai.StrictPlannerJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.sql.Statement;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class FeedbackController {
    private final JdbcTemplate jdbc;
    public record Create(@NotBlank @Pattern(regexp="FUNCTION|BUG|CONTENT|OTHER") String type,
            @NotBlank @Size(max=100) @JsonDeserialize(using=StrictPlannerJson.Text.class) String title,
            @NotBlank @Size(max=2000) @JsonDeserialize(using=StrictPlannerJson.Text.class) String content,
            @Size(max=100) String contact, @Size(max=3) List<@NotBlank @Size(max=255) String> images) {}
    public record Reply(@NotNull @Min(1) @Max(3) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer status,
            @NotBlank @Size(max=2000) @JsonDeserialize(using=StrictPlannerJson.Text.class) String replyContent) {}
    @PostMapping("/feedback")
    public R<Long> create(@AuthenticationPrincipal Long userId,@Valid @RequestBody Create data) {
        var images=data.images()==null?List.<String>of():data.images();
        for(String image:images) if (!image.matches("/api/files/[a-f0-9]{32}\\.(png|jpg)")) throw new BizException(400,"请使用本站上传的反馈图片");
        var key=new GeneratedKeyHolder();
        jdbc.update(conn->{ var ps=conn.prepareStatement("INSERT INTO feedback(user_id,type,title,content,contact,images) VALUES (?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,userId);ps.setString(2,data.type());ps.setString(3,data.title().strip());ps.setString(4,data.content().strip());
            ps.setString(5,data.contact()==null?"":data.contact().strip());ps.setString(6,String.join(",",images));return ps;},key);
        return R.ok(Objects.requireNonNull(key.getKey()).longValue());
    }
    @GetMapping("/feedback/my/page")
    public R<PageResult<Map<String,Object>>> mine(@AuthenticationPrincipal Long userId,@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") long size) {
        return R.ok(page(current,size,userId,null));
    }
    @GetMapping("/admin/feedback/page")
    public R<PageResult<Map<String,Object>>> admin(@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size,@RequestParam(required=false) Integer status) {
        if(status!=null && (status<0||status>3)) throw new BizException(400,"反馈状态不合法");
        return R.ok(page(current,size,null,status));
    }
    private PageResult<Map<String,Object>> page(long current,long size,Long userId,Integer status) {
        if(current<1||current>1000000||size<1||size>100) throw new BizException(400,"分页参数不合法");
        String where=" WHERE deleted=0";var params=new ArrayList<Object>();
        if(userId!=null){where+=" AND user_id=?";params.add(userId);}
        if(status!=null){where+=" AND status=?";params.add(status);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM feedback"+where,Long.class,params.toArray());
        params.add(size);params.add((current-1)*size);
        var records=jdbc.queryForList("SELECT id,user_id AS userId,type,title,content,images,contact,status,reply_content AS replyContent,reply_time AS replyTime,create_time AS createTime FROM feedback"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",params.toArray());
        for(var row:records){String images=Objects.toString(row.get("images"),"");row.put("images",images.isBlank()?List.of():List.of(images.split(",")));}
        var result=new PageResult<Map<String,Object>>();result.setRecords(records);result.setTotal(total==null?0:total);result.setCurrent(current);result.setSize(size);result.setPages((result.getTotal()+size-1)/size);return result;
    }
    @PutMapping("/admin/feedback/{id}/reply")
    public R<Void> reply(@PathVariable long id,@AuthenticationPrincipal Long adminId,@Valid @RequestBody Reply data) {
        var states=jdbc.queryForList("SELECT status FROM feedback WHERE id=? AND deleted=0",Integer.class,id);
        if(states.isEmpty()) throw new BizException(404,"反馈不存在");
        int previous=states.get(0);
        if(previous>=2 && previous!=data.status()) throw new BizException(400,"已结束的反馈不能重新流转状态");
        int changed=jdbc.update("UPDATE feedback SET status=?,reply_content=?,reply_by=?,reply_time=NOW() WHERE id=? AND deleted=0 AND status=?",data.status(),data.replyContent().strip(),adminId,id,previous);
        if(changed!=1) throw new BizException(409,"反馈状态已变化，请刷新后重试");
        return R.ok();
    }
}
