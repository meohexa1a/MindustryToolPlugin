package mindustrytool.messages;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StatsMessage {
    public long ramUsage;
    public long totalRam;
    public int players;
    public String mapName;
    public byte[] mapData;
}
