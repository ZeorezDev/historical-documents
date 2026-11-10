package dev.zeorez.historicaldocuments;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class DocumentArchive extends SavedData {
    private final Map<UUID, Document> entries = new HashMap<>();
    private final HashSet<UUID> damaged = new HashSet<>();
    private CompoundTag unreadable;

    public static DocumentArchive get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DocumentArchive::load,
                DocumentArchive::new, "historical_documents");
    }

    boolean reserved(UUID id) {
        return entries.containsKey(id) || damaged.contains(id);
    }

    public boolean verifies(Document doc) {
        return doc.seal() != null && doc.equals(entries.get(doc.id()));
    }

    boolean register(Document doc) {
        if (unreadable != null || doc.seal() == null || reserved(doc.id())) return false;
        entries.put(doc.id(), doc);
        setDirty();
        return true;
    }

    public static DocumentArchive load(CompoundTag root) {
        var archive = new DocumentArchive();
        var raw = root.get("Records");
        if (root.getInt("Version") != 1 || !(raw instanceof ListTag records)
                || (!records.isEmpty() && records.getElementType() != Tag.TAG_COMPOUND)) {
            archive.unreadable = root.copy();
            LogUtils.getLogger().error("Unreadable Historical Documents archive. Signing disabled until recovery.");
            return archive;
        }
        for (int i = 0; i < records.size(); i++) {
            var tag = records.getCompound(i);
            if (!tag.hasUUID("Id")) continue;
            var id = tag.getUUID("Id");
            var doc = Document.decode(tag).filter(d -> d.seal() != null);
            if (doc.isEmpty() || archive.reserved(id)) {
                archive.entries.remove(id);
                archive.damaged.add(id);
            } else {
                archive.entries.put(id, doc.get());
            }
        }
        return archive;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (unreadable != null) return unreadable.copy();
        var records = new ListTag();
        entries.values().forEach(d -> records.add(d.encode()));
        damaged.forEach(id -> {
            var marker = new CompoundTag();
            marker.putUUID("Id", id);
            records.add(marker);
        });
        root.putInt("Version", 1);
        root.put("Records", records);
        return root;
    }
}
