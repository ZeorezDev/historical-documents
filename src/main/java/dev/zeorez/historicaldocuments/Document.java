package dev.zeorez.historicaldocuments;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record Document(UUID id, Fields fields, Issuer issuer, Seal seal) {
    static final String TAG = "HistoricalDocument";
    public static final int TITLE = 80, TYPE = 48, RECIPIENT = 80, BODY = 2000;

    public enum Scope { PERSONAL, TOWN, NATION }

    public record Fields(String title, String type, String recipient, String body) {
        public boolean valid() {
            return !title.isBlank() && !type.isBlank() && !body.isBlank()
                    && clean(title, TITLE, false) && clean(type, TYPE, false)
                    && clean(recipient, RECIPIENT, false) && clean(body, BODY, true);
        }
    }

    public record Issuer(Scope scope, UUID id, String name) {
        public Component label() {
            return HistoricalDocuments.msg("issuer." + scope.name().toLowerCase(Locale.ROOT), name);
        }
    }

    public record Seal(UUID signer, String name, long time) {}

    static boolean clean(String value, int limit, boolean multiline) {
        return value.length() <= limit && value.codePoints().allMatch(c ->
                (multiline && c == '\n') || (!Character.isISOControl(c) && c != 0xa7
                        && !(c >= 0xd800 && c <= 0xdfff) && Character.getType(c) != Character.FORMAT));
    }

    public CompoundTag encode() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Id", id);
        tag.putString("Title", fields.title);
        tag.putString("Type", fields.type);
        tag.putString("Recipient", fields.recipient);
        tag.putString("Body", fields.body);
        tag.putString("Scope", issuer.scope.name());
        tag.putUUID("IssuerId", issuer.id);
        tag.putString("IssuerName", issuer.name);
        tag.putBoolean("Official", seal != null);
        if (seal != null) {
            tag.putUUID("Signer", seal.signer);
            tag.putString("SignerName", seal.name);
            tag.putLong("SignedAt", seal.time);
        }
        return tag;
    }

    public static Optional<Document> decode(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id") || !tag.hasUUID("IssuerId")) return Optional.empty();
        Scope scope;
        try {
            scope = Scope.valueOf(tag.getString("Scope"));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        var fields = new Fields(tag.getString("Title"), tag.getString("Type"),
                tag.getString("Recipient"), tag.getString("Body"));
        var issuer = new Issuer(scope, tag.getUUID("IssuerId"), tag.getString("IssuerName"));
        if (!fields.valid() || issuer.name.isBlank() || !clean(issuer.name, 80, false)) return Optional.empty();
        Seal seal = null;
        if (tag.getBoolean("Official")) {
            String name = tag.getString("SignerName");
            long time = tag.getLong("SignedAt");
            if (scope == Scope.PERSONAL || !tag.hasUUID("Signer") || name.isBlank()
                    || !clean(name, 80, false) || time <= 0) return Optional.empty();
            seal = new Seal(tag.getUUID("Signer"), name, time);
        }
        var document = new Document(tag.getUUID("Id"), fields, issuer, seal);
        // Reject missing, extra and incorrectly typed fields, even if getters return defaults.
        return document.encode().equals(tag) ? Optional.of(document) : Optional.empty();
    }

    public static Optional<Document> read(ItemStack stack) {
        return stack.is(HistoricalDocuments.WRITTEN.get()) && stack.getCount() == 1
                ? decode(stack.getTagElement(TAG)) : Optional.empty();
    }

    public ItemStack item() {
        var stack = new ItemStack(HistoricalDocuments.WRITTEN.get());
        stack.addTagElement(TAG, encode());
        return stack;
    }
}
