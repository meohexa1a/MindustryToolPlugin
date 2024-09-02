package mindustrytool.handlers;

import arc.Core;
import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler.Command;
import arc.util.Log;
import arc.util.CommandHandler;
import arc.util.OS;
import arc.util.Strings;
import arc.util.Structs;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonValue.ValueType;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.core.Version;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.maps.Maps.ShuffleMode;
import mindustry.mod.Mods.LoadedMod;
import mindustry.net.Administration.Config;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets.KickReason;
import mindustry.type.Item;
import mindustrytool.MindustryToolPlugin;

public class ServerCommandHandler {

    public void registerCommands(CommandHandler handler) {
        handler.register("help", "[command]", "Display the command list, or get help for a specific command.", arg -> {
            if (arg.length > 0) {
                Command command = handler.getCommandList().find(c -> c.text.equalsIgnoreCase(arg[0]));
                if (command == null) {
                    Log.err("Command " + arg[0] + " not found!");
                } else {
                    Log.info(command.text + ":");
                    Log.info("  &b&lb " + command.text + (command.paramText.isEmpty() ? "" : " &lc&fi")
                            + command.paramText + "&fr - &lw" + command.description);
                }
            } else {
                Log.info("Commands:");
                for (Command command : handler.getCommandList()) {
                    Log.info("  &b&lb " + command.text + (command.paramText.isEmpty() ? "" : " &lc&fi")
                            + command.paramText + "&fr - &lw" + command.description);
                }
            }
        });

        handler.register("version", "Displays server version info.", arg -> {
            Log.info("Version: Mindustry @-@ @ / build @", Version.number, Version.modifier, Version.type,
                    Version.build + (Version.revision == 0 ? "" : "." + Version.revision));
            Log.info("Java Version: @", OS.javaVersion);
        });

        handler.register("exit", "Exit the server application.", arg -> {
            Log.info("Shutting down server.");
            Vars.net.dispose();
            Core.app.exit();
        });

        handler.register("stop", "Stop hosting the server.", arg -> {
            Vars.net.closeServer();
            Vars.state.set(State.menu);
            Log.info("Stopped server.");
        });

        handler.register("host", "[mapname] [mode]",
                "Open the server. Will default to survival and a random map if not specified.", arg -> {
                    if (Vars.state.isGame()) {
                        Log.err("Already hosting. Type 'stop' to stop hosting first.");
                        return;
                    }
                    Gamemode preset = Gamemode.survival;

                    if (arg.length > 1) {
                        try {
                            preset = Gamemode.valueOf(arg[1]);
                        } catch (IllegalArgumentException event) {
                            Log.err("No gamemode '@' found.", arg[1]);
                            return;
                        }
                    }

                    Map result;
                    if (arg.length > 0) {
                        result = Vars.maps.all().find(map -> map.plainName().replace('_', ' ')
                                .equalsIgnoreCase(Strings.stripColors(arg[0]).replace('_', ' ')));

                        if (result == null) {
                            Log.err("No map with name '@' found.", arg[0]);
                            return;
                        }
                    } else {
                        result = Vars.maps.getShuffleMode().next(preset, Vars.state.map);
                        Log.info("Randomized next map to be @.", result.plainName());
                    }

                    Log.info("Loading map...");

                    Vars.logic.reset();
                    MindustryToolPlugin.eventHandler.lastMode = preset;
                    Core.settings.put("lastServerMode", MindustryToolPlugin.eventHandler.lastMode.name());

                    try {
                        Vars.world.loadMap(result, result.applyRules(MindustryToolPlugin.eventHandler.lastMode));
                        Vars.state.rules = result.applyRules(preset);
                        Vars.logic.play();

                        Log.info("Map loaded.");

                        Vars.netServer.openServer();
                    } catch (MapException event) {
                        Log.err("@: @", event.map.plainName(), event.getMessage());
                    }
                });

        handler.register("maps", "[all/custom/default]",
                "Display available maps. Displays only custom maps by default.", arg -> {
                    boolean custom = arg.length == 0 || arg[0].equals("custom") || arg[0].equals("all");
                    boolean def = arg.length > 0 && (arg[0].equals("default") || arg[0].equals("all"));

                    if (!Vars.maps.all().isEmpty()) {
                        Seq<Map> all = new Seq<>();

                        if (custom)
                            all.addAll(Vars.maps.customMaps());
                        if (def)
                            all.addAll(Vars.maps.defaultMaps());

                        if (all.isEmpty()) {
                            Log.info("No custom maps loaded. &fiTo display built-in maps, use the \"@\" argument.",
                                    "all");
                        } else {
                            Log.info("Maps:");

                            for (Map map : all) {
                                String mapName = map.plainName().replace(' ', '_');
                                if (map.custom) {
                                    Log.info("  @ (@): &fiCustom / @x@", mapName, map.file.name(), map.width,
                                            map.height);
                                } else {
                                    Log.info("  @: &fiDefault / @x@", mapName, map.width, map.height);
                                }
                            }
                        }
                    } else {
                        Log.info("No maps found.");
                    }
                    Log.info("Map directory: &fi@", Vars.customMapDirectory.file().getAbsoluteFile().toString());
                });

        handler.register("reloadmaps", "Reload all maps from disk.", arg -> {
            int beforeMaps = Vars.maps.all().size;
            Vars.maps.reload();
            if (Vars.maps.all().size > beforeMaps) {
                Log.info("@ new map(s) found and reloaded.", Vars.maps.all().size - beforeMaps);
            } else if (Vars.maps.all().size < beforeMaps) {
                Log.info("@ old map(s) deleted.", beforeMaps - Vars.maps.all().size);
            } else {
                Log.info("Maps reloaded.");
            }
        });

        handler.register("status", "Display server status.", arg -> {
            if (Vars.state.isMenu()) {
                Log.info("Status: &rserver closed");
            } else {
                Log.info("Status:");
                Log.info("  Playing on map &fi@ / Wave @", Strings.capitalize(Vars.state.map.plainName()),
                        Vars.state.wave);

                if (Vars.state.rules.waves) {
                    Log.info("  @ seconds until next wave.", (int) (Vars.state.wavetime / 60));
                }
                Log.info("  @ units / @ enemies", Groups.unit.size(), Vars.state.enemies);

                Log.info("  @ FPS, @ MB used.", Core.graphics.getFramesPerSecond(),
                        Core.app.getJavaHeap() / 1024 / 1024);

                if (Groups.player.size() > 0) {
                    Log.info("  Players: @", Groups.player.size());
                    for (Player p : Groups.player) {
                        Log.info("    @ @ / @", p.admin() ? "&r[A]&c" : "&b[P]&c", p.plainName(), p.uuid());
                    }
                } else {
                    Log.info("  No players connected.");
                }
            }
        });

        handler.register("mods", "Display all loaded mods.", arg -> {
            if (!Vars.mods.list().isEmpty()) {
                Log.info("Mods:");
                for (LoadedMod mod : Vars.mods.list()) {
                    Log.info("  @ &fi@ " + (mod.enabled() ? "" : " &lr(" + mod.state + ")"), mod.meta.displayName,
                            mod.meta.version);
                }
            } else {
                Log.info("No mods found.");
            }
            Log.info("Mod directory: &fi@", Vars.modDirectory.file().getAbsoluteFile().toString());
        });

        handler.register("mod", "<name...>", "Display information about a loaded plugin.", arg -> {
            LoadedMod mod = Vars.mods.list().find(p -> p.meta.name.equalsIgnoreCase(arg[0]));
            if (mod != null) {
                Log.info("Name: @", mod.meta.displayName);
                Log.info("Internal Name: @", mod.name);
                Log.info("Version: @", mod.meta.version);
                Log.info("Author: @", mod.meta.author);
                Log.info("Path: @", mod.file.path());
                Log.info("Description: @", mod.meta.description);
            } else {
                Log.info("No mod with name '@' found.", arg[0]);
            }
        });

        handler.register("js", "<script...>", "Run arbitrary Javascript.", arg -> {
            Log.info("&fi&lw&fb" + Vars.mods.getScripts().runConsole(arg[0]));
        });

        handler.register("say", "<message...>", "Send a message to all players.", arg -> {
            if (!Vars.state.isGame()) {
                Log.err("Not hosting. Host a game first.");
                return;
            }

            Call.sendMessage("[scarlet][[Server]:[] " + arg[0]);

            Log.info("&fi&lcServer: &fr@", "&lw" + arg[0]);
        });

        handler.register("rules", "[remove/add] [name] [value...]",
                "List, remove or add global rules. These will apply regardless of map.", arg -> {
                    String rules = Core.settings.getString("globalrules");
                    JsonValue base = JsonIO.json.fromJson(null, rules);

                    if (arg.length == 0) {
                        Log.info("Rules:\n@", JsonIO.print(rules));
                    } else if (arg.length == 1) {
                        Log.err("Invalid usage. Specify which rule to remove or add.");
                    } else {
                        if (!(arg[0].equals("remove") || arg[0].equals("add"))) {
                            Log.err("Invalid usage. Either add or remove rules.");
                            return;
                        }

                        boolean remove = arg[0].equals("remove");
                        if (remove) {
                            if (base.has(arg[1])) {
                                Log.info("Rule '@' removed.", arg[1]);
                                base.remove(arg[1]);
                            } else {
                                Log.err("Rule not defined, so not removed.");
                                return;
                            }
                        } else {
                            if (arg.length < 3) {
                                Log.err("Missing last argument. Specify which value to set the rule to.");
                                return;
                            }

                            try {
                                JsonValue value = new JsonReader().parse(arg[2]);
                                value.name = arg[1];

                                JsonValue parent = new JsonValue(ValueType.object);
                                parent.addChild(value);

                                JsonIO.json.readField(Vars.state.rules, value.name, parent);
                                if (base.has(value.name)) {
                                    base.remove(value.name);
                                }
                                base.addChild(arg[1], value);
                                Log.info("Changed rule: @", value.toString().replace("\n", " "));
                            } catch (Throwable event) {
                                Log.err("Error parsing rule JSON: @", event.getMessage());
                            }
                        }

                        Core.settings.put("globalrules", base.toString());
                        Call.setRules(Vars.state.rules);
                    }
                });

        handler.register("fillitems", "[team]", "Fill the core with items.", arg -> {
            if (!Vars.state.isGame()) {
                Log.err("Not playing. Host first.");
                return;
            }

            Team team = arg.length == 0 ? Team.sharded : Structs.find(Team.all, t -> t.name.equals(arg[0]));

            if (team == null) {
                Log.err("No team with that name found.");
                return;
            }

            if (Vars.state.teams.cores(team).isEmpty()) {
                Log.err("That team has no cores.");
                return;
            }

            for (Item item : Vars.content.items()) {
                var core = Vars.state.teams.cores(team).first();
                var storageCap = core.storageCapacity;
                core.items.set(item, storageCap);
            }

            Log.info("Core filled.");
        });

        handler.register("playerlimit", "[off/somenumber]", "Set the server player limit.", arg -> {
            if (arg.length == 0) {
                Log.info("Player limit is currently @.",
                        Vars.netServer.admins.getPlayerLimit() == 0 ? "off" : Vars.netServer.admins.getPlayerLimit());
                return;
            }
            if (arg[0].equals("off")) {
                Vars.netServer.admins.setPlayerLimit(0);
                Log.info("Player limit disabled.");
                return;
            }

            if (Strings.canParsePositiveInt(arg[0]) && Strings.parseInt(arg[0]) > 0) {
                int lim = Strings.parseInt(arg[0]);
                Vars.netServer.admins.setPlayerLimit(lim);
                Log.info("Player limit is now &lc@.", lim);
            } else {
                Log.err("Limit must be a number above 0.");
            }
        });

        handler.register("config", "[name] [value...]", "Configure server settings.", arg -> {
            if (arg.length == 0) {
                Log.info("All config values:");
                for (Config c : Config.all) {
                    Log.info("&lk| @: @", c.name, "&lc&fi" + c.get());
                    Log.info("&lk| | &lw" + c.description);
                    Log.info("&lk|");
                }
                return;
            }

            Config c = Config.all.find(conf -> conf.name.equalsIgnoreCase(arg[0]));

            if (c != null) {
                if (arg.length == 1) {
                    Log.info("'@' is currently @.", c.name, c.get());
                } else {
                    if (arg[1].equals("default")) {
                        c.set(c.defaultValue);
                    } else if (c.isBool()) {
                        c.set(arg[1].equals("on") || arg[1].equals("true"));
                    } else if (c.isNum()) {
                        try {
                            c.set(Integer.parseInt(arg[1]));
                        } catch (NumberFormatException event) {
                            Log.err("Not a valid number: @", arg[1]);
                            return;
                        }
                    } else if (c.isString()) {
                        c.set(arg[1].replace("\\n", "\n"));
                    }

                    Log.info("@ set to @.", c.name, c.get());
                    Core.settings.forceSave();
                }
            } else {
                Log.err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.",
                        arg[0]);
            }
        });

