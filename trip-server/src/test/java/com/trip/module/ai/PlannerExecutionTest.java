package com.trip.module.ai;

import com.sun.net.httpserver.HttpServer;
import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlannerExecutionTest {
    @Test void cancellationBeforeSendAndRevocationPreserveReason() {
        var execution=new PlannerExecution();execution.stop(499);execution.stop(408);
        var client=new CompatiblePlannerClient(new ObjectMapper(),10);
        var stopped=assertThrows(PlannerExecution.Stopped.class,()->client.call(URI.create("http://127.0.0.1:1"),
                new PlannerConnection("","model",""),"system","user",true,execution));
        assertEquals(499,stopped.code());
        var revoked=new PlannerExecution(()->{throw new BizException(401,"expired");},p->{},()->{});
        assertEquals(401,assertThrows(PlannerExecution.Stopped.class,revoked::authorize).code());
    }

    @Test void cancelsRealOutboundFutureWithoutInterruptingCleanupThread() throws Exception {
        var received=new CountDownLatch(1);var release=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/call",exchange->{received.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});
        server.start();var workers=Executors.newSingleThreadExecutor();var interrupted=new AtomicBoolean();
        try {
            var execution=new PlannerExecution();var client=new CompatiblePlannerClient(new ObjectMapper(),10);
            var result=workers.submit(()->{try{return client.call(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/call"),
                    new PlannerConnection("","model",""),"system","user",true,execution);}finally{interrupted.set(Thread.currentThread().isInterrupted());}});
            assertTrue(received.await(3,TimeUnit.SECONDS));execution.stop(499);
            var failure=assertThrows(ExecutionException.class,()->result.get(2,TimeUnit.SECONDS));
            assertInstanceOf(PlannerExecution.Stopped.class,failure.getCause());
            assertEquals(499,((PlannerExecution.Stopped)failure.getCause()).code());assertFalse(interrupted.get());
        } finally { release.countDown();workers.shutdownNow();server.stop(0); }
    }

    @Test void cancellationPreventsCommit() {
        var committed=new AtomicBoolean();var execution=new PlannerExecution(()->{},p->{},()->committed.set(true));
        execution.stop(499);assertThrows(PlannerExecution.Stopped.class,execution::commit);assertFalse(committed.get());
    }

    @Test void sharedSlotsLimitUsersAndReleaseExactlyOnce() {
        var policy=new PlannerEndpointPolicy("",true);
        var service=new PlannerService(policy,mock(CompatiblePlannerClient.class),mock(PlannerOutputValidator.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),new ObjectMapper(),mock(AiQuotaService.class),mock(PlannerCircuitService.class));
        var connection=new PlannerConnection("http://127.0.0.1:11434/v1","model","");
        var slots=new ArrayList<PlannerService.Slot>();for(long id=1;id<=8;id++)slots.add(service.reserve(id,connection));
        assertEquals(429,assertThrows(BizException.class,()->service.reserve(1,connection)).getCode());
        assertEquals(429,assertThrows(BizException.class,()->service.reserve(9,connection)).getCode());
        slots.get(0).close();slots.get(0).close();var replacement=service.reserve(9,connection);
        assertThrows(BizException.class,()->service.reserve(10,connection));
        replacement.close();slots.forEach(PlannerService.Slot::close);
    }
}
