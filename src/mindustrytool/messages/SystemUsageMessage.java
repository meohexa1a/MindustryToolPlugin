package mindustrytool.messages;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SystemUsageMessage {
    public long ramUsage;
    public long totalRam;
}
