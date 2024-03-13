package mindustrytool;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.net.Administration.*;
import mindustry.world.blocks.storage.*;

public class ExamplePlugin extends Plugin {

    @Override
    public void init() {

        Events.on(BuildSelectEvent.class, event -> {
            if (!event.breaking && event.builder != null && event.builder.buildPlan() != null
                    && event.builder.buildPlan().block == Blocks.thoriumReactor && event.builder.isPlayer()) {

                Player player = event.builder.getPlayer();

                Call.sendMessage("[scarlet]ALERT![] " + player.name + " has begun building a reactor at " + event.tile.x
                        + ", " + event.tile.y);
            }
        });

        Vars.netServer.admins.addChatFilter((player, text) -> text.replace("heck", "h*ck"));

        Vars.netServer.admins.addActionFilter(action -> {

            if (action.type == ActionType.depositItem && action.item == Items.blastCompound
                    && action.tile.block() instanceof CoreBlock) {
                action.player.sendMessage(
                        "Example action filter: Prevents players from depositing blast compound into the core.");
                return false;
            }
            return true;
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("reactors", "List all thorium reactors in the map.", args -> {
            for (int x = 0; x < Vars.world.width(); x++) {
                for (int y = 0; y < Vars.world.height(); y++) {

                    if (Vars.world.tile(x, y).block() == Blocks.thoriumReactor && Vars.world.tile(x, y).isCenter()) {
                        Log.info("Reactor at @, @", x, y);
                    }
                }
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {

        handler.<Player>register("reply", "<text...>", "A simple ping command that echoes a player's text.",
                (args, player) -> {
                    player.sendMessage("You said: [accent] " + args[0]);
                });

        handler.<Player>register("whisper", "<player> <text...>", "Whisper text to another player.", (args, player) -> {

            Player other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));

            if (other == null) {
                player.sendMessage("[scarlet]No player by that name found!");
                return;
            }

            other.sendMessage("[lightgray](whisper) " + player.name + ":[] " + args[1]);
        });
    }
}
