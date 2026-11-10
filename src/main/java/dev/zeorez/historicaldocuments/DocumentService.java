package dev.zeorez.historicaldocuments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import static dev.zeorez.historicaldocuments.Document.*;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.*;

public final class DocumentService {
    // Accessed only on the logical server thread. A fresh use supersedes any earlier screen.
    private static final Map<ServerPlayer, Session> sessions = new WeakHashMap<>();
    private record Session(UUID token, InteractionHand hand, ItemStack held, ItemStack snapshot,
                           List<Issuer> choices) {}

    static void open(ServerPlayer player, InteractionHand hand) {
        var screen = begin(player, hand);
        if (screen != null) DocumentNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), screen);
    }

    public static DocumentNetwork.Open begin(ServerPlayer player, InteractionHand hand) {
        sessions.remove(player);
        var held = player.getItemInHand(hand);
        var doc = Document.read(held).orElse(null);
        boolean blank = held.is(BLANK.get()) && held.getCount() == 1;
        var archive = DocumentArchive.get(player.server);
        if ((!blank && doc == null) || (doc != null && (doc.seal() != null || archive.reserved(doc.id()))
                && !archive.verifies(doc))) {
            report(player, "invalid");
            return null;
        }
        var choices = new ArrayList<Issuer>();
        if (blank) {
            for (var scope : Scope.values()) Authority.current(player, scope).ifPresent(choices::add);
        } else {
            choices.add(doc.issuer());
        }
        var token = UUID.randomUUID();
        if (doc == null || doc.seal() == null) {
            sessions.put(player, new Session(token, hand, held, held.copy(), List.copyOf(choices)));
        }
        boolean maySign = doc != null && doc.seal() == null && Authority.signingOffice(player, doc.issuer()).isPresent();
        return new DocumentNetwork.Open(token, doc, choices, maySign);
    }

    private static Session consume(ServerPlayer player, UUID token) {
        var session = sessions.get(player);
        if (session != null && session.token.equals(token)) {
            sessions.remove(player);
            var held = player.getItemInHand(session.hand);
            if (player.isAlive() && !player.isSpectator() && held == session.held
                    && ItemStack.matches(held, session.snapshot)) return session;
        }
        report(player, "stale");
        return null;
    }

    public static boolean save(ServerPlayer player, DocumentNetwork.Save request) {
        var session = consume(player, request.token());
        if (session == null) return false;
        if (!request.fields().valid()) return report(player, "bad.fields");
        var old = Document.read(session.held).orElse(null);
        var archive = DocumentArchive.get(player.server);
        Issuer issuer;
        UUID id;
        if (session.held.is(BLANK.get())) {
            issuer = Authority.current(player, request.scope()).filter(current -> session.choices.stream()
                    .anyMatch(choice -> choice.scope() == current.scope() && choice.id().equals(current.id())))
                    .orElse(null);
            if (issuer == null) return report(player, "denied");
            id = UUID.randomUUID();
        } else {
            if (old == null || old.seal() != null || old.issuer().scope() != request.scope())
                return report(player, "invalid");
            issuer = old.issuer();
            id = old.id();
        }
        if (archive.reserved(id)) return report(player, "invalid");
        var draft = new Document(id, request.fields(), issuer, null);
        player.setItemInHand(session.hand, draft.item());
        return success(player, "saved");
    }

    public static boolean sign(ServerPlayer player, UUID token) {
        var session = consume(player, token);
        if (session == null) return false;
        var draft = Document.read(session.held).orElse(null);
        if (draft == null || draft.seal() != null) return report(player, "invalid");
        var office = Authority.signingOffice(player, draft.issuer());
        if (office.isEmpty()) return report(player, "denied");
        var seal = new Seal(player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis());
        var signed = new Document(draft.id(), draft.fields(), office.get(), seal);
        if (!DocumentArchive.get(player.server).register(signed)) return report(player, "archive.error");
        player.setItemInHand(session.hand, signed.item());
        return success(player, "signed");
    }

    private static boolean report(ServerPlayer player, String key) {
        player.sendSystemMessage(msg(key));
        return false;
    }

    private static boolean success(ServerPlayer player, String key) {
        player.sendSystemMessage(msg(key));
        return true;
    }
}
