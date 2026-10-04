package com.trip.module.catalog;

import com.trip.common.exception.BizException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, create-only import. Commit revalidates and writes the whole file atomically. */
@Service
@RequiredArgsConstructor
public class CatalogImportService {
    private final ObjectMapper json;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    public record Row(int index,String name,boolean valid,List<String> errors) {}
    public record Preview(String type,int total,boolean valid,List<Row> rows) {}
    private record Entry(int index,Object value,Long parent,String name,List<String> errors) {}
    private record Batch(String type,List<Entry> entries) {}

    @Transactional(readOnly=true)
    public Preview preview(byte[] bytes) {return inspect(parse(bytes));}

    @Transactional
    public Map<String,Object> commit(long uid,byte[] bytes,String requestId,String confirmation) {
        if(!"确认导入".equals(confirmation))throw new BizException(400,"请明确确认导入");
        try {if(!UUID.fromString(requestId).toString().equals(requestId))throw new IllegalArgumentException();}
        catch(Exception e){throw new BizException(400,"requestId必须为小写UUID");}
        var user=jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND role='ADMIN' AND status=1 AND deleted=0 FOR UPDATE",uid);
        if(user.isEmpty())throw new BizException(403,"管理员账号已不可用");
        String hash;
        try {hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e){throw new IllegalStateException(e);}
        var replay=jdbc.queryForList("SELECT payload_hash,result_json FROM catalog_import_batch WHERE user_id=? AND request_id=?",uid,requestId);
        if(!replay.isEmpty()) {
            if(!hash.equals(replay.get(0).get("payload_hash")))throw new BizException(409,"请求编号已用于其他文件，请重新预览");
            var saved=json.readValue((String)replay.get(0).get("result_json"),Map.class);
            var result=new LinkedHashMap<String,Object>(saved);result.put("replayed",true);return result;
        }
        Batch batch=parse(bytes);
        // Lock parents in a fixed order against catalog deletion/status changes and parallel imports.
        batch.entries.stream().filter(e->e.parent!=null).map(Entry::parent).distinct().sorted().forEach(id->
                jdbc.queryForList("SELECT id FROM destination WHERE id=? FOR UPDATE",id));
        Preview report=inspect(batch);
        if(!report.valid)throw new BizException(400,"导入校验未通过，请重新预览并修正；本批未写入");
        var ids=new ArrayList<Long>();
        for(var entry:batch.entries) {
            Long id=switch(batch.type) {
                case "DESTINATION" -> catalog.saveDestination(null,(CatalogSaveDTO.DestinationInput)entry.value);
                case "ATTRACTION" -> catalog.saveAttraction(entry.parent,null,(CatalogSaveDTO.AttractionInput)entry.value);
                default -> catalog.saveRoute(null,uid,(CatalogSaveDTO.RouteInput)entry.value);
            };ids.add(id);
        }
        var result=new LinkedHashMap<String,Object>();result.put("type",batch.type);result.put("total",ids.size());result.put("ids",ids);result.put("replayed",false);
        jdbc.update("INSERT INTO catalog_import_batch(user_id,request_id,payload_hash,result_json) VALUES (?,?,?,?)",uid,requestId,hash,json.writeValueAsString(result));
        return result;
    }

