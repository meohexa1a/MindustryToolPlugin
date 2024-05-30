package mindustrytool.handlers;

import arc.Core;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustrytool.APIGateway;
import mindustrytool.messages.StatsMessage;

public class APIHandler {

    public void registerHandler(APIGateway apiGateway) {
        apiGateway.on("DISCORD_MESSAGE", String.class, event -> Call.sendMessage(event.getPayload()));

        apiGateway.on("STATS", String.class, event -> {
            String mapName = Vars.state.map == null ? "" : Vars.state.map.name();

            StatsMessage message = new StatsMessage()//
                    .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                    .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024).setPlayers(Groups.player.size())
                    .setMapName(mapName);

            event.response(message);
        });
    }
}
