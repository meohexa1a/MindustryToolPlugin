package mindustrytool.handlers;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timer;
import arc.util.Timer.Task;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustrytool.MindustryToolPlugin;
import mindustrytool.messages.request.PlayerMessageRequest;
import mindustrytool.messages.request.SetPlayerMessageRequest;
import mindustrytool.type.Team;
import mindustrytool.utils.Effects;
import mindustrytool.utils.Session;

public class ClientCommandHandler {

    private static boolean isPreparingForNewWave = false;
    private static short waveVoted = 0;

    public void registerCommands(CommandHandler handler) {
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

            Seq<Map> maps = MindustryToolPlugin.voteHandler.getMaps();

            if (mapId < 0 || mapId > (maps.size - 1)) {
                player.sendMessage("[red]Invalid map id");
                return;
            }
            if (MindustryToolPlugin.voteHandler.isVoted(player, mapId)) {
                Call.sendMessage("[red]RTV: " + player.name + " [accent]removed their vote for [yellow]"
                        + maps.get(mapId).name());
                MindustryToolPlugin.voteHandler.removeVote(player, mapId);
                return;
            }
            MindustryToolPlugin.voteHandler.vote(player, mapId);
            Call.sendMessage("[red]RTV: [accent]" + player.name() + " [white]Want to change map to [yellow]"
                    + maps.get(mapId).name());
            Call.sendMessage("[red]RTV: [white]Current Vote for [yellow]" + maps.get(mapId).name() + "[white]: [green]"
                    + MindustryToolPlugin.voteHandler.getVoteCount(mapId) + "/"
                    + MindustryToolPlugin.voteHandler.getRequire());
            Call.sendMessage("[red]RTV: [white]Use [yellow]/rtv " + mapId + " [white]to add your vote to this map !");
            MindustryToolPlugin.voteHandler.check(mapId);
        });

        handler.<Player>register("maps", "[page]", "Display available maps", (args, player) -> {
            final int MAPS_PER_PAGE = 10;
            Seq<Map> maps = MindustryToolPlugin.voteHandler.getMaps();
            int page = 1;
            int maxPage = maps.size / MAPS_PER_PAGE + (maps.size % MAPS_PER_PAGE == 0 ? 0 : 1);
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

            if (page < 1 || page > maxPage) {
                player.sendMessage("[red]Invalid page");
                return;
            }

            player.sendMessage("[green]Available maps: [white](" + page + "/" + maxPage + ")");

            for (int i = 0; i < MAPS_PER_PAGE; i++) {
                int mapId = (page - 1) * MAPS_PER_PAGE + i;
                if (mapId > maps.size - 1) {
                    break;
                }
                player.sendMessage("[green]" + mapId + " [white]- [yellow]" + maps.get(mapId).name());
            }
        });

        handler.<Player>register("servers", "", "Display available servers", (args, player) -> {
            MindustryToolPlugin.eventHandler.sendServerList(player, 0);
        });

        handler.<Player>register("hub", "", "Display available servers", (args, player) -> {
            MindustryToolPlugin.eventHandler.sendHub(player, null);
        });

        handler.<Player>register("js", "<code...>", "Execute JavaScript code.", (args, player) -> {
            if (player.admin) {
                String output = Vars.mods.getScripts().runConsole(args[0]);
                player.sendMessage("> " + (isError(output) ? "[#ff341c]" + output : output));
            } else {
                player.sendMessage("[scarlet]You must be admin to use this command.");
            }
        });

        handler.<Player>register("login", "", "Login", (args, player) -> {
            var team = player.team();
            var request = new PlayerMessageRequest()//
                    .setName(player.coloredName())//
                    .setIp(player.ip())//
                    .setUuid(player.uuid())//
                    .setTeam(new Team()//
                            .setName(team.name)//
                            .setColor(team.color.toString()));

            var playerData = MindustryToolPlugin.apiGateway.execute("PLAYER_JOIN", request,
                    SetPlayerMessageRequest.class);

            var loginLink = playerData.getLoginLink();
            if (loginLink != null && !loginLink.isEmpty()) {
                Call.openURI(player.con, loginLink);
            } else {
                player.sendMessage("Already logged in");
            }
        });

        handler.<Player>register("vnw", "[number]", "Vote for sending a New Wave", (arg, player) -> {
            var session = Session.get(player);

            if (Groups.player.size() < 3 && !player.admin) {
                player.sendMessage("[scarlet]3 players are required or be an admin to start a vote.");
                return;

            } else if (session.votedVNW) {
                player.sendMessage("You have Voted already.");
                return;
            }

            if (arg.length == 1) {
                if (!isPreparingForNewWave) {
                    if (player.admin) {
                        if (Strings.canParseInt(arg[0])) {
                            waveVoted = (short) Strings.parseInt(arg[0]);
                        } else {
                            player.sendMessage("Please select number of wave want to skip");
                            return;
                        }

                    }
                } else {
                    player.sendMessage("A vote to skip wave is already in progress!");
                    return;
                }
            } else if (!isPreparingForNewWave) {
                waveVoted = 1;
            }

            session.votedVNW = true;
            int cur = Session.count(p -> p.votedVNW), req = Mathf.ceil(0.6f * Groups.player.size());
            Call.sendMessage(player.name + "[orange] has voted to "
                    + (waveVoted == 1 ? "send a new wave" : "skip [green]" + waveVoted + " waves") + ". [lightgray]("
                    + (req - cur) + " votes missing)");

            if (!isPreparingForNewWave)
                Timer.schedule(new Task() {
                    @Override
                    public void run() {
                        Call.sendMessage("[scarlet]Vote for "
                                + (waveVoted == 1 ? "sending a new wave" : "skipping [scarlet]" + waveVoted + "[] waves")
                                + " failed! []Not enough votes.");
                        waveVoted = 0;
                        cancel();
                    }

                    @Override
                    public void cancel() {
                        Session.each(p -> p.votedVNW = false);
                        super.cancel();
                    }
                }, 60);

            if (cur < req)
                return;

            Call.sendMessage("[green]Vote for "
                    + (waveVoted == 1 ? "sending a new wave" : "skiping [scarlet]" + waveVoted + "[] waves")
                    + " is Passed. New Wave will be Spawned.");

            if (waveVoted > 0) {
                while (waveVoted-- > 0) {
                    try {
                        Vars.state.wavetime = 0f;
                        Thread.sleep(30);
                    } catch (Exception e) {
                        break;
                    }
                }

            } else
                Vars.state.wave += waveVoted;
        });

        handler.<Player>register("effect", "[list|name|id]", "Gives you a particles effect", (arg, player) -> {
            Seq<Effects> effects = Effects.copy(player.admin, false);
            Effects e;
            StringBuilder builder = new StringBuilder();
            var target = Session.get(player);

            if (arg.length >= 1 && arg[0].equals("list")) {
                if (arg.length == 2 && !Strings.canParseInt(arg[1])) {
                    player.sendMessage("[scarlet]'page' must be a number.");
                    return;
                }

                int page = arg.length == 2 ? Strings.parseInt(arg[1]) : 1, lines = 12,
                        pages = Mathf.ceil(effects.size / lines);
                if (effects.size % lines != 0)
                    pages++;

                if (page > pages || page < 0) {
                    player.sendMessage(
                            "[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                    return;
                }

                player.sendMessage("\n[orange]---- [gold]Effects list [lightgray]" + page + "[gray]/[lightgray]" + pages
                        + "[orange] ----");
                for (int i = (page - 1) * lines; i < lines * page; i++) {
                    try {
                        e = effects.get(i);
                        builder.append(
                                "  [orange]- [lightgray]ID:[white] " + e.id + "[orange] | [lightgray]Name:[white] "
                                        + e.name + (e.forAdmin ? "[orange] | [scarlet]Admin" : "") + "\n");
                    } catch (Exception err) {
                        break;
                    }
                }
                player.sendMessage(builder.toString());
                return;

            } else if (arg.length == 0) {
                if (target.hasEffect) {
                    target.hasEffect = false;
                    player.sendMessage("[green]Removed particles effect.");
                    return;

                } else if (target.spectate()) {

                    player.sendMessage("Can't start effect in vanish mode!");
                    return;

                } else {
                    target.rainbowed = false;
                    target.hasEffect = true;
                    target.effect = effects.random();

                    player.sendMessage("Randomised effect ...");
                    player.sendMessage("[green]Start particles effect [accent]" + target.effect.id + "[scarlet] - []"
                            + target.effect.name);

                }

            } else if (arg.length == 2) {
                if (player.admin) {
                    if (target.spectate()) {
                        player.sendMessage("Can't start effect in vanish mode!");
                        return;

                    } else {
                        if (target.hasEffect) {
                            target.hasEffect = false;
                            player.sendMessage("[green]Removed particles effect for [accent]" + player.name);

                        } else {
                            if (Strings.canParseInt(arg[0]))
                                e = Effects.getByID(Strings.parseInt(arg[0]) - 1);
                            else
                                e = Effects.getByName(arg[0]);

                            if (e == null) {
                                player.sendMessage("Particle effect don't exist");
                                return;

                            } else if (e.disabled) {
                                player.sendMessage("This particle effect is disabled");
                                return;

                            } else if (e.forAdmin && !player.admin) {
                                player.sendMessage("This particle effect is only for admins");
                                return;

                            } else {
                                target.rainbowed = false;
                                target.hasEffect = true;
                                target.effect = e;

                                player.sendMessage("[green]Start particles effect [accent]" + e.id + "[scarlet] - []"
                                        + e.name + "[] for [accent]" + player.name);
                            }
                        }
                    }
                } else
                    player.sendMessage("You can not use this command");
                return;

            } else {
                if (target.spectate()) {
                    player.sendMessage("Can't start effect in vanish mode!");
                    return;
                }

                if (Strings.canParseInt(arg[0]))
                    e = Effects.getByID(Strings.parseInt(arg[0]) - 1);
                else
                    e = Effects.getByName(arg[0]);

                if (e == null) {
                    player.sendMessage("Particle effect don't exist");
                    return;

                } else if (e.disabled) {
                    player.sendMessage("This particle effect is disabled");
                    return;

                } else if (e.forAdmin && !player.admin) {
                    player.sendMessage("This particle effect is only for admins");
                    return;

                } else {
                    target.rainbowed = false;
                    target.hasEffect = true;
                    target.effect = e;

                    player.sendMessage("[green]Start particles effect [accent]" + e.id + "[scarlet] - []" + e.name);
                }
            }
        });

    }

    private boolean isError(String output) {
        try {
            String errorName = output.substring(0, output.indexOf(' ') - 1);
            Class.forName("org.mozilla.javascript." + errorName);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
