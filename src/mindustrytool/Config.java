package mindustrytool;

public class Config {

    public static boolean isHub() {
        return mindustry.net.Administration.Config.all.find(conf -> conf.name.equalsIgnoreCase("port")).num() == 6567;
    }

    public static final String SERVER_IP = "15.235.147.219";

    public static Boolean isLoaded = false;
}
