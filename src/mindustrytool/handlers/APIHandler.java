package mindustrytool.handlers;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustrytool.APIGateway;
import mindustrytool.Config;
import mindustrytool.messages.response.StatsMessageResponse;

public class APIHandler {

    private final String TEMP_SAVE_NAME = "TempSave";

    public void registerHandler(APIGateway apiGateway) {
        apiGateway.on("DISCORD_MESSAGE", String.class, event -> Call.sendMessage(event.getPayload()));

        apiGateway.on("STATS", String.class, event -> {
            StatsMessageResponse message = getStats();

            event.response(message);
        });
        apiGateway.on("DETAIL_STATS", String.class, event -> {
            StatsMessageResponse message = getDetailStats();

            event.response(message);
        });

        apiGateway.on("SERVER_LOADED", String.class, event -> {
            event.response(Config.isLoaded);
        });

        apiGateway.on("START", String.class, event -> {
            String[] data = event.getPayload().split(" ");

            String mapName = data[0];
            String gameMode = data[1];

            if (Vars.state.isGame()) {
                Log.err("Already hosting. Type 'stop' to stop hosting first.");
                return;
            }

            Gamemode preset = Gamemode.survival;

            if (gameMode != null) {
                try {
                    preset = Gamemode.valueOf(gameMode);
                } catch (IllegalArgumentException e) {
                    Log.err("No gamemode '@' found.", gameMode);
                    return;
                }
            }

            Map result = Vars.maps.all().find(map -> map.plainName().replace('_', ' ')
                    .equalsIgnoreCase(Strings.stripColors(mapName).replace('_', ' ')));

            if (result == null) {
                Log.err("No map with name '@' found.", mapName);
                return;
            }

            Log.info("Loading map...");

            Vars.logic.reset();
            try {
                Vars.world.loadMap(result, result.applyRules(preset));
                Vars.state.rules = result.applyRules(preset);
                Vars.logic.play();

                Log.info("Map loaded.");

                Vars.netServer.openServer();

            } catch (MapException e) {
                Log.err("@: @", e.map.plainName(), e.getMessage());
            }

            event.response("STARTED");
        });
    }

    private StatsMessageResponse getStats() {
        var map = Vars.state.map;

        String mapName = "";

        if (map != null) {
            mapName = map.name();
        }

        return new StatsMessageResponse()//
                .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024)//
                .setPlayers(Groups.player.size())//
                .setMapName(mapName);

    }

    private StatsMessageResponse getDetailStats() {
        var map = Vars.state.map;

        byte[] mapData = {};

        if (map != null) {
            var pix = MapIO.generatePreview(Vars.world.tiles);
            Fi file = Fi.tempFile(TEMP_SAVE_NAME);
            file.writePng(pix);

            mapData = file.readBytes();
        }

        return getStats().setMapData(mapData);

    }
}
