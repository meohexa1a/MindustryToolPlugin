package mindustrytool.utils;

import java.util.Locale;

import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.ObjectMap;
import mindustry.gen.Player;

public class Session {
    private static ObjectMap<Player, Session> data = new ObjectMap<>();

    public final Player player;
    public final Locale locale;

    public mindustry.game.Team spectate = null;
    public Effects effect = Effects.getByName("none");
    public mindustry.gen.Unit lastUnit;
    public String tag = "", noColorTag = "", rainbowedName = "";
    public int hue = 0;
    public boolean votedVNW = false, //
            votedRTV = false, //
            rainbowed = false, //
            hasEffect = false, //
            isMuted = false, //
            inGodmode = false, //
            isCreator;

    private Session(Player p) {
        this.player = p;
        this.lastUnit = p.unit();
        this.locale = Locale.forLanguageTag(p.locale().replace('_', '-'));
    }

    public boolean spectate() {
        return this.spectate != null;
    }

    public void reset() {
        Session newData = new Session(this.player);

        if (spectate())
            this.player.team(this.spectate);
        this.player.unit().health = this.player.unit().maxHealth;
        this.lastUnit = newData.lastUnit;
        this.spectate = newData.spectate;
        this.effect = newData.effect;
        this.hue = newData.hue;
        this.votedVNW = newData.votedVNW;
        this.votedRTV = newData.votedRTV;
        this.rainbowed = newData.rainbowed;
        this.hasEffect = newData.hasEffect;
        this.isMuted = newData.isMuted;
        this.inGodmode = newData.inGodmode;
        this.isCreator = newData.isCreator;
    }

    public static Session getByName(String name) {
        return find(p -> p.player.name.equals(name));
    }

    public static Session getByID(String id) {
        return find(p -> p.player.uuid().equals(id));
    }

    public static Session get(Player p) {
        if (p == null)
            return null;
        return data.get(p);
    }

    public static Session put(Player p) {
        Session data_ = new Session(p);

        data.put(p, data_);

        return data_;
    }

    public static Session remove(Player p) {
        Session data_ = get(p);

        data.remove(p);

        return data_;
    }

    public static boolean contains(Player p) {
        return data.containsKey(p);
    }

    public static void each(Cons<Session> item) {
        data.each((k, v) -> item.get(v));
    }

    public static void each(Boolf<Session> pred, Cons<Session> item) {
        data.each((k, v) -> {
            if (pred.get(v))
                item.get(v);
        });
    }

    public static int count(Boolf<Session> pred) {
        int size = 0;

        for (Session p : data.values()) {
            if (pred.get(p))
                size++;
        }

        return size;
    }

    public static Session find(Boolf<Session> pred) {
        for (Session p : data.values()) {
            if (pred.get(p))
                return p;
        }

        return null;
    }
}
