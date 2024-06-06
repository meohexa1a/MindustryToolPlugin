package mindustrytool.handlers;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import arc.util.Timer.Task;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.mod.Mods.LoadedMod;
import mindustry.net.Administration.Config;
import mindustry.net.Packets.KickReason;
import mindustrytool.MindustryToolPlugin;
import mindustry.net.WorldReloader;

public class EventHandler {

    public Task lastTask;
    public boolean autoPaused = false;

    public Gamemode lastMode;
    public boolean inGameOverWait;

    public void init() {
        try {
            lastMode = Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival"));
        } catch (Exception e) { // handle enum parse exception
            lastMode = Gamemode.survival;
        }

        Events.on(GameOverEvent.class, event -> {
            if (inGameOverWait) {
                return;
            }

            if (Vars.state.rules.waves) {
                Log.info("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave,
                        Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));
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
                                + "\nNew game begins in " + Config.roundExtraTime.num() + " seconds.");

                Vars.state.gameOver = true;
                Call.updateGameOver(event.winner);

                Log.info("Selected next map to be @.", map.plainName());

                play(() -> Vars.world.loadMap(map, map.applyRules(lastMode)));
            } else {
                Vars.netServer.kickAll(KickReason.gameover);
                Vars.state.set(State.menu);
                Vars.net.closeServer();
            }
        });

        Events.on(PlayEvent.class, e -> {
            try {
                JsonValue value = JsonIO.json.fromJson(null, Core.settings.getString("globalrules"));
                JsonIO.json.readFields(Vars.state.rules, value);
            } catch (Throwable t) {
                Log.err("Error applying custom rules, proceeding without them.", t);
            }
        });

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

        Events.on(PlayerJoin.class, e -> {
            if (Vars.state.isPaused() && autoPaused) {
                Vars.state.set(State.playing);
                autoPaused = false;
            }
        });

        Events.on(PlayerLeave.class, e -> {
            if (!Vars.state.isPaused() && Groups.player.size() == 1) {
                Vars.state.set(State.paused);
                autoPaused = true;
            }
        });

        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;
            MindustryToolPlugin.voteHandler.removeVote(player);
        });

        Events.on(PlayerChatEvent.class, event -> {
            Player player = event.player;
            String message = event.message;

            String chat = Strings.format("[@] => @", player.plainName(), message);

            MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
        });

        Events.on(PlayerJoin.class, event -> {
            String playerName = event.player != null ? event.player.plainName() : "Unknown";
            String chat = Strings.format("@ joined the server, current players: @", playerName, Groups.player.size());

            MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);

            event.player.sendMessage("Server discord: https://discord.gg/72324gpuCd");
        });

        Events.on(PlayerLeave.class, event -> {
            String playerName = event.player != null ? event.player.plainName() : "Unknown";
            String chat = Strings.format("@ leaved the server, current players: @", playerName,
                    Groups.player.size() - 1);

            MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", chat);
        });

        Events.on(GameOverEvent.class, event -> {

            String message = Vars.state.rules.waves
                    ? Strings.format("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave,
                            Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()))
                    : Strings.format("Game over! Team @ is victorious with @ players online on map @.",
                            event.winner.name, Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));

            MindustryToolPlugin.apiGateway.emit("CHAT_MESSAGE", message);
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

                if (!Groups.player.isEmpty()) {
                    Vars.state.set(State.playing);
                }

            } catch (MapException e) {
                Log.err("@: @", e.map.plainName(), e.getMessage());
                Vars.net.closeServer();
            }
        };

        if (wait) {
            lastTask = Timer.schedule(reload, Config.roundExtraTime.num());
        } else {
            reload.run();
        }
    }

}
