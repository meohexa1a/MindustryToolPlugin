package mindustrytool;

import arc.*;
import arc.util.*;
import arc.util.CommandHandler.*;
import mindustry.Vars;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.maps.Maps.*;
import mindustry.mod.Mods.*;
import mindustrytool.commands.ServerCommands;
import mindustrytool.handlers.VoteHandler;
import java.time.format.*;

public class ServerController implements ApplicationListener {
    protected static DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public static ServerController instance;

    public static final CommandHandler handler = new CommandHandler("");
    public static final VoteHandler voteHandler = new VoteHandler();

    public static volatile boolean autoPaused = false;
    public static Gamemode lastMode;
    public static boolean inGameOverWait = false;
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

        Events.on(EventType.PlayerLeave.class, event -> {
            Player player = event.player;
            voteHandler.removeVote(player);
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
}
