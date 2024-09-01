package mindustrytool;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import arc.func.Cons;
import arc.util.Log;
import mindustrytool.handlers.ServerMessageHandler;
import mindustrytool.type.ServerExchange;
import mindustrytool.type.ServerMessageEvent;
import mindustrytool.utils.JsonUtils;

public class APIGateway {

    private ConcurrentHashMap<String, CompletableFuture<String>> requests = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ServerMessageHandler<?>> handlers = new ConcurrentHashMap<>();

    private static final int REQUEST_TIMEOUT = 20;

    private static final Executor executor = Executors.newFixedThreadPool(10);

    public <T> T execute(String method, Object data, Class<T> clazz) throws RuntimeException {
        return execute(method, data, clazz, REQUEST_TIMEOUT);
    }

    public <T> T execute(String method, Object data, Class<T> clazz, int timeOutSeconds) throws RuntimeException {
        String id = UUID.randomUUID().toString();

        ServerExchange exchangeData = new ServerExchange().setData(data).setMethod(method).setId(id);

        CompletableFuture<String> request = new CompletableFuture<>();

        requests.put(id, request);

        System.out.println(JsonUtils.toJsonString(exchangeData));

        try {
            String responseString = request.get(timeOutSeconds, TimeUnit.SECONDS);

            JsonNode node = JsonUtils.readJson(responseString);

            return JsonUtils.readJsonAsClass(node.get("data").toString(), clazz);

        } catch (Exception e) {
            Log.err(exchangeData.toString(), e);

            throw new RuntimeException(exchangeData.toString(), e);
        } finally {
            requests.remove(id);
        }
    }

    public void emit(String method, Object data) {
        String id = UUID.randomUUID().toString();

        ServerExchange exchangeData = new ServerExchange()//
                .setData(data)//
                .setMethod(method)//
                .setId(id);

        System.out.println(JsonUtils.toJsonString(exchangeData));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void handleMessage(String input) throws JsonParseException, JsonMappingException, IOException {
        JsonNode node = JsonUtils.readJson(input);

        executor.execute(() -> {
            try {
                String id = node.get("id").asText();
                Boolean isRequest = node.get("request").asBoolean();

                if (isRequest) {
                    String method = node.get("method").asText();
                    ServerMessageHandler<?> handler = handlers.get(method);

                    if (handler == null) {
                        throw new IllegalStateException("No handler for method " + method);
                    }

                    Object data = JsonUtils.readJsonAsClass(node.get("data").toString(), handler.getClazz());
                    var event = new ServerMessageEvent(id, method, data);

                    handler.apply(event);
                } else {

                    var request = requests.get(id);

                    if (request != null) {
                        request.complete(input);
                    }
                }
            } catch (Exception e) {
                Log.err(e);
            }
        });
    }

    public <T> void on(String method, Class<T> clazz, Cons<ServerMessageEvent<T>> event) {
        var handler = new ServerMessageHandler<>(method, clazz, event);

        if (handlers.containsKey(method)) {
            throw new IllegalStateException("Handler for method " + method + " already exists");
        }

        handlers.put(method, handler);
    }
}
