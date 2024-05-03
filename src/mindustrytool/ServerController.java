package mindustrytool;

import arc.*;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.serialization.*;
import mindustry.core.GameState.*;
import mindustry.Vars;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.*;
import mindustry.maps.Maps.*;
import mindustry.mod.Mods.*;
import mindustry.net.Administration.*;
import mindustry.net.Packets.*;
import mindustrytool.commands.base.ServerCommands;
import mindustry.net.*;
import java.time.format.*;

public class ServerController implements ApplicationListener {
    protected static DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public static ServerController instance;

    public final CommandHandler handler = new CommandHandler("");

    public static volatile boolean autoPaused = false;
    public static Gamemode lastMode;
    private int serverMaxTps = 60;

    public ServerController() {
        instance = this;
        setup();

    }

    protected void setup() {
        Core.settings.defaults(
                "bans", "",
                "admins", "",
                "shufflemode", "custom",
                "globalrules", "{reactorExplosions: false, logicUnitBuild: false}");

        Config.debug.set(Config.debug.bool());
        Vars.customMapDirectory.mkdirs();

        ServerCommands.registerCommands(handler);

        try {
            lastMode = Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival"));
        } catch (Exception event) {
            lastMode = Gamemode.survival;
        }

        Time.setDeltaProvider(() -> Core.graphics.getDeltaTime() * serverMaxTps);

        if (!Vars.mods.orderedMods().isEmpty()) {
            Log.info("@ mods loaded.", Vars.mods.orderedMods().size);
        }

        int unsupported = Vars.mods.list().count(mod -> !mod.enabled());

        if (unsupported > 0) {
            Log.err("There were errors loading @ mod(s):", unsupported);
            for (LoadedMod mod : Vars.mods.list().select(mod -> !mod.enabled())) {
                Log.err("- @ &ly(@)", mod.state, mod.meta.name);
            }
        }

        if (Version.build == -1) {
            Log.warn("&lyYour server is running a custom build, which means that client checking is disabled.");
            Log.warn(
                    "&lyIt is highly advised to specify which version you're using by building with gradle args &lb&fb-Pbuildversion=&lr<build>");
        }

        try {
            Vars.maps.setShuffleMode(ShuffleMode.valueOf(Core.settings.getString("shufflemode")));
        } catch (Exception event) {
            Vars.maps.setShuffleMode(ShuffleMode.all);
        }

        Events.on(GameOverEvent.class, event -> {

            int playerCount = Groups.player.size();
            String plainMapName = Strings.capitalize(Vars.state.map.plainName());

            if (Vars.state.rules.waves) {
                int wave = Vars.state.wave;

                Log.info("Game over! Reached wave @ with @ players online on map @.", wave, playerCount,
                        plainMapName);
            } else {
                String winner = event.winner.name;

                Log.info("Game over! Team @ is victorious with @ players online on map @.", winner, playerCount,
                        plainMapName);
            }

            Map map = Vars.maps.getNextMap(lastMode, Vars.state.map);

            if (map != null) {
                boolean isPvp = Vars.state.rules.pvp ? true : false;

                String message = "";

                if (isPvp) {
                    String winnerName = event.winner.coloredName();

                    message = Strings.format("[accent]The @ team is victorious![]\n", winnerName);
                } else {
                    String mapName = map.name();
                    String author = map.hasTag("author") ? "by[accent] " + map.author() + "[white]" : "";

                    message = Strings.format("""
                            [scarlet]Game over![]
                            Next selected map: [accent] @ [white] @
                            New game begins in @ seconds.
                            """, mapName, author, Config.roundExtraTime.num());

                }
                Call.infoMessage(message);
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

        Events.on(PlayEvent.class, event -> {
            try {
                JsonValue value = JsonIO.json.fromJson(null, Core.settings.getString("globalrules"));
                JsonIO.json.readFields(Vars.state.rules, value);
            } catch (Throwable t) {
                Log.err("Error applying custom rules, proceeding without them.", t);
            }
        });

        Events.on(SaveLoadEvent.class, event -> {
            Core.app.post(() -> {
                if (Config.autoPause.bool() && Groups.player.size() == 0) {
                    Vars.state.set(State.paused);
                    autoPaused = true;
                }
            });
        });

        // Default to auto pause
        Events.on(PlayerJoin.class, event -> {
            if (Vars.state.isPaused() && autoPaused) {
                Vars.state.set(State.playing);
                autoPaused = false;
            }
        });

        Events.on(PlayerLeave.class, event -> {
            if (!Vars.state.isPaused() && Groups.player.size() == 1) {
                Vars.state.set(State.paused);
                autoPaused = true;
            }
        });
    }

    public void handleCommandString(String line) {
        CommandResponse response = handler.handleMessage(line);

        if (response.type == ResponseType.unknownCommand) {

            int minDst = 0;
            Command closest = null;

            for (Command command : handler.getCommandList()) {
                int dst = Strings.levenshtein(command.text, response.runCommand);
                if (dst < 3 && (closest == null || dst < minDst)) {
                    minDst = dst;
                    closest = command;
                }
            }

            if (closest != null && !closest.text.equals("yes")) {
                Log.err("Command not found. Did you mean \"" + closest.text + "\"?");
            } else {
                Log.err("Invalid command. Type 'help' for help.");
            }
        } else if (response.type == ResponseType.fewArguments) {
            Log.err("Too few command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        } else if (response.type == ResponseType.manyArguments) {
            Log.err("Too many command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        }
    }

    public void setNextMapOverride(Map map) {
        Vars.maps.setNextMapOverride(map);
    }

    public void play(Runnable run) {
        try {
            WorldReloader reloader = new WorldReloader();
            reloader.begin();

            run.run();

            Vars.state.rules = Vars.state.map.applyRules(lastMode);
            Vars.logic.play();

            reloader.end();
        } catch (MapException event) {
            Log.err("@: @", event.map.plainName(), event.getMessage());
            Vars.net.closeServer();
        }
    }
}
