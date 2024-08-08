package mindustrytool.handlers;

import arc.Core;
import arc.files.Fi;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.io.MapIO;
import mindustrytool.APIGateway;
import mindustrytool.messages.StatsMessage;

public class APIHandler {

    private final String TEMP_SAVE_NAME = "TempSave";

    public void registerHandler(APIGateway apiGateway) {
        apiGateway.on("DISCORD_MESSAGE", String.class, event -> Call.sendMessage(event.getPayload()));

        apiGateway.on("STATS", String.class, event -> {
            var map = Vars.state.map;

            String mapName = "";
            byte[] mapData = {};

            if (map != null) {
                var pix = MapIO.generatePreview(Vars.world.tiles);
                Fi file = Fi.tempFile(TEMP_SAVE_NAME);
                file.writePng(pix);

                mapData = file.readBytes();
                mapName = map.name();
            }

            StatsMessage message = new StatsMessage()//
                    .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                    .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024).setPlayers(Groups.player.size())
                    .setMapName(mapName)//
                    .setMapData(mapData);

            event.response(message);
        });
    }
}