        handler.register("subnet-ban", "[add/remove] [address]",
                "Ban a subnet. This simply rejects all connections with IPs starting with some string.", arg -> {
                    if (arg.length == 0) {
                        Log.info("Subnets banned: @", Vars.netServer.admins.getSubnetBans().isEmpty() ? "<none>" : "");
                        for (String subnet : Vars.netServer.admins.getSubnetBans()) {
                            Log.info("&lw  " + subnet);
                        }
                    } else if (arg.length == 1) {
                        Log.err("You must provide a subnet to add or remove.");
                    } else {
                        if (arg[0].equals("add")) {
                            if (Vars.netServer.admins.getSubnetBans().contains(arg[1])) {
                                Log.err("That subnet is already banned.");
                                return;
                            }

                            Vars.netServer.admins.addSubnetBan(arg[1]);
                            Log.info("Banned @**", arg[1]);
                        } else if (arg[0].equals("remove")) {
                            if (!Vars.netServer.admins.getSubnetBans().contains(arg[1])) {
                                Log.err("That subnet isn't banned.");
                                return;
                            }

                            Vars.netServer.admins.removeSubnetBan(arg[1]);
                            Log.info("Unbanned @**", arg[1]);
                        } else {
                            Log.err("Incorrect usage. Provide add/remove as the second argument.");
                        }
                    }
                });

