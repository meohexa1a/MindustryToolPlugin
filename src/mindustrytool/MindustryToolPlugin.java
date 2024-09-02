package mindustrytool;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import arc.*;
import arc.util.*;
import arc.util.CommandHandler.Command;
import arc.util.CommandHandler.CommandResponse;
import arc.util.CommandHandler.ResponseType;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.maps.Maps.ShuffleMode;
import mindustry.mod.*;
import mindustry.net.Administration.Config;
import mindustrytool.error.NotJsonException;
import mindustrytool.handlers.APIHandler;
import mindustrytool.handlers.ClientCommandHandler;
import mindustrytool.handlers.EventHandler;
import mindustrytool.handlers.ServerCommandHandler;
import mindustrytool.handlers.VoteHandler;
import mindustrytool.messages.NotMessageException;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.VPNUtils;

public class MindustryToolPlugin extends Plugin {

    public static final APIGateway apiGateway = new APIGateway();
    public static final VoteHandler voteHandler = new VoteHandler();
    public static final APIHandler apiHandler = new APIHandler();
    public static final EventHandler eventHandler = new EventHandler();
    public static final CommandHandler handler = new CommandHandler("");
    public static final ClientCommandHandler clientCommandHandler = new ClientCommandHandler();
    public static final ServerCommandHandler serverCommandHandler = new ServerCommandHandler();

    @Override
    public void init() {

        Core.settings.defaults("bans", "", "admins", "", "shufflemode", "custom", "globalrules",
                "{reactorExplosions: false, logicUnitBuild: false}");

        // update log level
        Config.debug.set(Config.debug.bool());

        Time.setDeltaProvider(() -> Math.min(Core.graphics.getDeltaTime() * 60f, 60));

        Vars.customMapDirectory.mkdirs();

        // set up default shuffle mode
        try {
            Vars.maps.setShuffleMode(ShuffleMode.valueOf(Core.settings.getString("shufflemode")));
        } catch (Exception e) {
            Vars.maps.setShuffleMode(ShuffleMode.all);
        }

        Timer.schedule(() -> System.gc(), 0, 60);

        Runnable inputReader = () -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    try {
                        apiGateway.handleMessage(line);
                    } catch (NotJsonException ignored) {
                    } catch (NotMessageException ignored) {
                        handleCommandString(line);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.out.println("Server closed");
                System.exit(0);
            }
        };

        var inputThread = new Thread(inputReader, "InputThread");

        inputThread.setDaemon(true);
        inputThread.start();

        eventHandler.init();
        apiHandler.registerHandler(apiGateway);

        HudUtils.init();
        VPNUtils.init();
        Vars.mods.eachClass(p -> p.registerServerCommands(handler));

        if (Version.build == -1) {
            Log.warn("&lyYour server is running a custom build, which means that client checking is disabled.");
            Log.warn(
                    "&lyIt is highly advised to specify which version you're using by building with gradle args &lb&fb-Pbuildversion=&lr<build>");
        }
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
                Log.err(line + " Command not found. Did you mean \"" + closest.text + "\"?");
            } else {
                Log.err(line + " Invalid command. Type 'help' for help.");
            }
        } else if (response.type == ResponseType.fewArguments) {
            Log.err("Too few command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        } else if (response.type == ResponseType.manyArguments) {
            Log.err("Too many command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        serverCommandHandler.registerCommands(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        clientCommandHandler.registerCommands(handler);
    }
}
