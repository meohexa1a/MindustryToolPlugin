package mindustrytool;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Config {

    public static boolean isHub() {
        return mindustry.net.Administration.Config.all.find(conf -> conf.name.equalsIgnoreCase("port")).num() == 6567;
    }

    public static final String SERVER_IP = "15.235.147.219";

    public static Boolean isLoaded = false;

    public static final Executor BACKGROUND_TASK_EXECUTOR = Executors.newSingleThreadExecutor();
}
