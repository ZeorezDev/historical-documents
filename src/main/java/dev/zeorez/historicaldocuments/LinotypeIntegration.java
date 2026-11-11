package dev.zeorez.historicaldocuments;

import dev.historicallinotype.PrintHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.*;

final class LinotypeIntegration {
    static void register() {
        PrintHandler.register(WRITTEN.get(), LinotypeIntegration::print);
    }

    private static ItemStack print(ServerPlayer operator, ItemStack input, boolean certified) {
        var doc = Document.read(input).orElse(null);
        var archive = DocumentArchive.get(operator.server);
        if (doc == null || ((doc.seal() != null || archive.reserved(doc.id())) && !archive.verifies(doc)))
            return reject(operator, "invalid");
        if (!certified) {
            var copy = new ItemStack(PRINTED.get());
            copy.addTagElement("Print", new Document(doc.id(), doc.fields(), doc.issuer(), null).encode());
            return copy;
        }
        if (!archive.verifies(doc)) return reject(operator, "unsigned");
        if (archive.entry(doc.id()).revocation != null) return reject(operator, "already.revoked");
        var office = Authority.signingOffice(operator, doc.issuer());
        if (office.isEmpty()) return reject(operator, "denied");
        var cert = new Certificate(UUID.randomUUID(), doc.id(), office.get(), operator.getUUID(), System.currentTimeMillis());
        return archive.issue(operator.server, cert) ? cert.item() : reject(operator, "archive.error");
    }

    private static ItemStack reject(ServerPlayer player, String key) {
        player.sendSystemMessage(msg(key));
        return ItemStack.EMPTY;
    }
}
