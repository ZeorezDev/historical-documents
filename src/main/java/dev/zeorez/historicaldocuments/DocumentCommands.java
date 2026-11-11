package dev.zeorez.historicaldocuments;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import static net.minecraft.commands.Commands.literal;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.*;

final class DocumentCommands {
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("document")
                .executes(c -> help(c.getSource()))
                .then(literal("help").executes(c -> help(c.getSource())))
                .then(literal("give").requires(s -> s.hasPermission(2)).executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    var stack = new ItemStack(BLANK.get());
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                    c.getSource().sendSuccess(() -> msg("given"), false);
                    return 1;
                }))
                .then(ArchiveCommands.tree())
                .then(literal("revoke").then(net.minecraft.commands.Commands.argument("reason", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(c -> ArchiveCommands.change(c.getSource(), "revoke", c.getArgument("reason", String.class), null))))
                .then(literal("invite").then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(c -> ArchiveCommands.change(c.getSource(), "invite", "", net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player")))))
                .then(literal("cosign").executes(c -> ArchiveCommands.change(c.getSource(), "cosign", "", null)))
                .then(literal("verify").executes(c -> inspect(c.getSource(), true)))
                .then(literal("info").executes(c -> inspect(c.getSource(), false))));
    }

    static int inspect(CommandSourceStack source, boolean verify) throws CommandSyntaxException {
        var stack = source.getPlayerOrException().getMainHandItem();
        if (CopyView.isCopy(stack)) {
            var copy = CopyView.resolve(DocumentArchive.get(source.getServer()), stack);
            if (copy == null) {
                source.sendFailure(msg("invalid"));
                return 0;
            }
            source.sendSuccess(() -> msg("copy.source", copy.doc().fields().title(), copy.doc().id().toString())
                    .append("\n").append(copy.details()), false);
            return !verify || copy.kind() == DocumentNetwork.Kind.CERTIFIED ? 1 : 0;
        }
        if (!stack.is(WRITTEN.get())) {
            source.sendFailure(msg("hold"));
            return 0;
        }
        var archive = DocumentArchive.get(source.getServer());
        var doc = Document.read(stack).orElse(null);
        if (doc == null || ((verify || doc.seal() != null || archive.reserved(doc.id())) && !archive.verifies(doc))) {
            boolean draft = doc != null && doc.seal() == null && !archive.reserved(doc.id());
            source.sendFailure(msg(draft ? "unsigned" : "invalid"));
            return 0;
        }
        if (verify) {
            var row = archive.entry(doc.id());
            source.sendSuccess(() -> msg("verified", doc.id().toString()).append(" · ").append(row.status())
                    .append(" · ").append(msg("signatures", row.signatureCount())), false);
        } else {
            source.sendSuccess(() -> msg("info", doc.fields().title(), doc.fields().type(), doc.issuer().label(),
                    doc.seal() == null ? msg("draft") : archive.entry(doc.id()).status(), doc.id().toString()), false);
            if (doc.seal() != null) source.sendSuccess(() -> msg("signature", doc.seal().name(),
                    java.time.Instant.ofEpochMilli(doc.seal().time()).toString()), false);
        }
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> msg("help"), false);
        source.sendSuccess(() -> msg("help.archive"), false);
        if (source.hasPermission(2)) source.sendSuccess(() -> msg("help.give"), false);
        return 1;
    }
}
