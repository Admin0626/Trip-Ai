package com.trip.module.ai;

import com.trip.common.exception.BizException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Per-request cancellation and progress. Holds no persisted provider credentials. */
public class PlannerExecution {
    public record Progress(String stage,int attempt) {}
    public static class Stopped extends RuntimeException {
        private final int code;
        Stopped(int code) { super("Planner stopped"); this.code=code; }
        public int code() { return code; }
    }
    private int stopped;
    private CompletableFuture<?> outbound;
    private volatile Runnable authorization,commit;
    private volatile Consumer<Progress> progress;
    public PlannerExecution() { this(()->{},p->{},()->{}); }
    public PlannerExecution(Runnable authorization,Consumer<Progress> progress,Runnable commit) {
        this.authorization=authorization; this.progress=progress; this.commit=commit;
    }
    public synchronized void check() { if(stopped!=0)throw new Stopped(stopped); }
    public void authorize() {
        check();
        try { authorization.run(); } catch(BizException e) { stop(e.getCode()); }
        check();
    }
    public void progress(String stage,int attempt) { check(); progress.accept(new Progress(stage,attempt)); check(); }
    public synchronized void attach(CompletableFuture<?> future) { check(); outbound=future; }
    public synchronized void detach(CompletableFuture<?> future) { if(outbound==future)outbound=null; }
    public synchronized void stop(int code) {
        if(stopped!=0)return;
        stopped=code;
        if(outbound!=null)outbound.cancel(true);
    }
    public void commit() { authorize(); commit.run(); check(); }
    public synchronized void clear() { outbound=null; authorization=()->{}; progress=p->{}; commit=()->{}; }
}
