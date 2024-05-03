package mindustrytool;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import arc.func.Cons;
import mindustrytool.io.ServerMessageHandler;
import mindustrytool.type.ServerExchange;
import mindustrytool.type.ServerMessage;
import mindustrytool.type.ServerMessageEvent;
import mindustrytool.utils.JsonUtils;

public class APIGateway {

    private ConcurrentHashMap<String, CompletableFuture<String>> requests = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ServerMessageHandler<?>> handlers = new ConcurrentHashMap<>();

    private static final int REQUEST_TIMEOUT = 10;

    public <T> T execute(String method, Object data, Class<T> clazz) {
        String id = UUID.randomUUID().toString();

        ServerExchange exchangeData = new ServerExchange()
                .setData(data)
                .setMethod(method)
                .setId(id);

        CompletableFuture<String> request = new CompletableFuture<>();

        requests.put(id, request);

        System.out.println(JsonUtils.toJsonString(exchangeData));

        try {
            String responseString = request.get(REQUEST_TIMEOUT, TimeUnit.SECONDS);

            JsonNode node = JsonUtils.readJson(responseString);

            return JsonUtils.readJsonAsClass(node.get("data").toString(), clazz);

        } catch (Exception e) {
            throw new RuntimeException(exchangeData.toString(), e);
        } finally {
            requests.remove(id);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void handleMessage(String input) {
        ServerMessage message = JsonUtils.readJsonAsClass(input, ServerMessage.class);

        if (message == null) {
            throw new IllegalStateException("Server message is null");
        }

        if (message.isResponse()) {
            var request = requests.get(message.getId());

            if (request != null) {
                request.complete(input);
            }
        } else {
            String method = message.getMethod();
            ServerMessageHandler<?> handler = handlers.get(method);

            if (handler == null) {
                throw new IllegalStateException("No handler for method " + method);
            }

            JsonNode node = JsonUtils.readJson(input);

            Object data = JsonUtils.readJsonAsClass(node.get("data").toString(), handler.getClazz());
            var event = new ServerMessageEvent(message.getId(), method, data);

            handler.apply(event);
        }
    }

    public <T> void handle(String method, Class<T> clazz, Cons<ServerMessageEvent<T>> event) {
        var handler = new ServerMessageHandler<>(method, clazz, event);

        if (handlers.containsKey(method)) {
            throw new IllegalStateException("Handler for method " + method + " already exists");
        }

        handlers.put(method, handler);
    }
}
