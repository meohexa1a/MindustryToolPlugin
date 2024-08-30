package mindustrytool.messages.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HostMessage {
    private String gameMode;
    private String mapName;
}