        handler.register("whitelist", "[add/remove] [ID]", "Add/remove players from the whitelist using their ID.",
                arg -> {
                    if (arg.length == 0) {
                        Seq<PlayerInfo> whitelist = Vars.netServer.admins.getWhitelisted();

                        if (whitelist.isEmpty()) {
                            Log.info("No whitelisted players found.");
                        } else {
                            Log.info("Whitelist:");
                            whitelist.each(p -> Log.info("- Name: @ / UUID: @", p.plainLastName(), p.id));
                        }
                    } else {
                        if (arg.length == 2) {
                            PlayerInfo info = Vars.netServer.admins.getInfoOptional(arg[1]);

                            if (info == null) {
                                Log.err("Player ID not found. You must use the ID displayed when a player joins a server.");
                            } else {
                                if (arg[0].equals("add")) {
                                    Vars.netServer.admins.whitelist(arg[1]);
                                    Log.info("Player '@' has been whitelisted.", info.plainLastName());
                                } else if (arg[0].equals("remove")) {
                                    Vars.netServer.admins.unwhitelist(arg[1]);
                                    Log.info("Player '@' has been un-whitelisted.", info.plainLastName());
                                } else {
                                    Log.err("Incorrect usage. Provide add/remove as the second argument.");
                                }
                            }
                        } else {
                            Log.err("Incorrect usage. Provide an ID to add or remove.");
                        }
                    }
                });

