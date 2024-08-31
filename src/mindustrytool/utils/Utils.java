package mindustrytool.utils;

import arc.util.Log;
import mindustrytool.Config;

public class Utils {

    public static void executeExpectError(Runnable runnable) {
        try {
            Config.BACKGROUND_TASK_EXECUTOR.execute(runnable);
        } catch (Exception e) {
            Log.err(e);
        }
    }
}
