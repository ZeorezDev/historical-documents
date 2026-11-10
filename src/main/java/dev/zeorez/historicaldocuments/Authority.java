package dev.zeorez.historicaldocuments;

import dev.zeorez.historicalnations.api.HistoricalNationsApi;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import static dev.zeorez.historicaldocuments.Document.*;

final class Authority {
    static Optional<Issuer> current(ServerPlayer player, Scope scope) {
        var api = HistoricalNationsApi.get(player.server);
        var id = player.getUUID();
        return switch (scope) {
            case PERSONAL -> Optional.of(new Issuer(scope, id, player.getGameProfile().getName()));
            case TOWN -> api.getPlayerTown(id).map(town -> new Issuer(scope, town.id(), town.name()));
            case NATION -> api.getPlayerNation(id).map(nation -> new Issuer(scope, nation.id(), nation.name()));
        };
    }

    static Optional<Issuer> signingOffice(ServerPlayer player, Issuer intended) {
        var api = HistoricalNationsApi.get(player.server);
        boolean holdsOffice = switch (intended.scope()) {
            case TOWN -> api.isTownMayor(player.getUUID());
            case NATION -> api.isNationLeader(player.getUUID());
            case PERSONAL -> false;
        };
        return holdsOffice ? current(player, intended.scope()).filter(i -> i.id().equals(intended.id()))
                : Optional.empty();
    }
}
