package mindustrytool.messages.response;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StatsMessageResponse {
    public long ramUsage;
    public long totalRam;
    public int players;
    public String mapName;
    public byte[] mapData;
    public List<String> mods;
}
