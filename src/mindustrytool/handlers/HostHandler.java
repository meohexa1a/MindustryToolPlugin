package mindustrytool.handlers;

import mindustrytool.io.ServerMessageHandler;

public class HostHandler extends ServerMessageHandler<Void> {

    public HostHandler() {
        super("HOST", Void.class, (event) -> {

        });
    }

}
