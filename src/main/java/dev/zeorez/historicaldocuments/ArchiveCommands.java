package dev.zeorez.historicaldocuments;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.server.level.ServerPlayer;
import static net.minecraft.commands.Commands.*;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.msg;
import static dev.zeorez.historicaldocuments.Document.Scope;

final class ArchiveCommands {
    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = literal("archive").executes(c -> {
            c.getSource().sendSuccess(() -> msg("archive.help"), false);
            return 1;
        });
        for (var scope : new Scope[]{Scope.TOWN, Scope.NATION}) {
            root.then(literal(scope == Scope.TOWN ? "town" : "nation")
                    .then(argument("name", StringArgumentType.string())
                    .executes(c -> list(c.getSource(), scope, StringArgumentType.getString(c, "name"), 1))
                    .then(argument("page", IntegerArgumentType.integer(1)).executes(c -> list(c.getSource(),
                            scope, StringArgumentType.getString(c, "name"), IntegerArgumentType.getInteger(c, "page"))))));
        }
        return root.then(literal("info").then(argument("id", UuidArgument.uuid()).executes(c -> {
            var entry = DocumentArchive.get(c.getSource().getServer()).entry(UuidArgument.getUuid(c, "id"));
            if (entry == null) return fail(c.getSource(), "archive.empty");
            c.getSource().sendSuccess(entry::summary, false);
            return 1;
        })));
    }

    private static int list(CommandSourceStack source, Scope scope, String name, int page) {
        var archive = DocumentArchive.get(source.getServer());
        var ids = archive.resolve(source.getServer(), scope, name);
        if (ids.isEmpty()) return fail(source, "archive.empty");
        if (ids.size() > 1) {
            source.sendFailure(msg("archive.ambiguous", ids.stream().limit(5).map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "))));
            return 0;
        }
        var rows = archive.records(ids.iterator().next());
        long pages = (rows.size() + 4L) / 5;
        if (page > pages) return fail(source, "archive.empty");
        source.sendSuccess(() -> msg("archive.page", page, pages), false);
        rows.stream().skip(5L * (page - 1)).limit(5).forEach(row -> source.sendSuccess(row::summary, false));
        return 1;
    }

    static int change(CommandSourceStack source, String action, String reason, ServerPlayer guest) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var error = DocumentArchive.get(source.getServer()).change(player, action, reason, guest);
        if (error != null) return fail(source, error);
        source.sendSuccess(() -> msg(action + ".ok"), false);
        if (guest != null) guest.sendSystemMessage(msg("invite.notice", Document.read(player.getMainHandItem()).orElseThrow().id().toString()));
        return 1;
    }

    private static int fail(CommandSourceStack source, String key) {
        source.sendFailure(msg(key));
        return 0;
    }
}
