package mindustrytool.messages.response;

import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;

@Data
@Accessors(chain = true)
public class GetServersMessageResponse {

    private List<ResponseData> servers;

    @Data
    @Accessors(chain = true)
    public static class ResponseData {
        private String id;
        private String name;
        private String description;
        private String mode;
        private int port;
        private int players;
        private String mapName;
    }
}
