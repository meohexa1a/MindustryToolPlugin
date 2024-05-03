package mindustrytool.io;

import arc.func.Cons;
import mindustrytool.type.ServerMessageEvent;

public class ServerMessageHandler<T> {
    private final String method;
    private final Class<T> clazz;
    private final Cons<ServerMessageEvent<T>> exec;

    public String getMethod() {
        return method;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public Cons<ServerMessageEvent<T>> getExec() {
        return exec;
    }

    public ServerMessageHandler(String method, Class<T> clazz, Cons<ServerMessageEvent<T>> exec) {
        this.method = method;
        this.clazz = clazz;
        this.exec = exec;
    }

    public void apply(ServerMessageEvent<T> event){
        exec.get(event);
    }
}
