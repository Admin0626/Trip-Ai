package com.trip.module.ai;

import com.trip.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Bounded synchronous maintenance; every document uses its own service transaction. */
@Service
@RequiredArgsConstructor
public class KnowledgeMaintenanceService {
    private final KnowledgeService knowledge;
    public record Item(long id,int code,String outcome,String message,Long revision,Long chunkCount) {}
    public record Batch(int total,long succeeded,long unchanged,long failed,List<Item> results) {}
    public Batch repair(KnowledgeInput.BatchRepair input) {
        Set<Long> ids=new HashSet<>();
        for(var doc:input.documents())if(!ids.add(doc.id()))throw new BizException(400,"请勿重复选择同一篇资料");
        List<Item> results=new ArrayList<>();
        for(var doc:input.documents()) {
            try {
                var repair=knowledge.repair(doc.id(),doc.expectedRevision());var row=repair.document();
                results.add(new Item(doc.id(),200,repair.outcome(),repair.outcome().equals("REPAIRED")?"本地索引已修复":"索引已就绪，保留原有分片",((Number)row.get("revision")).longValue(),((Number)row.get("chunkCount")).longValue()));
            } catch(BizException e) {results.add(new Item(doc.id(),e.getCode(),"FAILED",e.getMessage(),null,null));}
            catch(RuntimeException e) {results.add(new Item(doc.id(),503,"FAILED","索引修复暂时失败，请稍后刷新重试",null,null));}
        }
        return new Batch(results.size(),results.stream().filter(x->x.outcome().equals("REPAIRED")).count(),results.stream().filter(x->x.outcome().equals("UNCHANGED")).count(),results.stream().filter(x->x.outcome().equals("FAILED")).count(),results);
    }
}
