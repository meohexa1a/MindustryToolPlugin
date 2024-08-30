package mindustrytool.messages.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GetServersMessageRequest {
    private int page;
    private int size;
}
