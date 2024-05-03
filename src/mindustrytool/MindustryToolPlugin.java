package mindustrytool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import arc.*;
import arc.util.*;
import mindustry.mod.*;
import mindustry.server.ServerControl;
import mindustrytool.commands.ClientCommands;

public class MindustryToolPlugin extends Plugin {

    public static final APIGateway apiGateway = new APIGateway();
    public static final ServerController serverController = new ServerController();

    @Override
    public void init() {
        removeDefaultServerControl();
        addCustomServerControl();

        List<String> data = new ArrayList<>();

        apiGateway.handle("HELLO", data.getClass(), event -> {
            event.response("Data");
        });

        Core.app.addListener(serverController);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        ClientCommands.registerCommands(handler);
    }

    private void removeDefaultServerControl() {
        ApplicationListener serverControl = Core.app
                .getListeners()
                .find(listener -> listener instanceof ServerControl);

        String[] setNullFields = { "serverInput" };

        if (serverControl != null) {
            try {

                for (String fieldName : setNullFields) {

                    Field field = ServerControl.class.getDeclaredField(fieldName);

                    field.setAccessible(true);
                    field.set(serverControl, null);
                }

            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
                e.printStackTrace();
            }
            Core.app.removeListener(serverControl);
            Log.info("Removed listener: " + serverControl.toString());

        }

        Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("Server Controls"))
                .forEach(thread -> {
                    thread.interrupt();
                    Log.info("Killed thread: " + thread.getName());
                });

    }

    private void addCustomServerControl() {
        Runnable inputReader = () -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    try {
                        apiGateway.handleMessage(line);
                    } catch (Exception ignored) {
                        serverController.handleCommandString(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        var inputThread = new Thread(inputReader, "InputThread");

        inputThread.setDaemon(true);
        inputThread.start();
    }
}
