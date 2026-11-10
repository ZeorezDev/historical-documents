package dev.zeorez.documenttests;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.zeorez.historicaldocuments.*;
import dev.zeorez.historicalnations.api.HistoricalNationsApi;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import static net.minecraft.world.InteractionHand.MAIN_HAND;
import static dev.zeorez.historicaldocuments.Document.*;

@Mod("historical_document_tests")
public final class ServerSuite {
    private boolean ran;
    private static final Fields TEXT = new Fields("İnşaat ruhsatı", "Ruhsat", "Şantiye",
            "Birinci satır.\n" + "ö".repeat(1985));
    private static final BlockPos CHEST = new BlockPos(0, 80, 0);

    public ServerSuite() {
        if ("network".equals(System.getProperty("documents.test"))) {
            new NetworkSuite();
        } else {
            MinecraftForge.EVENT_BUS.addListener(this::tick);
        }
    }

    private void tick(TickEvent.ServerTickEvent event) {
        var phase = System.getProperty("documents.test", "");
        if (ran || phase.isEmpty() || event.phase != TickEvent.Phase.END || event.getServer().getTickCount() < 30) return;
        ran = true;
        var server = event.getServer();
        try {
            switch (phase) {
                case "setup" -> setup(server);
                case "restart" -> restart(server);
                case "upgrade", "upgradeRestart" -> upgrade(server, phase.equals("upgrade"));
                case "networkRestart" -> {
                    var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
                    var doc = Document.read(chest.getItem(0)).orElseThrow();
                    var archive = DocumentArchive.get(server);
                    check(archive.verifies(doc) && archive.entry(doc.id()).signatureCount() == 2, "persisted countersignature");
                }
                default -> throw new IllegalArgumentException(phase);
            }
            LogUtils.getLogger().info("DOCUMENTS PASS {}", phase);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("DOCUMENTS FAIL " + phase, failure);
        } finally {
            server.halt(false);
        }
    }

    static ServerPlayer player(MinecraftServer server, String name) {
        var id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        var profile = new GameProfile(id, name);
        server.getProfileCache().add(profile);
        return new FakePlayer(server.overworld(), profile);
    }

    private static int command(ServerPlayer player, String text) throws Exception {
        return player.server.getCommands().getDispatcher().execute(text,
                player.createCommandSourceStack().withPermission(0).withSuppressedOutput());
    }

    private static void check(boolean ok, String scenario) {
        if (!ok) throw new AssertionError(scenario);
    }

    private static DocumentNetwork.Open open(ServerPlayer player) {
        return java.util.Objects.requireNonNull(DocumentService.begin(player, MAIN_HAND), "expected readable document");
    }

    private static Document draft(ServerPlayer player, Scope scope) {
        player.setItemInHand(MAIN_HAND, new ItemStack(HistoricalDocuments.BLANK.get()));
        var session = open(player);
        check(DocumentService.save(player, new DocumentNetwork.Save(session.token(), scope, TEXT)), "save " + scope);
        return Document.read(player.getMainHandItem()).orElseThrow();
    }

