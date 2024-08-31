package mindustrytool.messages.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlayerMessageRequest {
    private String uuid;
    private String name;
}
