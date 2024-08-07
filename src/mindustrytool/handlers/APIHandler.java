package mindustrytool.handlers;

import arc.Core;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustrytool.APIGateway;
import mindustrytool.messages.StatsMessage;

public class APIHandler {

    private final String TEMP_SAVE_NAME = "TempSave";

    public void registerHandler(APIGateway apiGateway) {
        apiGateway.on("DISCORD_MESSAGE", String.class, event -> Call.sendMessage(event.getPayload()));

        apiGateway.on("STATS", String.class, event -> {
            var map = Vars.state.map;

            String mapName = "";
            String mapData = "";

            if (map != null) {
                Vars.control.saves.getSaveSlots()//
                        .select(s -> s.getName().equals(TEMP_SAVE_NAME))//
                        .forEach(s -> s.delete());

                var save = Vars.control.saves.addSave(TEMP_SAVE_NAME);
                mapData = new String(save.file.readBytes());
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
