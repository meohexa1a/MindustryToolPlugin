package mindustrytool.handlers;

public class HostHandler extends ServerMessageHandler<Void> {

    public HostHandler() {
        super("HOST", Void.class, (event) -> {

        });
    }

}
