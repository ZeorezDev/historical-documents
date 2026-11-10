package dev.zeorez.historicaldocuments;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.time.Instant;
import java.util.*;
import static dev.zeorez.historicaldocuments.Document.*;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.msg;

/** The item keeps its original v1 payload. All later events belong to the server ledger. */
public final class ArchiveEntry {
    final Document original;
    record Signature(Issuer office, Seal seal) {}
    record Revocation(UUID actor, String reason, long time) {}
    final List<Signature> signatures = new ArrayList<>();
    final Map<UUID, UUID> invitations = new HashMap<>();
    Revocation revocation;

    ArchiveEntry(Document doc) {
        original = doc;
        signatures.add(new Signature(doc.issuer(), doc.seal()));
    }

    boolean represented(Issuer office, UUID player) {
        return signatures.stream().anyMatch(s -> s.office.id().equals(office.id()) || s.seal.signer().equals(player));
    }

    public int signatureCount() { return signatures.size(); }

    Component status() { return msg(revocation == null ? "active" : "revoked"); }

    Component summary() {
        return msg("archive.row", original.fields().title(), original.fields().type(), original.issuer().label(),
                Instant.ofEpochMilli(original.seal().time()).toString(), original.id().toString(), signatures.size(), status());
    }

    Component history() {
        MutableComponent text = Component.empty().append(status()).append("\n").append(msg("signatures", signatures.size()));
        for (var signature : signatures) {
            var seal = signature.seal;
            text.append("\n").append(signature.office.label()).append(" · ")
                    .append(msg("signature", seal.name(), Instant.ofEpochMilli(seal.time()).toString()));
        }
        if (revocation != null) text.append("\n").append(msg("revocation", revocation.reason,
                Instant.ofEpochMilli(revocation.time).toString(), revocation.actor.toString()));
        return text;
    }

    CompoundTag encode() {
        var row = new CompoundTag();
        row.put("Original", original.encode());
        var extra = new ListTag();
        for (var sig : signatures.subList(1, signatures.size())) {
            var t = new CompoundTag();
            t.putUUID("Office", sig.office.id());
            t.putString("OfficeName", sig.office.name());
            t.putUUID("Player", sig.seal.signer());
            t.putString("PlayerName", sig.seal.name());
            t.putLong("Time", sig.seal.time());
            extra.add(t);
        }
        row.put("Additional", extra);
        var pending = new CompoundTag();
        invitations.forEach((player, office) -> pending.putUUID(player.toString(), office));
        row.put("Invitations", pending);
        if (revocation != null) {
            var t = new CompoundTag();
            t.putUUID("Player", revocation.actor);
            t.putString("Reason", revocation.reason);
            t.putLong("Time", revocation.time);
            row.put("Revocation", t);
        }
        return row;
    }

    static ArchiveEntry decode(CompoundTag row, boolean legacy) {
        var doc = Document.decode(legacy ? row : row.getCompound("Original")).filter(d -> d.seal() != null).orElseThrow();
        var entry = new ArchiveEntry(doc);
        if (legacy) return entry;
        var extra = row.getList("Additional", Tag.TAG_COMPOUND);
        if (extra.size() > 3) throw new IllegalArgumentException();
        for (int n = 0; n < extra.size(); n++) {
            var t = extra.getCompound(n);
            var office = new Issuer(doc.issuer().scope(), t.getUUID("Office"), t.getString("OfficeName"));
            var seal = new Seal(t.getUUID("Player"), t.getString("PlayerName"), t.getLong("Time"));
            if (entry.represented(office, seal.signer()) || !validText(office.name(), 80)
                    || !validText(seal.name(), 80) || seal.time() <= 0) throw new IllegalArgumentException();
            entry.signatures.add(new Signature(office, seal));
        }
        var pending = row.getCompound("Invitations");
        if (pending.size() > 3 || (entry.signatureCount() == 4 && !pending.isEmpty())) throw new IllegalArgumentException();
        for (var key : pending.getAllKeys()) entry.invitations.put(UUID.fromString(key), pending.getUUID(key));
        if (row.contains("Revocation")) {
            var t = row.getCompound("Revocation");
            entry.revocation = new Revocation(t.getUUID("Player"), t.getString("Reason"), t.getLong("Time"));
            if (!validText(entry.revocation.reason, 160) || entry.revocation.time <= 0 || !pending.isEmpty())
                throw new IllegalArgumentException();
        }
        if (!entry.encode().equals(row)) throw new IllegalArgumentException();
        return entry;
    }

    static boolean validText(String text, int limit) {
        return !text.isBlank() && Document.clean(text, limit, false);
    }
}
