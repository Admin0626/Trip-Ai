package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.*;
import java.nio.charset.*;
import java.sql.Statement;
import java.util.*;

@Service
@RequiredArgsConstructor
public class KnowledgeService {
    private final JdbcTemplate jdbc;
    private static final String SOURCE_VISIBLE="(d.source_id IS NULL OR (d.doc_type='DESTINATION' AND EXISTS(SELECT 1 FROM destination a WHERE a.id=d.source_id AND a.status=1 AND a.deleted=0)) OR (d.doc_type='ROUTE' AND EXISTS(SELECT 1 FROM route r JOIN destination a ON a.id=r.destination_id WHERE r.id=d.source_id AND r.status=1 AND r.deleted=0 AND a.status=1 AND a.deleted=0)))";
    private static final String PUBLIC="d.status=1 AND d.deleted=0 AND "+SOURCE_VISIBLE;
    private static final String ACTUAL_CHUNKS="(SELECT COUNT(*) FROM knowledge_chunk qc WHERE qc.doc_id=d.id)";
    private static final String GENERATIONS="NOT EXISTS(SELECT 1 FROM knowledge_chunk qc WHERE qc.doc_id=d.id AND qc.doc_revision<>d.revision)";
    private static final String READY="d.index_method='LOCAL_NGRAM' AND d.indexed_revision=d.revision AND d.chunk_count>0 AND d.chunk_count="+ACTUAL_CHUNKS+" AND "+GENERATIONS;
    private static final String SOURCE_STATE="CASE WHEN d.doc_type<>'GUIDE' AND d.source_id IS NULL THEN 'UNLINKED' WHEN "+SOURCE_VISIBLE+" THEN 'AVAILABLE' ELSE 'UNAVAILABLE' END";
    private static final String ISSUE="CASE WHEN d.index_method<>'LOCAL_NGRAM' THEN 'NOT_BUILT' WHEN d.indexed_revision<>d.revision THEN 'STALE_REVISION' WHEN "+ACTUAL_CHUNKS+"=0 THEN 'MISSING_CHUNKS' WHEN d.chunk_count<>"+ACTUAL_CHUNKS+" THEN 'COUNT_MISMATCH' WHEN NOT ("+GENERATIONS+") THEN 'CHUNK_REVISION_MISMATCH' ELSE 'NONE' END";
    private static final String FIELDS="d.id,d.title,d.doc_type AS docType,d.source_type AS sourceType,d.source_id AS sourceId,d.status,d.revision,d.indexed_revision AS indexedRevision,d.index_method AS indexMethod,d.chunk_count AS chunkCount,d.vector_status AS vectorStatus,d.update_time AS updateTime";
    private Map<String,Object> own(long id,boolean lock) {
        if(id<1)throw new BizException(400,"文档ID须为正整数");
        var rows=jdbc.queryForList("SELECT "+FIELDS+",d.content FROM knowledge_doc d WHERE d.id=? AND d.deleted=0"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty())throw new BizException(404,"资料不存在或已删除");return rows.get(0);
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static void expected(Map<String,Object> row,Long revision) {
        if(revision==null || revision!=number(row,"revision"))throw new BizException(409,"资料已被更新，请刷新后重新操作");
    }
    private void source(String type,Long id,int status,boolean required) {
        if(type.equals("GUIDE")) { if(id!=null)throw new BizException(400,"独立攻略无需关联ID");return; }
        if(id==null) { if(required)throw new BizException(400,"目的地或路线资料须选择关联ID");return; }
        String query=type.equals("DESTINATION")?"SELECT COUNT(*) FROM destination WHERE id=? AND deleted=0"+(status==1?" AND status=1":""):
                "SELECT COUNT(*) FROM route r JOIN destination a ON a.id=r.destination_id WHERE r.id=? AND r.deleted=0 AND a.deleted=0"+(status==1?" AND r.status=1 AND a.status=1":"");
        if(jdbc.queryForObject(query,Long.class,id)==0)throw new BizException(400,"关联内容不存在或未公开，请先恢复内容或将资料停用");
    }
    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> page(long current,long size,String keyword,Integer status,String indexState,String sourceState) {
        if(current<1 || current>1000000 || size<1 || size>100 || (status!=null && status!=0 && status!=1))throw new BizException(400,"分页或状态参数超出范围");
        String where=" WHERE d.deleted=0";var args=new ArrayList<Object>();
        if(keyword!=null && !keyword.isBlank()) { String value=KnowledgeText.clean(keyword,1,100,"标题关键字");where+=" AND d.title LIKE ? ESCAPE '='";args.add("%"+value.replace("=","==").replace("%","=%").replace("_","=_")+"%"); }
        if(status!=null){where+=" AND d.status=?";args.add(status);}
        if(indexState!=null){if(!Set.of("READY","NEEDS_REBUILD").contains(indexState))throw new BizException(400,"索引筛选不正确");where+=" AND "+(indexState.equals("READY")?"("+READY+")":"NOT ("+READY+")");}
        if(sourceState!=null){if(!Set.of("AVAILABLE","UNAVAILABLE","UNLINKED").contains(sourceState))throw new BizException(400,"来源筛选不正确");where+=" AND ("+SOURCE_STATE+")=?";args.add(sourceState);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc d"+where,Long.class,args.toArray());
        args.add(size);args.add((current-1)*size);
        var records=jdbc.queryForList("SELECT "+FIELDS+",("+SOURCE_VISIBLE+") AS sourceAvailable,"+ACTUAL_CHUNKS+" AS actualChunkCount,CASE WHEN "+READY+" THEN 'READY' ELSE 'NEEDS_REBUILD' END AS indexState,("+ISSUE+") AS indexIssue,("+SOURCE_STATE+") AS sourceState FROM knowledge_doc d"+where+" ORDER BY d.id DESC LIMIT ? OFFSET ?",args.toArray());
        records.forEach(row->row.put("sourceAvailable",number(row,"sourceAvailable")!=0));
        var page=new PageResult<Map<String,Object>>();page.setRecords(records);page.setTotal(total);page.setCurrent(current);page.setSize(size);page.setPages((total+size-1)/size);return page;
    }
    public Map<String,Object> detail(long id) { return own(id,false); }
    /** Admin-only catalog summaries, including unpublished entries for disabled documents. */
    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> sources(String type,long current,long size,String keyword,Long sourceId) {
        if(!Set.of("DESTINATION","ROUTE").contains(type) || current<1 || current>1000000 || size<1 || size>50 || (sourceId!=null && sourceId<1))throw new BizException(400,"来源类型或分页参数不正确");
        boolean route=type.equals("ROUTE");
        String from=route?" FROM route r JOIN destination a ON a.id=r.destination_id WHERE r.deleted=0 AND a.deleted=0":" FROM destination a WHERE a.deleted=0";
        String id=route?"r.id":"a.id",name=route?"r.title":"a.name",available=route?"r.status=1 AND a.status=1":"a.status=1";
        var args=new ArrayList<Object>();
        if(sourceId!=null){from+=" AND "+id+"=?";args.add(sourceId);}
        if(keyword!=null&&!keyword.isBlank()){String value=KnowledgeText.clean(keyword,1,100,"来源关键字");from+=" AND "+name+" LIKE ? ESCAPE '='";args.add("%"+value.replace("=","==").replace("%","=%").replace("_","=_")+"%");}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());args.add(size);args.add((current-1)*size);
        String fields=id+" AS id,"+name+" AS name,"+(route?"r.status":"a.status")+" AS status,("+available+") AS available,"+(route?"a.name":"NULL")+" AS parentName,CASE WHEN "+available+" THEN 'AVAILABLE' "+(route?"WHEN a.status<>1 THEN 'PARENT_UNAVAILABLE' ":"")+"ELSE 'UNPUBLISHED' END AS availabilityReason";
        var rows=jdbc.queryForList("SELECT "+fields+from+" ORDER BY "+id+" DESC LIMIT ? OFFSET ?",args.toArray());rows.forEach(row->row.put("available",number(row,"available")!=0));
        var page=new PageResult<Map<String,Object>>();page.setRecords(rows);page.setTotal(total);page.setCurrent(current);page.setSize(size);page.setPages((total+size-1)/size);return page;
    }
    public record SourceChange(String outcome,Map<String,Object> document) {}
    @Transactional
    public SourceChange changeSource(long id,KnowledgeInput.Source input) {
        var row=own(id,true);expected(row,input.expectedRevision());
        source(input.docType(),input.sourceId(),(int)number(row,"status"),true);
        Long oldId=row.get("sourceId")==null?null:number(row,"sourceId");
        if(input.docType().equals(row.get("docType"))&&Objects.equals(input.sourceId(),oldId))return new SourceChange("UNCHANGED",row);
        long oldRevision=number(row,"revision"),revision=oldRevision+1;
        boolean ready=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc d WHERE d.id=? AND "+READY,Long.class,id)==1;
        // The indexed text is unchanged. Move only matching generations; damaged indexes stay damaged.
        jdbc.update("UPDATE knowledge_doc SET doc_type=?,source_id=?,revision=?,indexed_revision=? WHERE id=?",input.docType(),input.sourceId(),revision,ready?revision:0,id);
        jdbc.update("UPDATE knowledge_chunk SET doc_revision=? WHERE doc_id=? AND doc_revision=?",revision,id,oldRevision);
        return new SourceChange("UPDATED",own(id,false));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> health() {
        return jdbc.queryForMap("SELECT COUNT(*) AS total,COALESCE(SUM(d.status=1),0) AS enabled,COALESCE(SUM(d.status=0),0) AS disabled,COALESCE(SUM("+READY+"),0) AS indexReady,COALESCE(SUM(NOT ("+READY+")),0) AS needsRebuild,COALESCE(SUM(("+SOURCE_STATE+")='UNAVAILABLE'),0) AS sourceUnavailable,COALESCE(SUM(("+SOURCE_STATE+")='UNLINKED'),0) AS unlinked,COALESCE(SUM(("+PUBLIC+") AND ("+READY+")),0) AS searchable FROM knowledge_doc d WHERE d.deleted=0");
    }
    public record Repair(String outcome,Map<String,Object> document) {}
    @Transactional
    public Repair repair(long id,long revision) {
        var row=own(id,true);expected(row,revision);
        long ready=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc d WHERE d.id=? AND "+READY,Long.class,id);
        // Skipping healthy data preserves chunk IDs and already saved history references.
        if(ready==1)return new Repair("UNCHANGED",row);
        // This self-call runs inside repair's transaction; the batch calls repair through the proxy.
        return new Repair("REPAIRED",rebuild(id,revision));
    }
    @Transactional public Map<String,Object> save(Long id,KnowledgeInput.Save input) {
        String title=KnowledgeText.clean(input.title(),1,200,"标题"),content=KnowledgeText.clean(input.content(),1,20000,"资料正文");
        source(input.docType(),input.sourceId(),input.status(),true);
        long revision=1;
        if(id==null) {
            if(input.expectedRevision()!=null)throw new BizException(400,"新增资料不应携带旧版本");
            var holder=new GeneratedKeyHolder();
            jdbc.update(c->{var s=c.prepareStatement("INSERT INTO knowledge_doc(title,doc_type,source_type,source_id,content,status,revision) VALUES (?,?,'MANUAL',?,?,?,1)",Statement.RETURN_GENERATED_KEYS);
                s.setString(1,title);s.setString(2,input.docType());s.setObject(3,input.sourceId());s.setString(4,content);s.setInt(5,input.status());return s;},holder);
            id=Objects.requireNonNull(holder.getKey()).longValue();
        } else {
            var row=own(id,true);expected(row,input.expectedRevision());revision=number(row,"revision")+1;
            jdbc.update("UPDATE knowledge_doc SET title=?,doc_type=?,source_id=?,content=?,status=?,revision=?,vector_status=0 WHERE id=?",title,input.docType(),input.sourceId(),content,input.status(),revision,id);
        }
        index(id,revision,title,content);return own(id,false);
    }
    private void index(long id,long revision,String title,String content) {
        jdbc.update("DELETE FROM knowledge_chunk WHERE doc_id=?",id);
        var chunks=KnowledgeText.chunks(content);Set<String> titleTokens=KnowledgeText.tokens(title);
        for(var chunk:chunks) {
            var holder=new GeneratedKeyHolder();
            jdbc.update(c->{var s=c.prepareStatement("INSERT INTO knowledge_chunk(doc_id,doc_revision,chunk_index,start_offset,end_offset,content) VALUES (?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
                s.setLong(1,id);s.setLong(2,revision);s.setInt(3,chunk.index());s.setInt(4,chunk.start());s.setInt(5,chunk.end());s.setString(6,chunk.content());return s;},holder);
            long chunkId=Objects.requireNonNull(holder.getKey()).longValue();Set<String> tokens=new LinkedHashSet<>(titleTokens);tokens.addAll(KnowledgeText.tokens(chunk.content()));
            if(!tokens.isEmpty())jdbc.batchUpdate("INSERT INTO knowledge_chunk_token(chunk_id,token) VALUES (?,?)",new ArrayList<>(tokens),200,(s,token)->{s.setLong(1,chunkId);s.setString(2,token);});
        }
        jdbc.update("UPDATE knowledge_doc SET chunk_count=?,indexed_revision=?,index_method='LOCAL_NGRAM' WHERE id=?",chunks.size(),revision,id);
    }
    @Transactional public Map<String,Object> rebuild(long id,long revision) {
        var row=own(id,true);expected(row,revision);String content=KnowledgeText.clean((String)row.get("content"),1,20000,"资料正文");
        if(!content.equals(row.get("content"))) {
            revision++;
            jdbc.update("UPDATE knowledge_doc SET content=?,revision=?,vector_status=0 WHERE id=?",content,revision,id);
        }
        index(id,revision,(String)row.get("title"),content);return own(id,false);
    }
    @Transactional public Map<String,Object> state(long id,KnowledgeInput.State input) {
        var row=own(id,true);expected(row,input.expectedRevision());Long sourceId=row.get("sourceId")==null?null:number(row,"sourceId");
        if(input.status()==1)source((String)row.get("docType"),sourceId,1,false);
        long revision=number(row,"revision")+1;
        jdbc.update("UPDATE knowledge_doc SET status=?,revision=?,indexed_revision=CASE WHEN indexed_revision=? THEN ? ELSE indexed_revision END WHERE id=?",input.status(),revision,number(row,"revision"),revision,id);
        // State-only changes preserve the same indexed text, and update its matching generation atomically.
        jdbc.update("UPDATE knowledge_chunk SET doc_revision=? WHERE doc_id=? AND doc_revision=?",revision,id,number(row,"revision"));return own(id,false);
    }
    @Transactional public void delete(long id,long revision) {
        var row=own(id,true);expected(row,revision);
        jdbc.update("DELETE FROM knowledge_chunk WHERE doc_id=?",id);
        jdbc.update("UPDATE knowledge_doc SET deleted=1,status=0,revision=revision+1,chunk_count=0,indexed_revision=0,index_method='NONE',vector_status=0 WHERE id=?",id);
    }
    @Transactional public Map<String,Object> upload(String title,String type,Long sourceId,int status,MultipartFile file) {
        String name=file.getOriginalFilename();
        if(name==null || !name.toLowerCase(Locale.ROOT).matches(".*\\.(txt|md)$") || file.getSize()<1 || file.getSize()>262144)throw new BizException(400,"请选择256KiB以内的UTF-8 TXT/MD文件");
        String content;
        try { content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString(); }
        catch(Exception e) { throw new BizException(400,"无法读取文件，请使用UTF-8编码的TXT/MD资料"); }
        if(!Set.of("GUIDE","DESTINATION","ROUTE").contains(type) || (status!=0 && status!=1) || (sourceId!=null && sourceId<1))throw new BizException(400,"资料类型、关联ID或状态不正确");
        var result=save(null,new KnowledgeInput.Save(title,type,content,sourceId,status,null));long id=number(result,"id");
        jdbc.update("UPDATE knowledge_doc SET source_type='FILE' WHERE id=?",id);return own(id,false);
    }
    public Map<String,Object> publicDetail(long id) {
        if(id<1)throw new BizException(400,"资料ID须为正整数");
        var rows=jdbc.queryForList("SELECT d.id,d.title,d.doc_type AS docType,d.content,d.revision,d.update_time AS updateTime,d.source_id AS sourceId FROM knowledge_doc d WHERE d.id=? AND "+PUBLIC+" AND "+READY,id);
        if(rows.isEmpty())throw new BizException(404,"资料未公开、已过期或不存在");return rows.get(0);
    }
    public record Reference(long docId,long chunkId,int chunkIndex,String title,String excerpt,double keywordCoverage,String documentPath,String sourcePath) {}
    public record SearchResult(String query,String mode,boolean matched,String message,List<Reference> references) {}
    /** History never exposes saved text or links without validating current public data. */
    public Optional<Reference> activeReference(Reference old) {
        var rows=jdbc.queryForList("SELECT c.chunk_index,c.content,d.title,d.doc_type,d.source_id FROM knowledge_chunk c JOIN knowledge_doc d ON d.id=c.doc_id WHERE c.id=? AND d.id=? AND c.doc_revision=d.revision AND "+PUBLIC+" AND "+READY,old.chunkId(),old.docId());
        if(rows.isEmpty())return Optional.empty();
        var row=rows.get(0);String sourcePath=null;
        if(row.get("source_id")!=null)sourcePath=("ROUTE".equals(row.get("doc_type"))?"/route/":"/destination/")+number(row,"source_id");
        return Optional.of(new Reference(old.docId(),old.chunkId(),(int)number(row,"chunk_index"),(String)row.get("title"),(String)row.get("content"),old.keywordCoverage(),"/knowledge/"+old.docId(),sourcePath));
    }
    @Transactional(readOnly=true) public SearchResult search(KnowledgeInput.Search input) {
        String query=KnowledgeText.clean(input.query(),2,200,"检索问题");Set<String> tokens=KnowledgeText.tokens(query);
        if(tokens.isEmpty())throw new BizException(400,"请填写至少2个连续汉字或有效英文词");
        if(tokens.size()>200)throw new BizException(400,"检索问题过长");
        int top=input.topK()==null?5:input.topK();
        long publicCount=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc d WHERE "+PUBLIC,Long.class);
        long readyCount=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc d WHERE "+PUBLIC+" AND "+READY,Long.class);
        if(publicCount>0 && readyCount==0)throw new BizException(503,"资料尚未完成本地索引，请联系管理员");
        String placeholders=String.join(",",Collections.nCopies(tokens.size(),"?"));var args=new ArrayList<Object>(tokens);
        var rows=jdbc.queryForList("SELECT c.id,c.doc_id,c.chunk_index,c.content,d.title,d.doc_type,d.source_id,COUNT(DISTINCT t.token) AS matched FROM knowledge_chunk_token t JOIN knowledge_chunk c ON c.id=t.chunk_id JOIN knowledge_doc d ON d.id=c.doc_id WHERE t.token IN ("+placeholders+") AND "+PUBLIC+" AND "+READY+" AND c.doc_revision=d.revision GROUP BY c.id,c.doc_id,c.chunk_index,c.content,d.title,d.doc_type,d.source_id ORDER BY matched DESC,c.id ASC LIMIT 200",args.toArray());
        // Title tokens are shared by every chunk; prefer excerpts containing the actual query terms.
        rows.forEach(row->row.put("bodyMatched",KnowledgeText.tokens((String)row.get("content")).stream().filter(tokens::contains).count()));
        rows.sort(Comparator.<Map<String,Object>>comparingLong(row->number(row,"matched")).reversed()
                .thenComparing(Comparator.<Map<String,Object>>comparingLong(row->number(row,"bodyMatched")).reversed())
                .thenComparingLong(row->number(row,"id")));
        List<Reference> candidates=new ArrayList<>();int minimum=tokens.size()==1?1:2;
        for(var row:rows) {
            long matched=number(row,"matched");double score=matched/(double)tokens.size();if(matched<minimum || score<.3)continue;
            long docId=number(row,"doc_id");String sourcePath=null;
            if(row.get("source_id")!=null)sourcePath=((String)row.get("doc_type")).equals("ROUTE")?"/route/"+number(row,"source_id"):"/destination/"+number(row,"source_id");
            candidates.add(new Reference(docId,number(row,"id"),(int)number(row,"chunk_index"),(String)row.get("title"),(String)row.get("content"),Math.round(score*10000)/10000d,"/knowledge/"+docId,sourcePath));
        }
        List<Reference> references=new ArrayList<>();Set<Long> seenDocs=new HashSet<>(),seenChunks=new HashSet<>();
        for(var ref:candidates){if(seenDocs.add(ref.docId())){references.add(ref);seenChunks.add(ref.chunkId());if(references.size()==top)break;}}
        if(references.size()<top)for(var ref:candidates){if(seenChunks.add(ref.chunkId())){references.add(ref);if(references.size()==top)break;}}
        return new SearchResult(query,"LOCAL_NGRAM",!references.isEmpty(),references.isEmpty()?"当前资料中未找到相关片段，请换用具体地点或关键字；不会编造资料之外的回答":"以下为资料原文片段，请结合来源与更新时间核实",references);
    }
}
