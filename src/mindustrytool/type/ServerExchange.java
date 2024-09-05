package mindustrytool.type;

import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class ServerExchange {
    private String id;
    private String method;
    private Object data;
    private String type = "request";

    public ServerExchange response() {
        type = "response";
        return this;
    }

    public ServerExchange request() {
        return this;
    }

    public ServerExchange error() {
        type = "error";
        return this;
    }
}