        handler.register("shuffle", "[none/all/custom/builtin]", "Set map shuffling mode.", arg -> {
            if (arg.length == 0) {
                Log.info("Shuffle mode current set to '@'.", Vars.maps.getShuffleMode());
            } else {
                try {
                    ShuffleMode mode = ShuffleMode.valueOf(arg[0]);
                    Core.settings.put("shufflemode", mode.name());
                    Vars.maps.setShuffleMode(mode);
                    Log.info("Shuffle mode set to '@'.", arg[0]);
                } catch (Exception event) {
                    Log.err("Invalid shuffle mode.");
                }
            }
        });

        handler.register("nextmap", "<mapname...>",
                "Set the next map to be played after a game-over. Overrides shuffling.", arg -> {
                    Map res = Vars.maps.all().find(map -> map.plainName().replace('_', ' ')
                            .equalsIgnoreCase(Strings.stripColors(arg[0]).replace('_', ' ')));
                    if (res != null) {
                        Vars.maps.setNextMapOverride(res);
                        Log.info("Next map set to '@'.", res.plainName());
                    } else {
                        Log.err("No map '@' found.", arg[0]);
                    }
                });

        handler.register("kick", "<username...>", "Kick a person by name.", arg -> {
            if (!Vars.state.isGame()) {
                Log.err("Not hosting a game yet. Calm down.");
                return;
            }

            Player target = Groups.player.find(p -> p.name().equals(arg[0]));

            if (target != null) {
                Call.sendMessage("[scarlet]" + target.name() + "[scarlet] has been kicked by the server.");
                target.kick(KickReason.kick);
                Log.info("It is done.");
            } else {
                Log.info("Nobody with that name could be found...");
            }
        });

