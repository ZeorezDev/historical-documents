package dev.zeorez.historicaldocuments;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import static dev.zeorez.historicaldocuments.Document.*;

public final class DocumentNetwork {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HistoricalDocuments.ID, "documents"), () -> VERSION, VERSION::equals, VERSION::equals);

    public enum Kind { ORIGINAL, PRINTED, CERTIFIED }

    public record Open(UUID token, Document document, List<Issuer> choices, boolean maySign, net.minecraft.network.chat.Component history, Kind kind) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeNbt(document == null ? null : document.encode());
            buf.writeByte(choices.size());
            for (var issuer : choices) {
                buf.writeEnum(issuer.scope());
                buf.writeUUID(issuer.id());
                buf.writeUtf(issuer.name(), 80);
            }
            buf.writeBoolean(maySign);
            buf.writeComponent(history);
            buf.writeEnum(kind);
        }

        static Open read(FriendlyByteBuf buf) {
            var token = buf.readUUID();
            var tag = buf.readNbt();
            var doc = tag == null ? null : Document.decode(tag).orElseThrow();
            int count = buf.readUnsignedByte();
            if (count < 1 || count > 3) throw new IllegalArgumentException("Invalid issuer count");
            var issuers = new ArrayList<Issuer>(count);
            for (int i = 0; i < count; i++) {
                issuers.add(new Issuer(buf.readEnum(Scope.class), buf.readUUID(), buf.readUtf(80)));
            }
            return new Open(token, doc, List.copyOf(issuers), buf.readBoolean(), buf.readComponent(), buf.readEnum(Kind.class));
        }
    }

    public record Save(UUID token, Scope scope, Fields fields) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeEnum(scope);
            buf.writeUtf(fields.title(), TITLE);
            buf.writeUtf(fields.type(), TYPE);
            buf.writeUtf(fields.recipient(), RECIPIENT);
            buf.writeUtf(fields.body(), BODY);
        }

        static Save read(FriendlyByteBuf buf) {
            return new Save(buf.readUUID(), buf.readEnum(Scope.class),
                    new Fields(buf.readUtf(TITLE), buf.readUtf(TYPE), buf.readUtf(RECIPIENT), buf.readUtf(BODY)));
        }
    }
    public record Sign(UUID token) {}

    static void register() {
        CHANNEL.messageBuilder(Open.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Open::write).decoder(Open::read)
                .consumerMainThread((packet, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> dev.zeorez.historicaldocuments.client.DocumentScreen.open(packet)))
                .add();
        CHANNEL.messageBuilder(Save.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Save::write).decoder(Save::read)
                .consumerMainThread((packet, ctx) -> {
                    var player = ctx.get().getSender();
                    if (player != null) DocumentService.save(player, packet);
                }).add();
        CHANNEL.messageBuilder(Sign.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> buf.writeUUID(packet.token())).decoder(buf -> new Sign(buf.readUUID()))
                .consumerMainThread((packet, ctx) -> {
                    var player = ctx.get().getSender();
                    if (player != null) DocumentService.sign(player, packet.token());
                }).add();
    }
}
