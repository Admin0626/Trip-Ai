package com.trip.module.ai;

import com.trip.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class CompatiblePlannerClient {
    private final ObjectMapper json;
    private final int timeout;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public CompatiblePlannerClient(ObjectMapper json, @Value("${trip.ai.timeout-seconds:45}") int timeout) {
        this.json = json; this.timeout = Math.max(1, Math.min(timeout, 60));
    }
    public String call(URI uri, PlannerConnection config, String system, String user, boolean structured) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", config.model()); body.put("stream", false);
        body.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        body.put("max_tokens", structured ? 6000 : 16);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (config.apiKey() != null && !config.apiKey().isBlank()) request.header("Authorization", "Bearer " + config.apiKey().strip());
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request.build(), info -> new LimitedBody());
        try {
            var response = future.get(timeout, TimeUnit.SECONDS);
            int status = response.statusCode();
            if (status == 401 || status == 403) throw new BizException(3004, "模型服务拒绝认证，请检查API Key与模型权限");
            if (status == 429) throw new BizException(3004, "模型服务限流或额度不足，请稍后重试");
            if (status < 200 || status >= 300) throw new BizException(3004, "模型服务请求失败（HTTP " + status + "），请检查API地址与模型名称");
            var root = json.readTree(response.body());
            var content = root.path("choices").path(0).path("message").path("content");
            if (!content.isString() || content.asString().isBlank()) throw new BizException(3004, "模型服务没有返回有效文本");
            return content.asString();
        } catch (BizException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BizException(3004, "模型请求已中断"); }
        catch (TimeoutException e) { throw new BizException(3004, "模型请求超时，请稍后重试或减少行程天数"); }
        catch (Exception e) { throw new BizException(3004, "无法读取模型响应，请检查服务连接、响应大小与兼容协议"); }
        finally { if (!future.isDone()) future.cancel(true); }
    }

    /** Limit buffering to 1 MiB even for chunked responses without Content-Length. */
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long bytes;
        private boolean failed;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription s) { subscription=s; delegate.onSubscribe(s); }
        public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer b : buffers) bytes += b.remaining();
            if (bytes > 1048576) { failed=true; subscription.cancel(); delegate.onError(new IllegalStateException("response limit")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable t) { if (!failed) delegate.onError(t); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