        handler.register("ban", "<type-id/name/ip> <username/IP/ID...>", "Ban a person.", arg -> {
            if (arg[0].equals("id")) {
                Vars.netServer.admins.banPlayerID(arg[1]);
                Log.info("Banned.");
            } else if (arg[0].equals("name")) {
                Player target = Groups.player.find(p -> p.name().equalsIgnoreCase(arg[1]));
                if (target != null) {
                    Vars.netServer.admins.banPlayer(target.uuid());
                    Log.info("Banned.");
                } else {
                    Log.err("No matches found.");
                }
            } else if (arg[0].equals("ip")) {
                Vars.netServer.admins.banPlayerIP(arg[1]);
                Log.info("Banned.");
            } else {
                Log.err("Invalid type.");
            }

            for (Player player : Groups.player) {
                if (Vars.netServer.admins.isIDBanned(player.uuid())) {
                    Call.sendMessage("[scarlet]" + player.name + " has been banned.");
                    player.con.kick(KickReason.banned);
                }
            }
        });

        handler.register("bans", "List all banned IPs and IDs.", arg -> {
            Seq<PlayerInfo> bans = Vars.netServer.admins.getBanned();

            if (bans.size == 0) {
                Log.info("No ID-banned players have been found.");
            } else {
                Log.info("Banned players [ID]:");
                for (PlayerInfo info : bans) {
                    Log.info(" @ / Last known name: '@'", info.id, info.plainLastName());
                }
            }

            Seq<String> ipbans = Vars.netServer.admins.getBannedIPs();

            if (ipbans.size == 0) {
                Log.info("No IP-banned players have been found.");
            } else {
                Log.info("Banned players [IP]:");
                for (String string : ipbans) {
                    PlayerInfo info = Vars.netServer.admins.findByIP(string);
                    if (info != null) {
                        Log.info("  '@' / Last known name: '@' / ID: '@'", string, info.plainLastName(), info.id);
                    } else {
                        Log.info("  '@' (No known name or info)", string);
                    }
                }
            }
        });

        handler.register("unban", "<ip/ID>", "Completely unban a person by IP or ID.", arg -> {
            if (Vars.netServer.admins.unbanPlayerIP(arg[0]) || Vars.netServer.admins.unbanPlayerID(arg[0])) {
                Log.info("Unbanned player: @", arg[0]);
            } else {
                Log.err("That IP/ID is not banned!");
            }
        });

        handler.register("pardon", "<ID>", "Pardons a votekicked player by ID and allows them to join again.", arg -> {
            PlayerInfo info = Vars.netServer.admins.getInfoOptional(arg[0]);

            if (info != null) {
                info.lastKicked = 0;
                Vars.netServer.admins.kickedIPs.remove(info.lastIP);
                Log.info("Pardoned player: @", info.plainLastName());
            } else {
                Log.err("That ID can't be found.");
            }
        });

