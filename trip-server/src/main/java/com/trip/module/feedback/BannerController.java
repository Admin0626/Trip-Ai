package com.trip.module.feedback;

import com.trip.common.exception.BizException;
import com.trip.common.result.*;
import com.trip.module.ai.StrictPlannerJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.net.URI;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class BannerController {
    private final JdbcTemplate jdbc;
    public record Save(@NotBlank @Size(max=100) @JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.Text.class) String title,
            @NotBlank @Size(max=255) @JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.Text.class) String imageUrl,
            @NotNull @Pattern(regexp="NONE|ROUTE|DESTINATION|URL") @JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.Text.class) String linkType,
            @Size(max=255) @JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.Text.class) String linkValue,
            @NotNull @Min(0) @Max(10000) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer sortNo,
            @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer status,
            LocalDateTime startTime,LocalDateTime endTime) {}
    private static final String COLUMNS="id,title,image_url AS imageUrl,link_type AS linkType,link_value AS linkValue,sort_no AS sortNo,status,start_time AS startTime,end_time AS endTime";
    @GetMapping("/banner/list")
    public R<List<Map<String,Object>>> active(){
        return R.ok(jdbc.queryForList("SELECT "+COLUMNS+" FROM banner b WHERE status=1 AND (start_time IS NULL OR start_time<=NOW()) AND (end_time IS NULL OR end_time>NOW()) AND (link_type NOT IN ('ROUTE','DESTINATION') OR (link_type='DESTINATION' AND EXISTS(SELECT 1 FROM destination d WHERE d.id=b.link_value AND d.status=1 AND d.deleted=0)) OR (link_type='ROUTE' AND EXISTS(SELECT 1 FROM route r JOIN destination d ON d.id=r.destination_id WHERE r.id=b.link_value AND r.status=1 AND r.deleted=0 AND d.status=1 AND d.deleted=0))) ORDER BY sort_no,create_time DESC,id DESC LIMIT 8"));
    }
    @GetMapping("/admin/banner/page")
    public R<PageResult<Map<String,Object>>> page(@RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="20") long size){
        if(current<1||current>1000000||size<1||size>100)throw new BizException(400,"分页参数不合法");
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM banner",Long.class));
        var page=new PageResult<Map<String,Object>>();page.setCurrent(current);page.setSize(size);page.setTotal(total);page.setPages((total+size-1)/size);
        page.setRecords(jdbc.queryForList("SELECT "+COLUMNS+" FROM banner ORDER BY sort_no,create_time DESC,id DESC LIMIT ? OFFSET ?",size,(current-1)*size));return R.ok(page);
    }
    @PostMapping("/admin/banner") @Transactional
    public R<Long> create(@Valid @RequestBody Save data){
        validate(data,null);var key=new GeneratedKeyHolder();
        jdbc.update(conn->{var ps=conn.prepareStatement("INSERT INTO banner(title,image_url,link_type,link_value,sort_no,status,start_time,end_time) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            Object[] values=values(data);for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);return ps;},key);
        return R.ok(Objects.requireNonNull(key.getKey()).longValue());
    }
    @PutMapping("/admin/banner/{id}") @Transactional
    public R<Void> update(@PathVariable long id,@Valid @RequestBody Save data){
        validate(data,id);var args=new ArrayList<>(Arrays.asList(values(data)));args.add(id);
        if(jdbc.update("UPDATE banner SET title=?,image_url=?,link_type=?,link_value=?,sort_no=?,status=?,start_time=?,end_time=? WHERE id=?",args.toArray())!=1)throw new BizException(404,"轮播图不存在");return R.ok();
    }
    @DeleteMapping("/admin/banner/{id}") @Transactional
    public R<Void> delete(@PathVariable long id){
        jdbc.queryForList("SELECT id FROM banner ORDER BY id FOR UPDATE");
        if(jdbc.update("DELETE FROM banner WHERE id=?",id)!=1)throw new BizException(404,"轮播图不存在");return R.ok();
    }
    private Object[] values(Save d){return new Object[]{d.title().strip(),d.imageUrl(),d.linkType(),d.linkValue()==null?"":d.linkValue(),d.sortNo(),d.status(),d.startTime(),d.endTime()};}
    private void validate(Save d,Long id){
        if(!safeUrl(d.imageUrl(),true))throw new BizException(400,"图片地址须为本站图片或HTTP(S)地址");
        if(d.startTime()!=null&&d.endTime()!=null&&!d.endTime().isAfter(d.startTime()))throw new BizException(400,"结束时间须晚于开始时间");
        if(d.linkType().equals("URL")&&!safeUrl(d.linkValue(),false))throw new BizException(400,"跳转地址须为HTTP(S)地址");
        if(d.linkType().equals("ROUTE")||d.linkType().equals("DESTINATION")){
            if(d.linkValue()==null||!d.linkValue().matches("[1-9][0-9]{0,17}"))throw new BizException(400,"请填写有效的目标ID");
            String table=d.linkType().equals("ROUTE")?"route":"destination";
            if(Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND deleted=0",Long.class,Long.parseLong(d.linkValue())))==0)throw new BizException(400,"跳转目标不存在");
        }
        var rows=jdbc.queryForList("SELECT id,status FROM banner ORDER BY id FOR UPDATE");
        if(id!=null&&rows.stream().noneMatch(r->((Number)r.get("id")).longValue()==id))throw new BizException(404,"轮播图不存在");
        long enabled=rows.stream().filter(r->((Number)r.get("status")).intValue()==1&&(id==null||((Number)r.get("id")).longValue()!=id)).count();
        if(d.status()==1&&enabled>=8)throw new BizException(400,"最多启用8张轮播图，请先停用其他图片");
    }
    private boolean safeUrl(String value,boolean local){
        if(value==null)return false;
        if(local&&value.matches("/api/files/[a-f0-9]{32}\\.(png|jpg)"))return true;
        try{URI uri=URI.create(value);return Set.of("https","http").contains(Objects.toString(uri.getScheme(),""))&&uri.getHost()!=null&&uri.getUserInfo()==null;}catch(Exception e){return false;}
    }
    @GetMapping("/admin/dashboard/summary")
    public R<Map<String,Object>> summary(){
        return R.ok(Map.of("users",count("SELECT COUNT(*) FROM sys_user WHERE deleted=0"),"destinations",count("SELECT COUNT(*) FROM destination WHERE deleted=0"),"routes",count("SELECT COUNT(*) FROM route WHERE deleted=0"),"pendingBookings",count("SELECT COUNT(*) FROM route_booking WHERE status=0"),"pendingFeedback",count("SELECT COUNT(*) FROM feedback WHERE deleted=0 AND status IN(0,1)")));
    }
    private long count(String sql){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class));}
}
