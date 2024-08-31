package mindustrytool.utils;

import mindustry.gen.Call;
import mindustry.gen.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;

import arc.Events;
import arc.util.Log;
import lombok.AllArgsConstructor;
import lombok.Data;
import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerLeave;

public class HudUtils {

    public static final int SERVERS_UI = 1;

    public static final ConcurrentHashMap<Integer, ConcurrentHashMap<String, PlayerPressCallback[]>> menus = new ConcurrentHashMap<>();

    @FunctionalInterface
    public static interface PlayerPressCallback {
        void accept(Player player);
    }

    @Data
    @AllArgsConstructor
    public static class Option {
        PlayerPressCallback callback;
        String text[];
    }

    public static void init() {
        Events.on(PlayerLeave.class, HudUtils::onPlayerLeave);
        Events.on(MenuOptionChooseEvent.class, HudUtils::onMenuOptionChoose);
    }

    public static Option option(PlayerPressCallback callback, String... text) {
        return new Option(callback, text);
    }

    public static void showFollowDisplay(Player player, int id, String title, String description, Option... options) {

        var optionTexts = Arrays.asList(options).stream()//
                .map(option -> option.text)//
                .toArray(String[][]::new);

        var callbacks = Arrays.asList(options).stream()//
                .map(option -> option.callback)//
                .toArray(PlayerPressCallback[]::new);

        Call.followUpMenu(player.con, id, title, description, optionTexts);
        ConcurrentHashMap<String, PlayerPressCallback[]> userMenu = menus.computeIfAbsent(id,
                k -> new ConcurrentHashMap<>());

        userMenu.put(player.uuid(), callbacks);
    }

    public static void onMenuOptionChoose(MenuOptionChooseEvent event) {
        var menu = menus.get(event.menuId);

        if (menu == null) {
            Log.info("Menu not found: " + event.menuId);
            return;
        }

        var callbacks = menu.get(event.player.uuid());
        if (callbacks == null || event.option <= -1 || event.option >= callbacks.length) {
            Log.info("Callback not found: " + event.player.uuid());
            return;
        }

        callbacks[event.option].accept(event.player);
    }

    public static void onPlayerLeave(PlayerLeave event) {
        menus.values().forEach(menu -> {
            menu.remove(event.player.uuid());
        });
    }

    public static void closeFollowDisplay(Player player, int id) {
        Call.hideFollowUpMenu(id);
        menus.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).remove(player.uuid());
    }
}
