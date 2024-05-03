package mindustrytool.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustrytool.ServerController;

public class ClientCommands {

    public static void registerCommands(CommandHandler handler) {
        handler.<Player>register("rtv", "<mapId>", "Vote to change map (map id in /maps)", (args, player) -> {
            if (args.length != 1) {
                return;
            }

            int mapId;

            try {
                mapId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("[red]Map id must be a number");
                return;
            }

            Seq<Map> maps = Vars.maps.customMaps();

            if (mapId < 0 || mapId > (maps.size - 1)) {
                player.sendMessage("[red]Invalid map id");
                return;
            }
            if (ServerController.voteHandler.isVoted(player, mapId)) {
                Call.sendMessage("[red]RTV: " + player.name + " [accent]removed their vote for [yellow]"
                        + maps.get(mapId).name());
                ServerController.voteHandler.removeVote(player, mapId);
                return;
            }
            ServerController.voteHandler.vote(player, mapId);
            Call.sendMessage("[red]RTV: [accent]" + player.name() + " [white]Want to change map to [yellow]"
                    + maps.get(mapId).name());
            Call.sendMessage("[red]RTV: [white]Current Vote for [yellow]" + maps.get(mapId).name() + "[white]: [green]"
                    + ServerController.voteHandler.getVoteCount(mapId) + "/"
                    + ServerController.voteHandler.getRequire());
            Call.sendMessage("[red]RTV: [white]Use [yellow]/rtv " + mapId + " [white]to add your vote to this map !");
            ServerController.voteHandler.check(mapId);
        });

        handler.<Player>register("maps", "[page]", "Display available maps", (args, player) -> {
            Seq<Map> maps = Vars.maps.customMaps();
            int page = 1;
            int max_page = (maps.size / 5);
            if (args.length == 0) {
                page = 1;

            } else if (args.length == 1) {
                try {
                    page = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage("[red]Page must be a number");
                    return;
                }
            }

            if (page < 1 || page > max_page) {
                player.sendMessage("[red]Invalid page");
                return;
            }

            player.sendMessage("[green]Available maps: [white](" + page + "/" + max_page + ")");

            for (int i = 0; i < 5; i++) {
                int mapId = (page - 1) * 5 + i;
                if (mapId > maps.size - 1) {
                    break;
                }
                player.sendMessage("[green]" + mapId + " [white]- [yellow]" + maps.get(mapId).name());
            }
        });
    }
}
