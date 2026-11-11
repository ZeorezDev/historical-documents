package dev.zeorez.historicaldocuments;

import com.mojang.logging.LogUtils;
import dev.zeorez.historicalnations.api.HistoricalNationsApi;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import static dev.zeorez.historicaldocuments.Document.*;

public final class DocumentArchive extends SavedData {
    private final Map<UUID, ArchiveEntry> entries = new HashMap<>();
    private final Map<UUID, NavigableSet<ArchiveEntry>> offices = new HashMap<>();
    private final Set<Issuer> names = new HashSet<>();
    private final Map<UUID, Certificate> certificates = new HashMap<>();
    private CompoundTag unreadable;

    Certificate certificate(net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(HistoricalDocuments.CERTIFIED.get()) || stack.getCount() != 1) return null;
        var data = stack.getTagElement("Certificate");
        if (data == null || !data.hasUUID("Copy")) return null;
        var cert = certificates.get(data.getUUID("Copy"));
        return cert != null && cert.encode().equals(data) ? cert : null;
    }

    private static java.nio.file.Path archiveFile(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/historical_documents.dat");
    }

    // Publish the certificate and its source in one durable snapshot before the press delivers the item.
    boolean issue(MinecraftServer server, Certificate cert) {
        if (unreadable != null || certificates.putIfAbsent(cert.id(), cert) != null) return false;
        java.nio.file.Path tmp = null;
        try {
            var file = archiveFile(server);
            java.nio.file.Files.createDirectories(file.getParent());
            tmp = java.nio.file.Files.createTempFile(file.getParent(), "historical_documents-", ".tmp");
            var wrapper = new CompoundTag();
            wrapper.put("data", save(new CompoundTag()));
            NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(wrapper), tmp.toFile());
            try (var channel = java.nio.channels.FileChannel.open(tmp, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            setDirty(false);
            return true;
        } catch (java.io.IOException | RuntimeException error) {
            certificates.remove(cert.id());
            LogUtils.getLogger().error("Could not commit certified impression", error);
            return false;
        } finally {
            try { if (tmp != null) java.nio.file.Files.deleteIfExists(tmp); }
            catch (java.io.IOException error) { LogUtils.getLogger().warn("Could not clean temporary archive", error); }
        }
    }

    public static DocumentArchive get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DocumentArchive::load,
                () -> {
                    var archive = new DocumentArchive();
                    if (!java.nio.file.Files.notExists(archiveFile(server))) {
                        archive.unreadable = new CompoundTag();
                        LogUtils.getLogger().error("Cannot read existing document archive; writes disabled.");
                    }
                    return archive;
                }, "historical_documents");
    }

    boolean reserved(UUID id) { return entries.containsKey(id); }
    public ArchiveEntry entry(UUID id) { return entries.get(id); }
    public boolean verifies(Document doc) {
        var row = entry(doc.id());
        return row != null && row.original.equals(doc);
    }

    private void index(ArchiveEntry row, Issuer office) {
        names.add(office);
        offices.computeIfAbsent(office.id(), ignored -> new TreeSet<>(Comparator
                .comparingLong((ArchiveEntry e) -> e.original.seal().time()).reversed()
                .thenComparing(e -> e.original.id()))).add(row);
    }

    Set<UUID> resolve(MinecraftServer server, Scope scope, String name) {
        var result = new HashSet<UUID>();
        var api = HistoricalNationsApi.get(server);
        for (var office : names) {
            if (office.scope() != scope) continue;
            var current = scope == Scope.TOWN ? api.getTownById(office.id()).map(t -> t.name())
                    : api.getNationById(office.id()).map(n -> n.name());
            if (office.name().equalsIgnoreCase(name) || current.filter(name::equalsIgnoreCase).isPresent()
                    || office.id().toString().equalsIgnoreCase(name)) result.add(office.id());
        }
        return result;
    }

    Collection<ArchiveEntry> records(UUID office) {
        return Collections.unmodifiableCollection(offices.getOrDefault(office, Collections.emptyNavigableSet()));
    }

    boolean register(Document doc) {
        if (unreadable != null || doc.seal() == null || reserved(doc.id())) return false;
        var row = new ArchiveEntry(doc);
        entries.put(doc.id(), row);
        index(row, doc.issuer());
        setDirty();
        return true;
    }

    String change(ServerPlayer actor, String action, String reason, ServerPlayer guest) {
        var doc = Document.read(actor.getMainHandItem()).orElse(null);
        if (doc == null || !verifies(doc)) return "invalid";
        var row = entry(doc.id());
        if (row.revocation != null) return "already.revoked";
        if (!action.equals("cosign") && Authority.signingOffice(actor, doc.issuer()).isEmpty()) return "denied";
        if (action.equals("revoke")) {
            if (!ArchiveEntry.validText(reason, 160)) return "bad.reason";
            row.revocation = new ArchiveEntry.Revocation(actor.getUUID(), reason, System.currentTimeMillis());
            row.invitations.clear();
        } else {
            var signer = guest == null ? actor : guest;
            var id = signer.getUUID();
            var office = Authority.current(signer, doc.issuer().scope())
                    .flatMap(o -> Authority.signingOffice(signer, o)).orElse(null);
            if (office == null) return "denied";
            if (row.signatureCount() >= 4 || row.represented(office, id)) return "signature.limit";
            if (guest != null) {
                // Free slots held by former officeholders, including offline players.
                if (row.invitations.entrySet().removeIf(e -> Authority.currentByPlayer(actor.server, e.getKey(), doc.issuer().scope())
                        .filter(e.getValue()::equals).isEmpty())) setDirty();
                if (row.invitations.containsKey(id) || row.invitations.size() >= 3) return "invite.limit";
                row.invitations.put(id, office.id());
            } else {
                if (!office.id().equals(row.invitations.get(id))) return "invite.required";
                row.invitations.remove(id);
                row.signatures.add(new ArchiveEntry.Signature(office, new Seal(id, actor.getGameProfile().getName(), System.currentTimeMillis())));
                if (row.signatureCount() == 4) row.invitations.clear();
                index(row, office);
            }
        }
        setDirty();
        return null;
    }

    public static DocumentArchive load(CompoundTag root) {
        var archive = new DocumentArchive();
        try {
            int version = root.getInt("Version");
            if ((version < 1 || version > 3) || !(root.get("Records") instanceof ListTag records)
                    || (!records.isEmpty() && records.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
            for (int i = 0; i < records.size(); i++) {
                var row = ArchiveEntry.decode(records.getCompound(i), version == 1);
                if (archive.entries.put(row.original.id(), row) != null) throw new IllegalArgumentException();
                row.signatures.forEach(s -> archive.index(row, s.office()));
            }
            if (version == 3) {
                if (!(root.get("Certificates") instanceof ListTag copies)
                        || (!copies.isEmpty() && copies.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
                for (int i = 0; i < copies.size(); i++) {
                    var cert = Certificate.decode(copies.getCompound(i));
                    var row = archive.entry(cert.source());
                    if (row == null || !row.original.issuer().id().equals(cert.office().id())
                            || row.original.issuer().scope() != cert.office().scope()
                            || archive.certificates.put(cert.id(), cert) != null) throw new IllegalArgumentException();
                }
            } else {
                archive.setDirty();
            }
        } catch (RuntimeException error) {
            var locked = new DocumentArchive();
            locked.unreadable = root.copy();
            LogUtils.getLogger().error("Invalid or unsupported document archive. Original data preserved, signing disabled.", error);
            return locked;
        }
        return archive;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (unreadable != null) return unreadable.copy();
        var records = new ListTag();
        entries.values().forEach(row -> records.add(row.encode()));
        var copies = new ListTag();
        certificates.values().forEach(cert -> copies.add(cert.encode()));
        root.put("Certificates", copies);
        root.putInt("Version", 3);
        root.put("Records", records);
        return root;
    }
}
