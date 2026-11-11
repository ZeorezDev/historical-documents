package dev.zeorez.documenttests;

import dev.zeorez.historicaldocuments.*;
import dev.zeorez.historicaldocuments.client.DocumentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.msg;

@Mod.EventBusSubscriber(modid = "historical_document_tests", value = Dist.CLIENT)
public final class ClientSuite {
    private static final String ACTOR = System.getProperty("documents.client", "");
    private static boolean connecting, received, reportedWarnings;
    private static int stage, ticks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (ACTOR.isEmpty() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!reportedWarnings && mc.screen instanceof net.minecraftforge.client.gui.LoadingErrorScreen) {
            reportedWarnings = true;
            net.minecraftforge.fml.ModLoader.get().getWarnings().forEach(warning ->
                    com.mojang.logging.LogUtils.getLogger().warn("Test client loading warning: {}", warning.formatToString()));
        }
        if (!connecting && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            connecting = true;
            String address = "127.0.0.1:25761";
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                    new ServerData("Documents test", address, false), false);
        }
        if (mc.player == null || mc.getConnection() == null || ++ticks % 12 != 0) return;
        if (ACTOR.equals("DocWriter")) writer(mc);
        else reader(mc);
    }

    private static void writer(Minecraft mc) {
        var held = mc.player.getMainHandItem();
        if (stage == 5 && mc.player.containerMenu.slots.size() == 48
                && mc.player.containerMenu.getSlot(0).getItem().is(HistoricalDocuments.WRITTEN.get())) {
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 6);
            mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 6);
            stage++;
        }
        if ((stage == 0 && held.is(HistoricalDocuments.BLANK.get())
                || stage == 3 && Document.read(held).isPresent()) && mc.screen == null) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            stage++;
        } else if (mc.screen instanceof DocumentScreen screen) {
            if (stage == 1) {
                var boxes = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                boxes.get(0).setValue("İzin 41");
                boxes.get(1).setValue("Ruhsat");
                boxes.get(2).setValue("Mehmet");
                var body = screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                body.setValue("İmar izni.\nZweite Zeile.");
                press(screen, msg("issuer.personal", ACTOR).getString());
                press(screen, msg("issuer.town", "Berlin").getString());
                stage++;
            } else if (stage == 2) {
                Screenshot.grab(mc.gameDirectory, "draft.png", mc.getMainRenderTarget(), ignored -> {});
                press(screen, msg("save").getString());
                stage++;
            } else if (stage == 4) {
                press(screen, msg("sign").getString());
                stage++;
            }
        }
    }

    private static void reader(Minecraft mc) {
        if (stage == 0 && mc.screen == null && Document.read(mc.player.getMainHandItem()).filter(d -> d.seal() != null).isPresent()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            stage++;
        } else if (stage == 1 && mc.screen instanceof DocumentScreen screen) {
            if (screen.children().stream().noneMatch(FittingMultiLineTextWidget.class::isInstance)
                    || screen.children().stream().anyMatch(EditBox.class::isInstance)) throw new AssertionError("Editable signed document");
            Screenshot.grab(mc.gameDirectory, "sealed.png", mc.getMainRenderTarget(), ignored -> {});
            press(screen, msg("close").getString());
            mc.getConnection().sendCommand("document cosign");
            mc.getConnection().sendCommand("document verify");
            stage++;
        } else if (stage == 2 && received) {
            mc.getConnection().sendCommand("documentstestreceipt");
            stage++;
        }
    }

    private static void press(DocumentScreen screen, String caption) {
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.active && b.getMessage().getString().equals(caption)).findFirst().orElseThrow();
        button.onPress();
    }

    @SubscribeEvent
    public static void message(ClientChatReceivedEvent.System event) {
        if (event.getMessage().getContents() instanceof TranslatableContents text
                && text.getKey().equals("historical_documents.verified")) received = true;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!ACTOR.isEmpty() && event.getPlayer() != null) Minecraft.getInstance().stop();
    }
}
