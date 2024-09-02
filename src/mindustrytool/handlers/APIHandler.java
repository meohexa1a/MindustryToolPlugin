package mindustrytool.handlers;

import java.util.List;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.Administration.PlayerInfo;
import mindustrytool.APIGateway;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.response.StatsMessageResponse;
import mindustrytool.utils.HudUtils;

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
            event.response(true);
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

        apiGateway.on("SET_PLAYER", SetPlayerMessageRequest.class, event -> {
            var uuid = event.getPayload().getUuid();
            var isAdmin = event.getPayload().isAdmin();
            var loginLink = event.getPayload().getLoginLink();

            PlayerInfo target = Vars.netServer.admins.getInfoOptional(uuid);
            Player playert = Groups.player.find(p -> p.getInfo() == target);

            if (target != null) {
                if (isAdmin) {
                    Vars.netServer.admins.adminPlayer(target.id, playert == null ? target.adminUsid : playert.usid());
                } else {
                    Vars.netServer.admins.unAdminPlayer(target.id);
                }
                if (playert != null)
                    playert.admin = isAdmin;
                Log.info("Changed admin status of player: @", target.plainLastName());
            } else {
                Log.err("Nobody with that name or ID could be found. If adding an admin by name, make sure they're online; otherwise, use their UUID.");
            }

            if (loginLink != null && !loginLink.isEmpty()) {
                var options = List.of(//
                        HudUtils.option((player, state) -> Call.openURI(player.con, loginLink), "[green]Login"),
                        HudUtils.option((player, state) -> HudUtils.closeFollowDisplay(player, HudUtils.LOGIN_UI),
                                "[red]Close"));

                HudUtils.showFollowDisplay(playert, HudUtils.LOGIN_UI, "[green]Login", "", null, options);
            } else {
                HudUtils.closeFollowDisplay(playert, HudUtils.LOGIN_UI);
                playert.sendMessage("[green]Logged in successfully");
            }
        });

    }

    private StatsMessageResponse getStats() {
        var map = Vars.state.map;

        String mapName = "";

        if (map != null) {
            mapName = map.name();
        }

        int players = Groups.player.size();

        return new StatsMessageResponse()//
                .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024)//
                .setPlayers(players)//
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
