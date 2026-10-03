package com.trip.module.ai;

import com.sun.net.httpserver.HttpServer;
import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UserPlannerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PlannerOutputValidator validator = new PlannerOutputValidator(json);
    private String draft(String item) {
        return "{\"title\":\"大理旅行\",\"dayList\":[{\"title\":\"古城\",\"items\":[" + item + "]}]}";
    }
    @Test void deepseekProbeDisablesReasoningAndStructuredCallRequestsJson() {
        var config=new PlannerConnection("https://api.deepseek.com","deepseek-flash","not-a-real-key");
        var probe=CompatiblePlannerClient.requestBody(URI.create("https://api.DeepSeek.com/chat/completions"),config,"system","user",false);
        assertEquals(Map.of("type","disabled"),probe.get("thinking"));assertFalse(probe.containsKey("response_format"));
        var structured=CompatiblePlannerClient.requestBody(URI.create("https://api.deepseek.com/v1/chat/completions"),config,"JSON required","user",true);
        assertEquals(Map.of("type","json_object"),structured.get("response_format"));
        for(String host:List.of("api.openai.com","127.0.0.1","api.deepseek.com.example.org")) {
            var other=CompatiblePlannerClient.requestBody(URI.create("https://"+host+"/v1/chat/completions"),config,"system","user",true);
            assertFalse(other.containsKey("thinking"));assertFalse(other.containsKey("response_format"));
        }
    }
    @Test void acceptsStandardBearerPasteWithoutLeakingItInToString() {
        var config=new PlannerConnection("base","model","  Bearer fixture-not-a-secret  ");
        assertEquals("fixture-not-a-secret",config.apiKey());assertFalse(config.toString().contains(config.apiKey()));
        assertNull(new PlannerConnection("base","model",null).apiKey());
    }
    @Test void validatesOptionalModelAnswerWhileKeepingLegacyItineraries() {
        String body=draft("{\"title\":\"散步\"}");
        assertNull(validator.validate(body,1).answer());
        assertEquals("建议慢游",validator.validate(body.substring(0,body.length()-1)+",\"answer\":\"建议慢游\"}",1).answer());
        for(String answer:List.of("3","\""+"a".repeat(4001)+"\""))assertThrows(IllegalArgumentException.class,()->validator.validate(body.substring(0,body.length()-1)+",\"answer\":"+answer+"}",1));
    }
    @Test void rejectsTruncatedFinalContentEvenWhenNonEmpty() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{
            byte[] body="{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial answer\",\"reasoning_content\":\"do not expose reasoning\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {var error=assertThrows(BizException.class,()->new CompatiblePlannerClient(json,2).call(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),new PlannerConnection("","test",""),"system","user",false));assertTrue(error.getMessage().contains("长度上限"));assertFalse(error.getMessage().contains("reasoning"));}
        finally{server.stop(0);}
    }
    @Test void rejectsUnsafeDestinationsWithoutCallingThem() {
        var policy = new PlannerEndpointPolicy("api.deepseek.com", true);
        for (String url : List.of("http://169.254.169.254/latest", "http://192.168.1.1:8080/v1", "https://untrusted.example/v1",
                "http://localhost:80/v1", "file:///etc/passwd", "http://user:pass@localhost:11434/v1", "http://localhost:11434/v1?key=abc", "http://localhost:11434/v1#fragment")) {
            assertThrows(BizException.class, () -> policy.endpoint(url), url);
        }
    }
    @Test void acceptsCustomLocalPortsAndAppendsOnce() {
        var policy = new PlannerEndpointPolicy("", true);
        assertEquals("http://127.0.0.1:12345/v1/chat/completions", policy.endpoint("http://localhost:12345/v1/").toString());
        assertThrows(BizException.class, () -> policy.endpoint("http://localhost:12345/v1/chat/completions"));
        assertThrows(BizException.class, () -> new PlannerEndpointPolicy("localhost", false).endpoint("http://localhost:11434/v1"));
    }
    @Test void stripsUntrustedIdsAndKeepsUnknownCost() {
        var result = validator.validate(draft("{\"title\":\"苍山\",\"attractionId\":999,\"sortNo\":88}"), 1);
        assertEquals(0, result.dayList().get(0).getItems().get(0).getAttractionId());
        assertEquals(1, result.dayList().get(0).getItems().get(0).getSortNo());
        assertNull(result.dayList().get(0).getItems().get(0).getCost());
    }
    @Test void validatesWholeItineraryStructure() {
        for (String body : List.of("not json", "{}", "null", draft("{\"title\":3}"), draft("{\"title\":\"a\",\"cost\":-1}"),
                draft("{\"title\":\"a\",\"cost\":\"20\"}"), draft("{\"title\":\"a\",\"cost\":1.001}"), draft("{\"title\":\"a\",\"cost\":1000001}"))) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(body, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> validator.validate(draft("{\"title\":\"a\"}"), 2));
    }
    @Test void supportsJsonFenceButNotPlainProse() {
        assertNotNull(validator.validate("```json\n" + draft("{\"title\":\"a\"}") + "\n```", 1));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("方案如下：" + draft("{\"title\":\"a\"}"), 1));
    }
    @Test void requestTimeoutIsBounded() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try { Thread.sleep(1800); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var client = new CompatiblePlannerClient(json, 1);
            var error = assertThrows(BizException.class, () -> client.call(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),
                    new PlannerConnection("", "test-model", ""), "system", "user", false));
            assertTrue(error.getMessage().contains("超时") || error.getMessage().contains("无法读取"));
        } finally { server.stop(0); }
    }
    @Test void doesNotFollowRedirectOrLeakProviderError() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "provider secret should never escape".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest");
            exchange.sendResponseHeaders(302, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var error = assertThrows(BizException.class, () -> new CompatiblePlannerClient(json, 2).call(
                    URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),
                    new PlannerConnection("", "test-model", ""), "system", "user", false));
            assertTrue(error.getMessage().contains("302")); assertFalse(error.getMessage().contains("provider secret"));
        } finally { server.stop(0); }
    }
}
