package mindustrytool.handlers;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.net.Server;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import arc.util.Timer.Task;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.core.Version;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerConnect;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.mod.Mods.LoadedMod;
import mindustry.net.Packets.KickReason;
import mindustrytool.Config;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.messages.request.GetServersMessageRequest;
import mindustrytool.messages.request.PlayerMessageRequest;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.messages.response.GetServersMessageResponse;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Utils;
import mindustrytool.utils.VPNUtils;
import mindustrytool.utils.HudUtils.Option;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.ArcNetProvider;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.WorldReloader;

public class EventHandler {

    public Task lastTask;

    public Gamemode lastMode;
    public boolean inGameOverWait;

    private long lastTimeGetPlayers = 0;
    private int lastPlayers = 0;

    private static final long GET_PLAYERS_DURATION_GAP = 1000 * 30;

    public void init() {
        try {
            lastMode = Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival"));
        } catch (Exception e) { // handle enum parse exception
            lastMode = Gamemode.survival;
        }

        if (!Vars.mods.orderedMods().isEmpty()) {
            Log.info("@ mods loaded.", Vars.mods.orderedMods().size);
        }

        int unsupported = Vars.mods.list().count(l -> !l.enabled());

        if (unsupported > 0) {
            Log.err("There were errors loading @ mod(s):", unsupported);
            for (LoadedMod mod : Vars.mods.list().select(l -> !l.enabled())) {
                Log.err("- @ &ly(" + mod.state + ")", mod.meta.name);
            }
        }

        Vars.net.handleServer(Packets.Connect.class, (con, packet) -> {
            Events.fire(new EventType.ConnectionEvent(con));
            Seq<NetConnection> connections = Seq.with(Vars.net.getConnections())
                    .select(other -> other.address.equals(con.address));
            if (connections.size > Config.MAX_IDENTICAL_IPS) {
                Vars.netServer.admins.blacklistDos(con.address);
                connections.each(NetConnection::close);
                Log.info("@ blacklisted because of ip spam", con.address);
            }
        });

        Events.on(GameOverEvent.class, this::onGameOver);
        Events.on(PlayEvent.class, this::onPlay);
        Events.on(PlayerJoin.class, this::onPlayerJoin);
        Events.on(PlayerLeave.class, this::onPlayerLeave);
        Events.on(PlayerChatEvent.class, this::onPlayerChat);
        Events.on(ServerLoadEvent.class, this::onServerLoad);
        Events.on(PlayerConnect.class, this::onPlayerConnect);
        Events.run(EventType.Trigger.update, this::onUpdate);

        if (Config.isHub()) {
            setupCustomServerDiscovery();
        }
    }

    private void onPlayerConnect(PlayerConnect event) {
        if (VPNUtils.isBot(event.player)) {
            event.player.kick(Packets.KickReason.typeMismatch);

        }
    }

    private void setupCustomServerDiscovery() {
        try {
            var providerField = Net.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            var provider = (ArcNetProvider) providerField.get(Vars.net);
            var serverField = ArcNetProvider.class.getDeclaredField("server");
            serverField.setAccessible(true);
            var server = (Server) serverField.get(provider);

            server.setDiscoveryHandler((address, handler) -> {
                String name = mindustry.net.Administration.Config.serverName.string();
                String description = mindustry.net.Administration.Config.desc.string();
                String map = Vars.state.map.name();

                ByteBuffer buffer = ByteBuffer.allocate(500);

                int players = lastPlayers;

                if (System.currentTimeMillis() - lastTimeGetPlayers > GET_PLAYERS_DURATION_GAP)
                    try {
                        players = MindustryToolPlugin.apiGateway.execute("PLAYERS", "", Integer.class);
                        lastTimeGetPlayers = System.currentTimeMillis();
                        lastPlayers = players;
                    } catch (Exception e) {
                        Log.err(e);
                    }

                writeString(buffer, name, 100);
                writeString(buffer, map, 64);

                buffer.putInt(Core.settings.getInt("totalPlayers", players));
                buffer.putInt(Vars.state.wave);
                buffer.putInt(Version.build);
                writeString(buffer, Version.type);

                buffer.put((byte) Vars.state.rules.mode().ordinal());
                buffer.putInt(Vars.netServer.admins.getPlayerLimit());

                writeString(buffer, description, 100);
                if (Vars.state.rules.modeName != null) {
                    writeString(buffer, Vars.state.rules.modeName, 50);
                }
                buffer.position(0);
                handler.respond(buffer);
            });

        } catch (Exception e) {
            Log.err(e);
        }
    }

