package dev.zeorez.documenttests;

import com.mojang.logging.LogUtils;
import dev.zeorez.historicaldocuments.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import static net.minecraft.world.InteractionHand.MAIN_HAND;

final class PrintingSuite {
    private final ServerPlayer writer, reader;
    private final ItemStack original;
    private final BlockPos machine;
    private AbstractContainerMenu menu;
    private ItemStack certificate;
    private ItemStack townOriginal;
    private int step, ticks, paper, networkWait;
    private boolean done;

    PrintingSuite(ServerPlayer writer, ServerPlayer reader, ItemStack original) {
        this.writer = writer;
        this.reader = reader;
        this.original = original;
        machine = writer.blockPosition().offset(1, 0, 0);
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("historicallinotype", "linotype_printing_machine"));
        writer.serverLevel().setBlockAndUpdate(machine, block.defaultBlockState());
        reader.teleportTo(writer.getX(), writer.getY(), writer.getZ());
        open(reader);
        slot(0, new ItemStack(Items.WRITABLE_BOOK));
        item(0).getOrCreateTag().putString("test", "Istanbul");
        slot(1, new ItemStack(Items.PAPER, 32));
        slot(2, new ItemStack(Items.INK_SAC));
        slot(3, new ItemStack(Items.COAL));
        button(reader, 4);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void open(ServerPlayer actor) {
        actor.openMenu((MenuProvider)actor.serverLevel().getBlockEntity(machine));
        menu = actor.containerMenu;
    }
    private void slot(int i, ItemStack stack) { menu.getSlot(i).set(stack); }
    private ItemStack item(int i) { return menu.getSlot(i).getItem(); }
    private void button(ServerPlayer actor, int action) {
        check(menu.clickMenuButton(actor, action), "menu button " + action);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    private static int command(ServerPlayer p, String cmd) throws Exception {
        return p.server.getCommands().getDispatcher().execute(cmd, p.createCommandSourceStack());
    }
    private void emptyOutput() {
        var counters = writer.serverLevel().getBlockEntity(machine).saveWithoutMetadata();
        int used = 32 - paper;
        check(item(4).isEmpty() && item(1).getCount() == paper
                && counters.getInt("Ink") == 16 - used && counters.getInt("Fuel") == 64 - used,
                "failed job charged or delivered");
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || ++ticks < 30) return;
        ticks = 0;
        if (step == 5 && item(4).isEmpty() && ++networkWait < 15) return;
        try {
            advance();
            step++;
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("DOCUMENTS FAIL printing step " + step, failure);
            writer.server.halt(false);
        }
    }