    private Batch parse(byte[] bytes) {
        if(bytes.length==0 || bytes.length>1048576)throw new BizException(400,"请选择1MiB以内的JSON文件");
        JsonNode root;
        try {root=json.reader().with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(bytes);}catch(Exception e){throw new BizException(400,"文件不是有效JSON（不能包含重复字段）");}
        if(root==null || !root.isObject() || root.size()!=3 || !root.has("version") || !root.has("type") || !root.has("records")
                || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt() || root.path("version").asInt()!=1 || !root.path("type").isString()
                || !Set.of("DESTINATION","ATTRACTION","ROUTE").contains(root.path("type").asString())
                || !root.path("records").isArray() || root.path("records").isEmpty() || root.path("records").size()>50)
            throw new BizException(400,"模板须为version=1、type与1至50条records");
        String type=root.path("type").asString();var entries=new ArrayList<Entry>();
        for(JsonNode node:root.path("records")) {
            var errors=new ArrayList<String>();Object value=null;Long parent=null;String name="";
            try {
                if(!node.isObject())throw new IllegalArgumentException();
                var copy=(ObjectNode)node.deepCopy();
                if(type.equals("ATTRACTION")) {
                    var id=copy.remove("destinationId");
                    if(id==null || !id.isIntegralNumber() || !id.canConvertToLong() || id.asLong()<1)throw new IllegalArgumentException();
                    parent=id.asLong();
                }
                Class<?> cls=switch(type){case "DESTINATION"->CatalogSaveDTO.DestinationInput.class;case "ATTRACTION"->CatalogSaveDTO.AttractionInput.class;default->CatalogSaveDTO.RouteInput.class;};
                value=json.readerFor(cls).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json.writeValueAsBytes(copy));
                validator.validate(value).stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).limit(10)
                        .forEach(v->errors.add(v.getPropertyPath()+": "+v.getMessage()));
                int status;
                if(value instanceof CatalogSaveDTO.DestinationInput d){name=d.getName();status=d.getStatus()==null?-1:d.getStatus();}
                else if(value instanceof CatalogSaveDTO.AttractionInput a){name=a.getName();status=a.getStatus()==null?-1:a.getStatus();}
                else {var r=(CatalogSaveDTO.RouteInput)value;name=r.getTitle();parent=r.getDestinationId();status=r.getStatus()==null?-1:r.getStatus();}
                if(status!=0)errors.add("批量导入仅创建下架内容，请设置status=0；导入后单独审核上架");
            }catch(Exception e){errors.add("字段类型、未知字段或目的地ID不合法");}
            entries.add(new Entry(entries.size()+1,value,parent,name==null?"":name,errors));
        }
        return new Batch(type,entries);
    }

    private Preview inspect(Batch batch) {
        var rows=new ArrayList<Row>();var seen=new HashSet<String>();
        for(var entry:batch.entries) {
            var errors=new ArrayList<>(entry.errors);
            if(errors.isEmpty()) {
                String identity;
                List<String> tags=entry.value instanceof CatalogSaveDTO.DestinationInput d?d.getTags():entry.value instanceof CatalogSaveDTO.AttractionInput a?a.getTags():((CatalogSaveDTO.RouteInput)entry.value).getTags();
                if(tags!=null && tags.stream().anyMatch(t->t.contains(",")||t.contains("，")))errors.add("单个标签不能包含逗号");
                if(entry.value instanceof CatalogSaveDTO.DestinationInput d) {
                    identity=d.getProvince().strip()+"\0"+d.getName().strip();
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM destination WHERE province=? AND name=?",Long.class,d.getProvince().strip(),d.getName().strip())>0)
                        errors.add("同省份已存在该目的地（含已删除记录）");
                } else {
                    identity=entry.parent+"\0"+entry.name.strip();
                    var parent=jdbc.queryForList("SELECT id FROM destination WHERE id=? AND deleted=0",entry.parent);
                    if(parent.isEmpty())errors.add("所属目的地不存在或已删除");
                    String table=batch.type.equals("ROUTE")?"route":"attraction",field=batch.type.equals("ROUTE")?"title":"name";
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE destination_id=? AND "+field+"=?",Long.class,entry.parent,entry.name.strip())>0)
                        errors.add("所属目的地下已有同名内容（含已删除记录）");
                    if(entry.value instanceof CatalogSaveDTO.RouteInput r) {
                        if(r.getDays()!=r.getDayList().size())errors.add("路线天数必须等于每日行程数量");
                        for(var day:r.getDayList())for(var item:day.getItems())if(item.getAttractionId()!=0 &&
                                jdbc.queryForObject("SELECT COUNT(*) FROM attraction WHERE id=? AND destination_id=? AND status=1 AND deleted=0",Long.class,item.getAttractionId(),entry.parent)==0)
                            errors.add("关联景点必须属于该目的地且已上架");
                    }
                }
                String duplicateKey=jdbc.queryForObject("SELECT HEX(WEIGHT_STRING(CONVERT(? USING utf8mb4) COLLATE utf8mb4_general_ci))",String.class,identity);
                if(!seen.add(duplicateKey))errors.add("文件内同一范围存在重名内容");
            }
            rows.add(new Row(entry.index,entry.name,errors.isEmpty(),errors.stream().distinct().toList()));
        }
        return new Preview(batch.type,rows.size(),rows.stream().allMatch(Row::valid),rows);
    }
}
