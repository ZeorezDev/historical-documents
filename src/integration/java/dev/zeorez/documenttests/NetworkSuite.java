package dev.zeorez.documenttests;

import com.mojang.logging.LogUtils;
import dev.zeorez.historicaldocuments.*;
import net.minecraft.commands.Commands;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

final class NetworkSuite {
    private int step;

    NetworkSuite() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("documentstestreceipt").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException();
            var doc = Document.read(player.getMainHandItem()).orElseThrow();
            if (step == 2 && player.getGameProfile().getName().equals("DocReader")
                    && DocumentArchive.get(player.server).verifies(doc)) {
                step = 3;
                LogUtils.getLogger().info("DOCUMENTS PASS network");
                player.server.halt(false);
            }
            return 1;
        }));
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || step == 3) return;
        var server = event.getServer();
        if (server.getTickCount() > 12000) {
            step = 3;
            LogUtils.getLogger().error("DOCUMENTS FAIL network timeout");
            server.halt(false);
            return;
        }
        var writer = server.getPlayerList().getPlayerByName("DocWriter");
        var reader = server.getPlayerList().getPlayerByName("DocReader");
        if (writer == null || reader == null) return;
        if (step == 0) {
            server.getCommands().performPrefixedCommand(writer.createCommandSourceStack(), "town create Berlin");
            writer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HistoricalDocuments.BLANK.get()));
            step = 1;
        } else if (step == 1) {
            var doc = Document.read(writer.getMainHandItem()).orElse(null);
            if (doc == null || doc.seal() == null) return;
            if (!doc.fields().title().equals("İzin 41") || !doc.fields().body().equals("İmar izni.\nZweite Zeile.")
                    || !doc.issuer().name().equals("Berlin") || !doc.seal().signer().equals(writer.getUUID())) {
                throw new AssertionError("GUI fields or seal changed");
            }
            reader.setItemInHand(InteractionHand.MAIN_HAND, writer.getMainHandItem());
            writer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            step = 2;
        }
    }
}
