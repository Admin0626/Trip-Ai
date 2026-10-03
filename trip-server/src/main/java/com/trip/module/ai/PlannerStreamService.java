package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.security.AuthSessionService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, instance-local jobs. Initial HTTP authentication still applies. */
@Service
public class PlannerStreamService {
    private final PlannerService planner;
    private final AuthSessionService sessions;
    private final ObjectMapper json;
    private final long timeoutMs;
    private final int heartbeatSeconds;
    private final Map<String,Job> jobs=new HashMap<>();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(8,8,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final ScheduledExecutorService heartbeats=Executors.newScheduledThreadPool(2);
    public PlannerStreamService(PlannerService planner,AuthSessionService sessions,ObjectMapper json,
            @Value("${trip.ai.stream.timeout-seconds:125}") int timeout,
            @Value("${trip.ai.stream.heartbeat-seconds:5}") int heartbeat) {
        if(timeout<2 || timeout>180 || heartbeat<1 || heartbeat>15 || heartbeat>=timeout)throw new IllegalArgumentException("Invalid planner stream configuration");
        this.planner=planner;this.sessions=sessions;this.json=json;this.timeoutMs=timeout*1000L;this.heartbeatSeconds=heartbeat;
    }
    public record Status(String requestId,String state,String startedAt,String finishedAt) {}
    public record Cancel(String requestId,boolean accepted,String state) {}
    private final class Job {
        final String id;
        final long userId;
        final PlannerService.Slot slot;
        final SseEmitter emitter=new SseEmitter(timeoutMs);
        final PlannerExecution execution;
        final long started=System.currentTimeMillis();
        final Object output=new Object();
        volatile ScheduledFuture<?> heartbeat;
        private String state="RUNNING";
        private long finished;
        private int stopped;
        private volatile boolean disconnected;
        Job(String id,long userId,PlannerService.Slot slot,String token) {
            this.id=id;this.userId=userId;this.slot=slot;
            this.execution=new PlannerExecution(()->{
                if(!Objects.equals(sessions.authenticate(token,"access").getId(),userId))throw new BizException(401,"登录已变更");
            },p->emit("progress",Map.of("stage",p.stage(),"attempt",p.attempt()),false),this::commitResult);
            emitter.onTimeout(()->stop(408));
            emitter.onError(e->{ disconnected=true;stop(499); });
            emitter.onCompletion(()->{ disconnected=true;stop(499); });
        }
        synchronized boolean active() { return state.equals("RUNNING"); }
        synchronized Status status() { return new Status(id,state,Instant.ofEpochMilli(started).toString(),finished==0?null:Instant.ofEpochMilli(finished).toString()); }
        synchronized boolean stop(int code) {
            if(state.equals("CANCELLING"))return true;
            if(!active())return false;
            stopped=code;state="CANCELLING";execution.stop(code);return true;
        }
        synchronized void commitResult() {
            if(!active())throw new PlannerExecution.Stopped(stopped==0?499:stopped);
            state="FINISHING";
        }
        void emit(String event,Object data,boolean terminal) {
            synchronized(output) {
                if(disconnected || (!terminal && !active()))return;
                try {
                    // Serialize through the shared Jackson3 mapper; never send provider raw text.
                    emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(Map.of("requestId",id,"data",data)),MediaType.APPLICATION_JSON));
                } catch(IOException | IllegalStateException e) { disconnected=true;stop(499); }
            }
        }
        void terminal(String event,Object data,String finalState) {
            synchronized(this) { if(finished!=0)return;state=finalState;finished=System.currentTimeMillis(); }
            if(heartbeat!=null)heartbeat.cancel(false);
            try { emit(event,data,true);if(!disconnected)emitter.complete(); }
            finally { execution.clear(); }
        }
        void tick() {
            if(!active())return;
            if(System.currentTimeMillis()-started>=timeoutMs-heartbeatSeconds*1000L) { stop(408);return; }
            try { execution.authorize();emit("heartbeat",Map.of("elapsedSeconds",(System.currentTimeMillis()-started)/1000),false); }
            catch(PlannerExecution.Stopped e) { stop(e.code()); }
            catch(RuntimeException e) { stop(503); }
        }
        void run(PlannerController.Generate input) {
            try {
                emit("start",Map.of("state","RUNNING"),false);
                Object preview=planner.executeReserved(slot,input,false,execution);
                terminal("done",preview,"SUCCEEDED");
            } catch(PlannerExecution.Stopped e) {
                if(e.code()==499)terminal("cancelled",Map.of("code",499,"message","已取消生成；已准入的调用仍计入本小时次数"),"CANCELLED");
                else terminal("error",Map.of("code",e.code(),"message",switch(e.code()) {
                    case 401 -> "登录已变更或过期，请重新操作";
                    case 408 -> "生成等待超时，请稍后重试或使用普通生成";
                    default -> "认证服务暂不可用，已停止生成，请稍后重试";
                }),"FAILED");
            } catch(BizException e) { terminal("error",Map.of("code",e.getCode(),"message",e.getMessage()),"FAILED"); }
            catch(RuntimeException e) { terminal("error",Map.of("code",500,"message","无法完成生成，请稍后重试"),"FAILED"); }
            finally { slot.close(); }
        }
    }
    public SseEmitter start(long userId,String token,PlannerController.Stream request) {
        Job job;
        synchronized(jobs) {
            long cutoff=System.currentTimeMillis()-600000;
            jobs.values().removeIf(j->{ synchronized(j) { return j.finished>0 && j.finished<cutoff; } });
            if(jobs.containsKey(request.requestId()))throw new BizException(409,"该请求编号已使用，请重新发起生成");
            if(jobs.size()>=2048)throw new BizException(429,"生成记录暂满，请稍后重试");
            var slot=planner.reserve(userId,request.input().connection());
            job=new Job(request.requestId(),userId,slot,token);jobs.put(job.id,job);
            try {
                job.heartbeat=heartbeats.scheduleAtFixedRate(job::tick,heartbeatSeconds,heartbeatSeconds,TimeUnit.SECONDS);
                workers.execute(()->job.run(request.input()));
            } catch(RejectedExecutionException e) {
                if(job.heartbeat!=null)job.heartbeat.cancel(false);
                jobs.remove(job.id);slot.close();throw new BizException(503,"生成服务暂不可用，请稍后重试");
            }
        }
        return job.emitter;
    }
    private Job own(long userId,String id) {
        synchronized(jobs) {
            var job=jobs.get(id);
            if(job!=null) synchronized(job) { if(job.finished>0 && job.finished<System.currentTimeMillis()-600000) { jobs.remove(id);job=null; } }
            if(job==null || job.userId!=userId)throw new BizException(404,"生成请求不存在或已过期");
            return job;
        }
    }
    public Status status(long userId,String id) { return own(userId,id).status(); }
    public Cancel cancel(long userId,String id) {
        var job=own(userId,id);
        if(job.status().state().equals("CANCELLED"))return new Cancel(id,true,"CANCELLED");
        if(!job.stop(499))throw new BizException(409,"该请求已完成或正在提交结果，无法再取消");
        return new Cancel(id,true,job.status().state());
    }
    @PreDestroy void shutdown() {
        synchronized(jobs) { jobs.values().forEach(j->j.stop(503)); }
        heartbeats.shutdownNow();workers.shutdown();
    }
}
