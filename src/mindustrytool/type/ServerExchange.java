package mindustrytool.type;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ServerExchange {
    private String id;
    private String method;
    private Object data;
    private boolean request = true;

    public ServerExchange request() {
        request = true;
        return this;
    }

    public ServerExchange response() {
        request = false;
        return this;
    }

    public boolean isRequest() {
        return request;
    }
}