    private void advance() throws Exception {
        switch (step) {
            case 0 -> {
                check(ItemStack.matches(item(0), item(4)) && item(1).getCount() == 31 && item(2).isEmpty() && item(3).isEmpty(), "writable book");
                slot(4, ItemStack.EMPTY);
                slot(0, new ItemStack(Items.WRITTEN_BOOK));
                item(0).getOrCreateTag().putString("author", "Writer");
                button(reader, 4);
            }
            case 1 -> {
                check(ItemStack.matches(item(0), item(4)), "written book");
                slot(4, ItemStack.EMPTY);
                slot(0, original.copy());
                button(reader, 4);
            }
            case 2 -> {
                reader.setItemInHand(MAIN_HAND, item(4).copy());
                var view = DocumentService.begin(reader, MAIN_HAND);
                check(view.kind() == DocumentNetwork.Kind.PRINTED && view.document().fields().equals(Document.read(original).orElseThrow().fields())
                        && command(reader, "document verify") == 0, "ordinary text and status");
                slot(4, ItemStack.EMPTY);
                paper = item(1).getCount();
                button(reader, 6);
            }
            case 3 -> {
                emptyOutput(); // foreign leader and second signer
                check(command(reader, "town setmayor DocDeputy") == 1, "transfer");
                button(reader, 6);
            }
            case 4 -> {
                emptyOutput(); // ordinary citizen
                open(writer); // writer sends two actual inventory-button packets
            }
            case 5 -> {
                check(item(4).is(HistoricalDocuments.CERTIFIED.get()) && item(1).getCount() == paper - 1 && item(5).isEmpty(), "certified job and replay");
                certificate = item(4).copy();
                writer.setItemInHand(MAIN_HAND, certificate.copy());
                var view = DocumentService.begin(writer, MAIN_HAND);
                check(view.kind() == DocumentNetwork.Kind.CERTIFIED && view.history().getString().contains("DocWriter")
                        && view.history().getString().contains("DocReader") && command(writer, "document verify") == 1, "two live seals");
                for (String key : new String[]{"Copy","Original","Printer","Office"}) {
                    var fake = certificate.copy();
                    fake.getTagElement("Certificate").putUUID(key, java.util.UUID.randomUUID());
                    writer.setItemInHand(MAIN_HAND, fake);
                    check(command(writer, "document verify") == 0, "tamper " + key);
                }
                slot(4, ItemStack.EMPTY);
                button(writer, 6);
            }
            case 6 -> {
                check(item(4).is(HistoricalDocuments.CERTIFIED.get()) && !ItemStack.isSameItemSameTags(certificate, item(4)), "unique impressions");
                slot(4, ItemStack.EMPTY);
                writer.setItemInHand(MAIN_HAND, original.copy());
                check(command(writer, "document revoke Printing test") == 1, "revoke");
                writer.setItemInHand(MAIN_HAND, certificate.copy());
                check(DocumentService.begin(writer, MAIN_HAND).history().getString().contains("Printing test"), "revoked copy");
                paper = item(1).getCount();
                button(writer, 6);
            }
            case 7 -> {
                emptyOutput();
                writer.setItemInHand(MAIN_HAND, new ItemStack(HistoricalDocuments.BLANK.get()));
                var session = DocumentService.begin(writer, MAIN_HAND);
                var fields = Document.read(original).orElseThrow().fields();
                check(DocumentService.save(writer, new DocumentNetwork.Save(session.token(), Document.Scope.TOWN, fields)), "municipal draft");
                check(DocumentService.sign(writer, DocumentService.begin(writer, MAIN_HAND).token()), "municipal signature");
                townOriginal = writer.getMainHandItem().copy();
                slot(0, townOriginal.copy());
                button(writer, 6);
                var deputy = ServerSuite.player(writer.server, "TownDeputy");
                check(command(writer, "town invite TownDeputy") == 1 && command(deputy, "town accept Berlin") == 1
                        && command(writer, "town setmayor TownDeputy") == 1, "transfer during impression");
            }
            case 8 -> {
                emptyOutput();
                button(writer, 4);
                slot(0, original.copy());
            }
            case 9 -> {
                emptyOutput(); // swapped source
                button(writer, 4);
                button(writer, 5);
            }
            case 10 -> {
                emptyOutput(); // cancelled impression
                var deputy = ServerSuite.player(writer.server, "TownDeputy");
                check(command(deputy, "town setmayor DocWriter") == 1, "restore mayor");
                slot(0, townOriginal.copy());
                button(writer, 6);
            }
            case 11 -> {
                var townCopy = item(4).copy();
                check(townCopy.is(HistoricalDocuments.CERTIFIED.get()), "municipal impression");
                writer.setItemInHand(MAIN_HAND, townOriginal.copy());
                var deputy = ServerSuite.player(reader.server, "DocDeputy");
                check(command(deputy, "town setmayor DocReader") == 1, "restore other mayor");
                check(command(writer, "document invite DocReader") == 1, "post-print invitation");
                reader.setItemInHand(MAIN_HAND, townOriginal.copy());
                check(command(reader, "document cosign") == 1, "post-print signature");
                reader.setItemInHand(MAIN_HAND, townCopy);
                check(command(reader, "document verify") == 1
                        && DocumentService.begin(reader, MAIN_HAND).history().getString().contains("DocReader"),
                        "old municipal copy resolves later signature");
                var chest = (ChestBlockEntity)writer.server.overworld().getBlockEntity(new BlockPos(0,80,0));
                chest.setItem(1, certificate.copy());
                chest.setChanged();
                done = true;
                LogUtils.getLogger().info("DOCUMENTS PASS printing");
                writer.server.halt(false);
            }
        }
    }

    static void restart(MinecraftServer server) throws Exception {
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(new BlockPos(0,80,0));
        var p = ServerSuite.player(server, "Verifier");
        p.setItemInHand(MAIN_HAND, chest.getItem(1).copy());
        var copy = DocumentService.begin(p, MAIN_HAND);
        check(command(p, "document verify") == 1 && copy.document().equals(Document.read(chest.getItem(0)).orElseThrow())
                && copy.history().getString().contains("Printing test") && copy.history().getString().contains("DocReader"), "persisted copy");
    }
}
