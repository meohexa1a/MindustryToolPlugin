package mindustrytool;

import arc.*;
import arc.util.*;
import arc.util.CommandHandler.*;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.*;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustrytool.commands.ServerCommands;
import mindustrytool.handlers.VoteHandler;
import mindustrytool.messages.StatsMessage;

import static mindustrytool.MindustryToolPlugin.*;

public class ServerController implements ApplicationListener {

    public static ServerController instance;

    public static final CommandHandler handler = new CommandHandler("");
    public static final VoteHandler voteHandler = new VoteHandler();

    public static volatile boolean autoPaused = false;
    public static Gamemode lastMode;
    public static boolean inGameOverWait = false;
    private int serverMaxTps = 120;

    public ServerController() {
        instance = this;
        setup();

    }

    protected void setup() {
        Core.settings.defaults("bans", "", "admins", "", "shufflemode", "custom", "globalrules",
                "{reactorExplosions: false, logicUnitBuild: false}");

        ServerCommands.registerCommands(handler);

        registerHandler();

        try {
            lastMode = Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival"));
        } catch (Exception event) {
            lastMode = Gamemode.survival;
        }

        Time.setDeltaProvider(() -> Core.graphics.getDeltaTime() * serverMaxTps);

        Events.on(EventType.PlayerLeave.class, event -> {
            Player player = event.player;
            voteHandler.removeVote(player);
        });

        Events.on(PlayerChatEvent.class, event -> {
            Player player = event.player;
            String message = event.message;

            String chat = Strings.format("[@] => @", player.plainName(), message);

            apiGateway.emit("CHAT_MESSAGE", chat);
        });

        Events.on(PlayerJoin.class, event -> {
            if (Vars.state.isPaused() && autoPaused) {
                Vars.state.set(State.playing);
                autoPaused = false;
            }

            String playerName = event.player != null ? event.player.plainName() : "Unknown";
            String chat = Strings.format("@ joined the server, current players: @", playerName, Groups.player.size());

            apiGateway.emit("CHAT_MESSAGE", chat);

            event.player.sendMessage("Server discord: https://discord.gg/72324gpuCd");
        });

        Events.on(PlayerLeave.class, event -> {
            if (!Vars.state.isPaused() && Groups.player.size() == 1) {
                Vars.state.set(State.paused);
                autoPaused = true;
            }

            String playerName = event.player != null ? event.player.plainName() : "Unknown";
            String chat = Strings.format("@ leaved the server, current players: @", playerName,
                    Groups.player.size() - 1);

            apiGateway.emit("CHAT_MESSAGE", chat);
        });

        Events.on(GameOverEvent.class, event -> {

            String message = Vars.state.rules.waves
                    ? Strings.format("Game over! Reached wave @ with @ players online on map @.", Vars.state.wave,
                            Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()))
                    : Strings.format("Game over! Team @ is victorious with @ players online on map @.",
                            event.winner.name, Groups.player.size(), Strings.capitalize(Vars.state.map.plainName()));

            apiGateway.emit("CHAT_MESSAGE", message);
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

    public void registerHandler() {
        apiGateway.on("DISCORD_MESSAGE", String.class, event -> Call.sendMessage(event.getPayload()));

        String mapName = Vars.state.map == null ? "" : Vars.state.map.plainName();

        apiGateway.on("STATS", String.class, event -> {
            StatsMessage message = new StatsMessage()//
                    .setRamUsage(Core.app.getJavaHeap() / 1024 / 1024)
                    .setTotalRam(Runtime.getRuntime().maxMemory() / 1024 / 1024).setPlayers(Groups.player.size())
                    .setMapName(mapName);

            event.response(message);
        });
    }
}
