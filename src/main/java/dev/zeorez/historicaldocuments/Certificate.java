package dev.zeorez.historicaldocuments;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

record Certificate(UUID id, UUID source, Document.Issuer office, UUID printer, long time) {
    CompoundTag encode() {
        var t = new CompoundTag();
        t.putUUID("Copy", id);
        t.putUUID("Original", source);
        t.putString("Scope", office.scope().name());
        t.putUUID("Office", office.id());
        t.putString("OfficeName", office.name());
        t.putUUID("Printer", printer);
        t.putLong("PrintedAt", time);
        return t;
    }

    static Certificate decode(CompoundTag t) {
        var office = new Document.Issuer(Document.Scope.valueOf(t.getString("Scope")),
                t.getUUID("Office"), t.getString("OfficeName"));
        var cert = new Certificate(t.getUUID("Copy"), t.getUUID("Original"), office, t.getUUID("Printer"), t.getLong("PrintedAt"));
        if (!ArchiveEntry.validText(office.name(), 80) || cert.time <= 0 || !cert.encode().equals(t))
            throw new IllegalArgumentException("Invalid certificate");
        return cert;
    }

    ItemStack item() {
        var result = new ItemStack(HistoricalDocuments.CERTIFIED.get());
        result.addTagElement("Certificate", encode());
        return result;
    }
}
