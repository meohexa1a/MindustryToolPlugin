package mindustrytool.utils;

import java.net.URI;
import java.util.HashSet;

import arc.util.Log;
import arc.util.Strings;
import mindustry.gen.Player;

public class VPNUtils {
    private static final HashSet<String> BLACK_LISTED_SUBNET = new HashSet<>();

    public static void init() {
        Utils.executeExpectError(() -> {
            try {
                var response = JsonUtils.objectMapper.readTree(URI.create("https://api.github.com/meta").toURL());

                var actions = response.withArray("actions").elements();

                while (actions.hasNext()) {
                    var subnet = actions.next().asText();
                    if (subnet.contains(":")) {
                        return; // skipping IPv6
                    }

                    BLACK_LISTED_SUBNET.add(subnet);
                }
            } catch (Exception e) {
                Log.err(e);
            }
        });
    }

    public static boolean isBot(Player player) {
        var ip = player.ip();

        for (int i = 0; i < player.name().length(); i++) {
            char ch = player.name().charAt(i);
            if (ch <= '\u001f') {
                return true;
            }
        }

        // TODO: Api endpoint for this
        if (isVpnIp(ip)) {
            return true;
        }

        return false;
    }

    public static boolean isVpnIp(String ipAddress) {
        return BLACK_LISTED_SUBNET.stream().anyMatch(subnet -> isIPInSubnet(ipAddress, subnet));
    }

    public static boolean isIPInSubnet(String ipAddress, String subnet) {
        String[] subnetParts = subnet.split("/");
        String subnetAddress = subnetParts[0];

        int subnetPrefix = Strings.parseInt(subnetParts[1]);
        int ipInt = ipToInt(ipAddress);
        int subnetInt = ipToInt(subnetAddress);
        int subnetMask = (0xFFFFFFFF << (32 - subnetPrefix));

        return (ipInt & subnetMask) == (subnetInt & subnetMask);
    }

    public static int ipToInt(String ipAddress) {
        String[] ipParts = ipAddress.split("\\.");
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result = result << 8 | Strings.parseInt(ipParts[i]);
        }
        return result;
    }
}