    private static void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, 32);
    }

    private static void writeString(ByteBuffer buffer, String string, int maxlen) {
        byte[] bytes = string.getBytes(Vars.charset);
        if (bytes.length > maxlen) {
            bytes = Arrays.copyOfRange(bytes, 0, maxlen);
        }

        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    public void onUpdate() {
        Groups.player.each(p -> {
            if (p.unit().moving()) {
                var effect = Effect.all.get((int) Math.random() % Effect.all.size);
                Call.effect(effect, p.x, p.y, 0, Color.white);
            }
        });
    }

    public void onServerLoad(ServerLoadEvent event) {
        Config.isLoaded = true;
    }

    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.player;
        String message = event.message;

        String chat = Strings.format("[@] => @", player.plainName(), message);

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
    }

    public void onPlayerLeave(PlayerLeave event) {
        if (!Vars.state.isPaused() && Groups.player.size() == 1) {
            Vars.state.set(State.paused);
        }

        Player player = event.player;
        MindustryToolPlugin.voteHandler.removeVote(player);

        String playerName = event.player != null ? event.player.plainName() : "Unknown";
        String chat = Strings.format("@ leaved the server, current players: @", playerName, Groups.player.size() - 1);

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
        MindustryToolPlugin.apiGateway.emit("PLAYER_LEAVE", new PlayerMessageRequest()//
                .setName(playerName)//
                .setIp(event.player.ip())//
                .setUuid(event.player.uuid()));
    }

    public void onPlayerJoin(PlayerJoin event) {
        if (Vars.state.isPaused()) {
            Vars.state.set(State.playing);
        }

        var player = event.player;
        String playerName = player != null ? player.plainName() : "Unknown";
        String chat = Strings.format("@ joined the server, current players: @", playerName, Groups.player.size());

        var request = new PlayerMessageRequest()//
                .setName(player.coloredName())//
                .setIp(player.ip())//
                .setUuid(player.uuid());

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
        MindustryToolPlugin.apiGateway.emit("PLAYER_JOIN", request);

        var playerData = MindustryToolPlugin.apiGateway.execute("GET_PLAYER_DATA", request,
                SetPlayerMessageRequest.class);

        if (Config.isHub()) {
            sendHub(event.player, playerData);
        } else {
            if (playerData.getLoginLink() != null) {
                player.sendMessage("[green]Login successfully");
            } else {

            }
        }

        var name = playerData.getName();
        var uuid = playerData.getUuid();
        var isAdmin = playerData.isAdmin();

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

        if (name != null && !name.isEmpty()) {
            player.name(name);
        }
    }

    public void onPlay(PlayEvent event) {
        try {
            JsonValue value = JsonIO.json.fromJson(null, Core.settings.getString("globalrules"));
            JsonIO.json.readFields(Vars.state.rules, value);
        } catch (Throwable t) {
            Log.err("Error applying custom rules, proceeding without them.", t);
        }
    }

    public void onGameOver(GameOverEvent event) {
        if (inGameOverWait) {
            return;
        }

        if (Vars.state.rules.waves) {
            Log.info("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave, Groups.player.size(),
                    Strings.capitalize(Vars.state.map.plainName()));
        } else {
            Log.info("Game over! Team @ is victorious with @ players online on map @.", event.winner.name,
                    Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));
        }

        // set the next map to be played
        Map map = Vars.maps.getNextMap(lastMode, Vars.state.map);
        if (map != null) {
            Call.infoMessage(
                    (Vars.state.rules.pvp ? "[accent]The " + event.winner.coloredName() + " team is victorious![]\n"
                            : "[scarlet]Game over![]\n") + "\nNext selected map: [accent]" + map.name() + "[white]"
                            + (map.hasTag("author") ? " by[accent] " + map.author() + "[white]" : "") + "."
                            + "\nNew game begins in " + mindustry.net.Administration.Config.roundExtraTime.num()
                            + " seconds.");

            Vars.state.gameOver = true;
            Call.updateGameOver(event.winner);

            Log.info("Selected next map to be @.", map.plainName());

            play(() -> Vars.world.loadMap(map, map.applyRules(lastMode)));
        } else {
            Vars.netServer.kickAll(KickReason.gameover);
            Vars.state.set(State.menu);
            Vars.net.closeServer();
        }

        String message = Vars.state.rules.waves
                ? Strings.format("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave,
                        Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()))
                : Strings.format("Game over! Team @ is victorious with @ players online on map @.", event.winner.name,
                        Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));

        MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", message);
    }

    public void sendHub(Player player, SetPlayerMessageRequest playerData) {
        var loginLink = playerData.getLoginLink();

        var options = new ArrayList<Option>();

        if (loginLink != null && !loginLink.isEmpty()) {
            options.add(HudUtils.option((trigger, state) -> Call.openURI(trigger.con, loginLink),
                    "[green]Login via MindustryTool"));
        } else {
            player.sendMessage("[green]Logged in successfully");
        }

        options.add(HudUtils.option((p, state) -> Call.openURI(player.con, Config.RULE_URL), "[green]Rules"));
        options.add(
                HudUtils.option((p, state) -> Call.openURI(player.con, Config.MINDUSTRY_TOOL_URL), "[green]Website"));
        options.add(
                HudUtils.option((p, state) -> Call.openURI(player.con, Config.DISCORD_INVITE_URL), "[blue]Discord"));
        options.add(HudUtils.option((p, state) -> {
            HudUtils.closeFollowDisplay(p, HudUtils.HUB_UI);
            sendServerList(player, 0);
        }, "[red]Close"));

        HudUtils.showFollowDisplay(player, HudUtils.HUB_UI, "Servers", Config.HUB_MESSAGE, null, options);

        var map = Vars.state.map;
        if (map != null) {
            Call.label(Config.HUB_MESSAGE, 200000, map.width / 2, map.height / 2);
        }
    }

    public void sendServerList(Player player, int page) {
        Utils.executeExpectError(() -> {
            var size = 8;
            var request = new GetServersMessageRequest()//
                    .setPage(page)//
                    .setSize(size);

            var response = MindustryToolPlugin.apiGateway.execute("SERVERS", request, GetServersMessageResponse.class);
            var servers = response.getServers();
            var options = new ArrayList<>(servers.stream()//
                    .map(server -> HudUtils.option((p, state) -> onServerChoose(p, server.getId(), server.getName()),
                            "%s [cyan]Players: %s [green]Map: %s".formatted(//
                                    server.getName(), //
                                    server.getPlayers(),
                                    server.getMapName() == null ? "[red]Not playing" : server.getMapName())))//
                    .toList());

            if (page > 0) {
                options.add(HudUtils.option((p, state) -> {
                    HudUtils.closeFollowDisplay(p, HudUtils.SERVERS_UI);
                    sendServerList(player, (int) state - 1);
                }, "[yellow]Previous"));
            }

            if (servers.size() == size) {
                options.add(HudUtils.option((p, state) -> {
                    HudUtils.closeFollowDisplay(p, HudUtils.SERVERS_UI);
                    sendServerList(player, (int) state + 1);
                }, "[green]Next"));

            }
            options.add(
                    HudUtils.option((p, state) -> HudUtils.closeFollowDisplay(p, HudUtils.SERVERS_UI), "[red]Close"));

            HudUtils.showFollowDisplay(player, HudUtils.SERVERS_UI, "Servers", "", Integer.valueOf(page), options);
        });
    }

    public void onServerChoose(Player player, String id, String name) {
        HudUtils.closeFollowDisplay(player, HudUtils.SERVERS_UI);
        Utils.executeExpectError(() -> {
            player.sendMessage("[green]Starting server [white]%s, [white]redirection will happen soon".formatted(name));
            var data = MindustryToolPlugin.apiGateway.execute("START_SERVER", id, Integer.class);
            player.sendMessage("[green]Redirecting");
            Call.sendMessage("%s [green]redirecting to server [white]%s".formatted(player.coloredName(), name));
            Call.connect(player.con, Config.SERVER_IP, data);
        });
    }

    public void cancelPlayTask() {
        if (lastTask != null)
            lastTask.cancel();
    }

    public void play(Runnable run) {
        play(true, run);
    }

    public void play(boolean wait, Runnable run) {
        inGameOverWait = true;
        cancelPlayTask();

        Runnable reload = () -> {
            try {
                WorldReloader reloader = new WorldReloader();
                reloader.begin();

                run.run();

                Vars.state.rules = Vars.state.map.applyRules(lastMode);
                Vars.logic.play();

                reloader.end();
                inGameOverWait = false;

            } catch (MapException e) {
                Log.err("@: @", e.map.plainName(), e.getMessage());
                Vars.net.closeServer();
            }
        };

        if (wait)

        {
            lastTask = Timer.schedule(reload, mindustry.net.Administration.Config.roundExtraTime.num());
        } else {
            reload.run();
        }
    }

}