    private static void setup(MinecraftServer server) throws Exception {
        var ahmet = player(server, "DocAhmet");
        var mehmet = player(server, "DocMehmet");
        var leader = player(server, "DocDeniz");
        var api = HistoricalNationsApi.get(server);

        draft(mehmet, Scope.PERSONAL);
        check(command(mehmet, "document verify") == 0 && command(mehmet, "document info") == 1, "personal draft");
        var original = mehmet.getMainHandItem();
        var edit = open(mehmet);
        mehmet.setItemInHand(MAIN_HAND, original.copy());
        check(!DocumentService.save(mehmet, new DocumentNetwork.Save(edit.token(), Scope.PERSONAL, TEXT)), "hand changed");
        var longText = new Fields("Title", "Letter", "", "x".repeat(2001));
        check(!DocumentService.save(mehmet, new DocumentNetwork.Save(open(mehmet).token(), Scope.PERSONAL, longText)), "limit");
        var edited = new Fields("Not", "Mektup", "", "Düzenlenmiş metin");
        check(DocumentService.save(mehmet, new DocumentNetwork.Save(open(mehmet).token(), Scope.PERSONAL, edited)), "edit");
        check(Document.read(mehmet.getMainHandItem()).orElseThrow().fields().equals(edited), "edited text");
        check(command(ahmet, "town create Berlin") == 1 && command(ahmet, "nation create Almanya") == 1, "found Berlin");
        check(command(ahmet, "town invite DocMehmet") == 1 && command(mehmet, "town accept Berlin") == 1, "citizenship");
        check(command(leader, "town create Ankara") == 1 && command(leader, "nation create Türkiye") == 1, "second nation");

        var townDraft = draft(mehmet, Scope.TOWN);
        check(!DocumentService.sign(mehmet, open(mehmet).token()), "2 citizen denied");
        ahmet.setItemInHand(MAIN_HAND, mehmet.getMainHandItem().copy());
        var signing = open(ahmet);
        check(signing.maySign() && DocumentService.sign(ahmet, signing.token()), "1 mayor signs");
        var sealedTown = ahmet.getMainHandItem().copy();
        var town = Document.read(sealedTown).orElseThrow();
        check(town.id().equals(townDraft.id()) && town.issuer().id().equals(api.getPlayerTown(ahmet.getUUID()).orElseThrow().id())
                && town.seal().signer().equals(ahmet.getUUID()) && town.issuer().name().equals("Berlin"), "town identity");
        check(!DocumentService.sign(ahmet, signing.token()), "no duplicate transaction");
        check(DocumentService.begin(mehmet, MAIN_HAND) == null, "superseded draft copy");
        mehmet.setItemInHand(MAIN_HAND, sealedTown.copy());
        check(command(mehmet, "document verify") == 1 && !open(mehmet).maySign(), "9 recipient verifies");
        check(!DocumentService.save(mehmet, new DocumentNetwork.Save(open(mehmet).token(), Scope.TOWN, edited)), "locked");

        draft(mehmet, Scope.NATION);
        leader.setItemInHand(MAIN_HAND, mehmet.getMainHandItem().copy());
        check(!DocumentService.sign(leader, open(leader).token()), "4 wrong nation leader");
        ahmet.setItemInHand(MAIN_HAND, mehmet.getMainHandItem().copy());
        check(DocumentService.sign(ahmet, open(ahmet).token()), "3 nation leader");
        var sealedNation = ahmet.getMainHandItem().copy();
        check(command(ahmet, "document verify") == 1, "nation record");

        draft(ahmet, Scope.TOWN);
        var beforeTransfer = open(ahmet);
        check(command(ahmet, "town setmayor DocMehmet") == 1, "transfer");
        check(!DocumentService.sign(ahmet, beforeTransfer.token()), "5 former mayor denied with open editor");
        ahmet.setItemInHand(MAIN_HAND, sealedTown.copy());
        check(command(ahmet, "document verify") == 1, "5 archived municipal signature");
        check(command(mehmet, "nation disband") == 1, "disband");
        mehmet.setItemInHand(MAIN_HAND, sealedNation.copy());
        check(command(mehmet, "document verify") == 1, "6 dissolved nation");

        for (var field : new String[]{"Body", "IssuerName", "SignerName", "SignedAt", "Official", "IssuerId", "Id"}) {
            var changed = sealedNation.copy();
            changed.getTagElement("HistoricalDocument").putString(field, "Changed");
            leader.setItemInHand(MAIN_HAND, changed);
            check(command(leader, "document verify") == 0 && DocumentService.begin(leader, MAIN_HAND) == null,
                    "8 altered " + field);
        }
        var tag = Document.read(sealedNation).orElseThrow().encode();
        tag.putUUID("Id", UUID.randomUUID());
        var unregistered = Document.decode(tag).orElseThrow();
        leader.setItemInHand(MAIN_HAND, unregistered.item());
        check(command(leader, "document verify") == 0, "unknown signature");

        server.overworld().setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        chest.setItem(0, sealedTown);
        chest.setItem(1, sealedNation);
        chest.setChanged();
    }

    private static void upgrade(MinecraftServer server, boolean first) throws Exception {
        restart(server); // Read the two untouched v0.1.0 items placed by that release.
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        var original = chest.getItem(0).copy();
        var doc = Document.read(original).orElseThrow();
        var mayor = player(server, "DocMehmet");
        var oldMayor = player(server, "DocAhmet");
        var foreign = player(server, "DocDeniz");
        for (var person : new ServerPlayer[]{mayor, oldMayor, foreign}) person.setItemInHand(MAIN_HAND, original.copy());
        check(doc.fields().equals(TEXT) && doc.seal().signer().equals(oldMayor.getUUID()), "legacy content and original signer");
        check(command(mayor, "document archive town Berlin") == 1 && command(mayor, "document archive info " + doc.id()) == 1,
                "old town archive after transfer");
        if (first) {
            check(command(oldMayor, "document revoke Former") == 0 && command(foreign, "document revoke Foreign") == 0, "revocation authority");
            check(command(mayor, "document revoke Test reason") == 1, "new mayor revokes");
        }
        check(command(mayor, "document revoke Again") == 0 && command(foreign, "document cosign") == 0, "final status");
        check(command(oldMayor, "document verify") == 1 && open(oldMayor).history().getString().contains("Test reason"), "old copy authentic and revoked");
        var forged = original.copy();
        forged.getTagElement("HistoricalDocument").putString("SecondSignature", "Unregistered");
        foreign.setItemInHand(MAIN_HAND, forged);
        check(command(foreign, "document verify") == 0, "unregistered additional seal");
        net.minecraft.world.level.saveddata.SavedData saved = DocumentArchive.get(server);
        var broken = saved.save(new net.minecraft.nbt.CompoundTag());
        broken.getList("Records", 10).getCompound(0).remove("Additional");
        net.minecraft.world.level.saveddata.SavedData locked = DocumentArchive.load(broken);
        check(locked.save(new net.minecraft.nbt.CompoundTag()).equals(broken), "preserve damaged archive");
    }

    private static void restart(MinecraftServer server) throws Exception {
        var recipient = player(server, "DocReader");
        var chest = (ChestBlockEntity) server.overworld().getBlockEntity(CHEST);
        for (int slot = 0; slot < 2; slot++) {
            recipient.setItemInHand(MAIN_HAND, chest.getItem(slot).copy());
            check(command(recipient, "document verify") == 1, "7 disk reload " + slot);
            check(open(recipient).document().seal() != null, "signed viewer after restart");
        }
        var inNether = new FakePlayer(server.getLevel(net.minecraft.world.level.Level.NETHER), recipient.getGameProfile());
        inNether.setItemInHand(MAIN_HAND, recipient.getMainHandItem().copy());
        check(command(inNether, "document verify") == 1, "same archive across dimensions");
    }
}
