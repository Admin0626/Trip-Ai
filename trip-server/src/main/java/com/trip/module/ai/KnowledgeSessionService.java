package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Statement;
import java.util.*;

/** Private, durable retrieval history. It never calls an LLM or sends model context. */
@Service
@RequiredArgsConstructor
public class KnowledgeSessionService {
    private final JdbcTemplate jdbc;
    private final KnowledgeService knowledge;
    private final ObjectMapper json;
    private static final String FIELDS="id,title,mode,revision,message_count AS messageCount,last_message_time AS lastMessageTime,create_time AS createTime,update_time AS updateTime";
    private static final String MESSAGE_FIELDS="id,session_id,role,message_type,content,references_json,request_id,tokens_used,cost_ms,create_time";
    private static final String DEFAULT_TITLE="新资料会话";
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }

    private Map<String,Object> own(long uid,long id,boolean lock) {
        if(id<1)throw new BizException(400,"会话ID须为正整数");
        var rows=jdbc.queryForList("SELECT "+FIELDS+" FROM llm_chat_session WHERE id=? AND user_id=? AND mode='LOCAL_SEARCH' AND deleted=0"+(lock?" FOR UPDATE":""),id,uid);
        if(rows.isEmpty())throw new BizException(404,"资料会话不存在或已删除");
        return rows.get(0);
    }
    private static void expected(Map<String,Object> row,long version) {
        if(version<1)throw new BizException(400,"版本须为正整数");
        if(version!=number(row,"revision"))throw new BizException(409,"会话已变化，请刷新后重新操作");
    }
    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> page(long uid,long current,long size,String keyword) {
        if(current<1||current>1000000||size<1||size>50)throw new BizException(400,"会话分页超出范围");
        String where=" WHERE user_id=? AND mode='LOCAL_SEARCH' AND deleted=0";
        var args=new ArrayList<Object>();args.add(uid);
        if(keyword!=null&&!keyword.isBlank()) {
            String value=KnowledgeText.clean(keyword,1,100,"会话标题关键字");
            where+=" AND title LIKE ? ESCAPE '='";args.add("%"+value.replace("=","==").replace("%","=%").replace("_","=_")+"%");
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM llm_chat_session"+where,Long.class,args.toArray());
        args.add(size);args.add((current-1)*size);
        var rows=jdbc.queryForList("SELECT "+FIELDS+" FROM llm_chat_session"+where+" ORDER BY update_time DESC,id DESC LIMIT ? OFFSET ?",args.toArray());
        var result=new PageResult<Map<String,Object>>();result.setRecords(rows);result.setTotal(total);result.setCurrent(current);result.setSize(size);result.setPages((total+size-1)/size);return result;
    }
    public Map<String,Object> detail(long uid,long id) { return own(uid,id,false); }

    @Transactional
    public Map<String,Object> create(long uid,KnowledgeSessionInput.Create input) {
        String title=input.title()==null||input.title().isBlank()?DEFAULT_TITLE:KnowledgeText.clean(input.title(),1,100,"会话标题");
        // Serialize creation for this user so the bounded count cannot race.
        var users=jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 AND status=1 FOR UPDATE",uid);
        if(users.isEmpty())throw new BizException(401,"账号不可用，请重新登录");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM llm_chat_session WHERE user_id=? AND mode='LOCAL_SEARCH' AND deleted=0",Long.class,uid);
        if(count>=100)throw new BizException(400,"最多保留100个资料会话，请先删除不需要的会话");
        var holder=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement("INSERT INTO llm_chat_session(user_id,title,mode,revision) VALUES (?,?,'LOCAL_SEARCH',1)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,uid);s.setString(2,title);return s;},holder);
        return own(uid,Objects.requireNonNull(holder.getKey()).longValue(),false);
    }
    @Transactional
    public Map<String,Object> rename(long uid,long id,KnowledgeSessionInput.Rename input) {
        String title=KnowledgeText.clean(input.title(),1,100,"会话标题");
        var row=own(uid,id,true);expected(row,input.expectedRevision());
        jdbc.update("UPDATE llm_chat_session SET title=?,revision=revision+1 WHERE id=?",title,id);return own(uid,id,false);
    }
    @Transactional
    public void delete(long uid,long id,long version) {
        var row=own(uid,id,true);expected(row,version);
        jdbc.update("DELETE FROM llm_chat_message WHERE session_id=?",id);
        jdbc.update("UPDATE llm_chat_session SET deleted=1,message_count=0,revision=revision+1 WHERE id=?",id);
    }

    public record ResultView(String query,String mode,boolean matchedAtSearch,String message,
                             List<KnowledgeService.Reference> references,int unavailableReferenceCount) {}
    public record Message(long id,String role,String messageType,String content,String requestId,long tokensUsed,long costMs,Object createTime,ResultView result) {}
    public record MessagePage(List<Message> records,boolean hasMore,Long nextBeforeId) {}
    public record Turn(Map<String,Object> session,String requestId,boolean replayed,List<Message> messages) {}

    private ResultView visibleResult(String raw) {
        KnowledgeService.SearchResult snapshot;
        try { snapshot=json.readValue(raw,KnowledgeService.SearchResult.class); }
        catch(RuntimeException e) { return new ResultView(null,"LOCAL_NGRAM",false,"历史检索记录无法恢复，请重新检索",List.of(),0); }
        if(snapshot==null||!"LOCAL_NGRAM".equals(snapshot.mode())||snapshot.references()==null||snapshot.references().size()>5)
            return new ResultView(null,"LOCAL_NGRAM",false,"历史检索记录无法恢复，请重新检索",List.of(),0);
        var visible=new ArrayList<KnowledgeService.Reference>();
        for(var ref:snapshot.references()) {
            if(ref!=null&&ref.docId()>0&&ref.chunkId()>0)knowledge.activeReference(ref).ifPresent(visible::add);
        }
        int unavailable=snapshot.references().size()-visible.size();
        String message=unavailable>0?"部分历史引用已更新或不再公开，已隐藏旧片段，请重新检索":snapshot.message();
        return new ResultView(snapshot.query(),"LOCAL_NGRAM",snapshot.matched(),message,visible,unavailable);
    }
    private Message message(Map<String,Object> row) {
        String type=(String)row.get("message_type");
        ResultView result="LOCAL_RESULT".equals(type)?visibleResult((String)row.get("references_json")):null;
        return new Message(number(row,"id"),(String)row.get("role"),type,(String)row.get("content"),(String)row.get("request_id"),number(row,"tokens_used"),number(row,"cost_ms"),row.get("create_time"),result);
    }
    @Transactional(readOnly=true)
    public MessagePage messages(long uid,long sessionId,int size,Long beforeId) {
        if(size<1||size>50||(beforeId!=null&&beforeId<1))throw new BizException(400,"历史分页参数超出范围");
        own(uid,sessionId,false);
        String query="SELECT "+MESSAGE_FIELDS+" FROM llm_chat_message WHERE session_id=?";
        var args=new ArrayList<Object>();args.add(sessionId);
        if(beforeId!=null){query+=" AND id<?";args.add(beforeId);}
        args.add(size+1);
        var rows=jdbc.queryForList(query+" ORDER BY id DESC LIMIT ?",args.toArray());
        boolean more=rows.size()>size;if(more)rows.remove(rows.size()-1);
        Collections.reverse(rows);var records=rows.stream().map(this::message).toList();
        return new MessagePage(records,more,more?records.get(0).id():null);
    }
    private static String fingerprint(String query,int top) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((query+"\0"+top).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void insert(long id,String role,String type,String content,String references,String requestId,String hash,int cost) {
        jdbc.update("INSERT INTO llm_chat_message(session_id,role,message_type,content,references_json,request_id,request_hash,tokens_used,cost_ms) VALUES (?,?,?,?,?,?,?,0,?)",id,role,type,content,references,requestId,hash,cost);
    }
    @Transactional
    public Turn search(long uid,KnowledgeSessionInput.Search input) {
        long started=System.nanoTime();String query=KnowledgeText.clean(input.query(),2,200,"检索问题");
        int top=input.topK()==null?5:input.topK();String hash=fingerprint(query,top);long id=input.sessionId();
        var session=own(uid,id,true);
        var existing=jdbc.queryForList("SELECT request_hash FROM llm_chat_message WHERE session_id=? AND request_id=? AND role='user'",id,input.requestId());
        boolean replayed=!existing.isEmpty();
        if(replayed) {
            if(!hash.equals(existing.get(0).get("request_hash")))throw new BizException(409,"此请求编号已用于其他问题，请刷新或提交新的检索");
        } else {
            if(number(session,"messageCount")>=400)throw new BizException(400,"此会话已达200轮，请新建资料会话");
            var result=knowledge.search(new KnowledgeInput.Search(query,top));
            String snapshot=json.writeValueAsString(result);
            insert(id,"user","LOCAL_QUERY",query,null,input.requestId(),hash,0);
            insert(id,"assistant","LOCAL_RESULT",result.matched()?"本地资料检索结果":"本地资料未找到匹配",snapshot,input.requestId(),hash,(int)Math.min(Integer.MAX_VALUE,(System.nanoTime()-started)/1000000));
            String title=(String)session.get("title");
            if(number(session,"messageCount")==0&&DEFAULT_TITLE.equals(title))title=query.substring(0,query.offsetByCodePoints(0,Math.min(20,query.codePointCount(0,query.length()))));
            jdbc.update("UPDATE llm_chat_session SET message_count=message_count+2,last_message_time=NOW(),revision=revision+1,title=? WHERE id=?",title,id);
        }
        var rows=jdbc.queryForList("SELECT "+MESSAGE_FIELDS+" FROM llm_chat_message WHERE session_id=? AND request_id=? ORDER BY id",id,input.requestId());
        if(rows.size()!=2)throw new BizException(503,"检索记录不完整，请联系管理员");
        return new Turn(own(uid,id,false),input.requestId(),replayed,rows.stream().map(this::message).toList());
    }
}
