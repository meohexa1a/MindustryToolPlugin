package mindustrytool.type;

import mindustrytool.utils.JsonUtils;

public class ServerMessageEvent<T> {
    private final String id;
    private final String method;

    private final T payload;

    public ServerMessageEvent(String id, String method, T payload) {
        this.id = id;
        this.method = method;
        this.payload = payload;
    }

    public String getMethod() {
        return method;
    }

    public T getPayload() {
        return payload;
    }

    public void response(Object data) {
        var message = new ServerExchange().response().setData(data).setId(id);

        System.out.println(JsonUtils.toJsonString(message));
    }
}
