package mindustrytool.messages.request;

import lombok.Data;
import lombok.experimental.Accessors;
import mindustrytool.type.Team;

@Data
@Accessors(chain = true)
public class PlayerMessageRequest {
    private String uuid;
    private String name;
    private String ip;
    private Team team;
}
