package dev.zeorez.historicaldocuments;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.time.Instant;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.*;

record CopyView(Document doc, DocumentNetwork.Kind kind, Component details) {
    static boolean isCopy(ItemStack stack) { return stack.is(PRINTED.get()) || stack.is(CERTIFIED.get()); }

    static CopyView resolve(DocumentArchive archive, ItemStack stack) {
        if (stack.is(PRINTED.get())) {
            return Document.decode(stack.getTagElement("Print")).filter(d -> d.seal() == null)
                    .map(d -> new CopyView(d, DocumentNetwork.Kind.PRINTED, msg("copy.ordinary"))).orElse(null);
        }
        var cert = archive.certificate(stack);
        if (cert == null) return null;
        var entry = archive.entry(cert.source());
        if (entry == null) return null;
        var details = msg("copy.certificate", cert.id().toString(), cert.office().label(),
                cert.printer().toString(), Instant.ofEpochMilli(cert.time()).toString())
                .append("\n").append(entry.history());
        return new CopyView(entry.original, DocumentNetwork.Kind.CERTIFIED, details);
    }
}
