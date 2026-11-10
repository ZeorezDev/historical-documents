package dev.zeorez.historicaldocuments;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.msg;

final class DocumentItem extends Item {
    DocumentItem() { super(new Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) DocumentService.open(sp, hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> lines, TooltipFlag flag) {
        if (!stack.is(HistoricalDocuments.WRITTEN.get())) return;
        Document.read(stack).ifPresentOrElse(doc -> {
            lines.add(Component.literal(doc.fields().title()));
            lines.add(msg("type.line", doc.fields().type()));
            lines.add(doc.issuer().label());
            lines.add(msg(doc.seal() == null ? "draft" : "sealed.hint"));
            if (doc.seal() != null) lines.add(msg("signed.by", doc.seal().name()));
        }, () -> lines.add(msg("invalid")));
    }
}