        handler.register("admin", "<add/remove> <username/ID...>", "Make an online user admin", arg -> {
            if (!Vars.state.isGame()) {
                Log.err("Open the server first.");
                return;
            }

            if (!(arg[0].equals("add") || arg[0].equals("remove"))) {
                Log.err("Second parameter must be either 'add' or 'remove'.");
                return;
            }

            boolean add = arg[0].equals("add");

            PlayerInfo target;
            Player playert = Groups.player.find(p -> p.plainName().equalsIgnoreCase(Strings.stripColors(arg[1])));
            if (playert != null) {
                target = playert.getInfo();
            } else {
                target = Vars.netServer.admins.getInfoOptional(arg[1]);
                playert = Groups.player.find(p -> p.getInfo() == target);
            }

            if (target != null) {
                if (add) {
                    Vars.netServer.admins.adminPlayer(target.id, playert == null ? target.adminUsid : playert.usid());
                } else {
                    Vars.netServer.admins.unAdminPlayer(target.id);
                }
                if (playert != null)
                    playert.admin = add;
            } else {
                Log.err("Nobody with that name or ID could be found. If adding an admin by name, make sure they're online; otherwise, use their UUID.");
            }
        });

        handler.register("admins", "List all admins.", arg -> {
            Seq<PlayerInfo> admins = Vars.netServer.admins.getAdmins();

            if (admins.size == 0) {
                Log.info("No admins have been found.");
            } else {
                Log.info("Admins:");
                for (PlayerInfo info : admins) {
                    Log.info(" &lm @ /  ID: '@' / IP: '@'", info.plainLastName(), info.id, info.lastIP);
                }
            }
        });

        handler.register("players", "List all players currently in game.", arg -> {
            if (Groups.player.size() == 0) {
                Log.info("No players are currently in the server.");
            } else {
                Log.info("Players: @", Groups.player.size());
                for (Player user : Groups.player) {
                    Log.info(" @&lm @ / ID: @ / IP: @", user.admin ? "&r[A]&c" : "&b[P]&c", user.plainName(),
                            user.uuid(), user.ip());
                }
            }
        });

        handler.register("runwave", "Trigger the next wave.", arg -> {
            if (!Vars.state.isGame()) {
                Log.err("Not hosting. Host a game first.");
            } else {
                Vars.logic.runWave();
                Log.info("Wave spawned.");
            }
        });

        handler.register("gameover", "Force a game over.", arg -> {
            if (Vars.state.isMenu()) {
                Log.err("Not playing a map.");
                return;
            }

            Log.info("Core destroyed.");

            Events.fire(new GameOverEvent(Vars.state.rules.waveTeam));
        });

        handler.register("info", "<IP/UUID/name...>",
                "Find playerLog.Info(s). Can optionally check for all names or IPs a player has had.", arg -> {
                    ObjectSet<PlayerInfo> infos = Vars.netServer.admins.findByName(arg[0]);

                    if (infos.size > 0) {
                        Log.info("Players found: @", infos.size);

                        int i = 0;
                        for (PlayerInfo info : infos) {
                            Log.info("[@] Trace info for player '@' / UUID @ / RAW @", i++, info.plainLastName(),
                                    info.id, info.lastName);
                            Log.info("  all names used: @", info.names);
                            Log.info("  IP: @", info.lastIP);
                            Log.info("  all IPs used: @", info.ips);
                            Log.info("  times joined: @", info.timesJoined);
                            Log.info("  times kicked: @", info.timesKicked);
                        }
                    } else {
                        Log.info("Nobody with that name could be found.");
                    }
                });

        handler.register("search", "<name...>", "Search players who have used part of a name.", arg -> {
            ObjectSet<PlayerInfo> infos = Vars.netServer.admins.searchNames(arg[0]);

            if (infos.size > 0) {
                Log.info("Players found: @", infos.size);

                int i = 0;
                for (PlayerInfo info : infos) {
                    Log.info("- [@] '@' / @", i++, info.plainLastName(), info.id);
                }
            } else {
                Log.info("Nobody with that name could be found.");
            }
        });

        handler.register("gc", "Trigger a garbage collection. Testing only.", arg -> {
            int pre = (int) (Core.app.getJavaHeap() / 1024 / 1024);
            System.gc();
            int post = (int) (Core.app.getJavaHeap() / 1024 / 1024);
            Log.info("@ MB collected. Memory usage now at @ MB.", pre - post, post);
        });
    }
}
