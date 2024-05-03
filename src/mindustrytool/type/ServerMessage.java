package mindustrytool.type;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ServerMessage {
    private String id;
    private boolean request;
    private String method;

    public boolean isRequest() {
        return request;
    }

    public boolean isResponse() {
        return !request;
    }
}
