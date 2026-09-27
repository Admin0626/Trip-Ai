package com.trip.module.ai;

import com.trip.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.util.*;

@Component
public class PlannerEndpointPolicy {
    private final Set<String> hosts;
    private final boolean loopback;
    public PlannerEndpointPolicy(@Value("${trip.ai.allowed-hosts:api.deepseek.com,api.openai.com}") String hosts,
                                 @Value("${trip.ai.allow-loopback:false}") boolean loopback) {
        this.hosts = new HashSet<>(Arrays.asList(hosts.toLowerCase(Locale.ROOT).replace(" ", "").split(",")));
        this.loopback = loopback;
    }
    public record Options(Set<String> allowedHosts, boolean allowLoopback) {}
    public Options options() { return new Options(Set.copyOf(hosts), loopback); }

    public URI endpoint(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.strip());
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) throw invalid();
            host = host.toLowerCase(Locale.ROOT);
            boolean local = Set.of("localhost", "127.0.0.1").contains(host);
            if (local) {
                if (!loopback || uri.getPort() < 1024 || uri.getPort() > 65535 || !Set.of("http", "https").contains(uri.getScheme())) throw invalid();
            } else {
                if (!"https".equals(uri.getScheme()) || !hosts.contains(host) || uri.getPort() > 65535 || uri.getPort() == 0) throw invalid();
                for (InetAddress ip : InetAddress.getAllByName(host)) {
                    if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress() || ip.isMulticastAddress()) throw invalid();
                }
            }
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            if (path.contains("..") || path.endsWith("/chat/completions")) throw new BizException(400, "请填写API基础地址，例如https://api.deepseek.com/v1，不要附加/chat/completions");
            return new URI(uri.getScheme(), null, local ? "127.0.0.1" : host, uri.getPort(), path + "/chat/completions", null, null);
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }
    private BizException invalid() { return new BizException(400, "API地址不被允许或无法解析：公网需HTTPS及可信主机；本机需启用本地模式并使用1024以上端口"); }
}
