package dev.zeorez.historicaldocuments.client;

import dev.zeorez.historicaldocuments.Document;
import dev.zeorez.historicaldocuments.DocumentNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.time.Instant;
import static dev.zeorez.historicaldocuments.HistoricalDocuments.msg;

public final class DocumentScreen extends Screen {
    private final DocumentNetwork.Open packet;
    private EditBox titleField, typeField, recipientField;
    private MultiLineEditBox body;
    private Button save, sign;
    private int choice, left, top, pageWidth, pageHeight;
    private static final int INK = 0xff382e27, RED = 0xff81372e;

    private DocumentScreen(DocumentNetwork.Open packet) {
        super(msg(packet.document() != null && packet.document().seal() != null ? "official" : "editor"));
        this.packet = packet;
    }

    public static void open(DocumentNetwork.Open packet) {
        Minecraft.getInstance().setScreen(new DocumentScreen(packet));
    }

    @Override
    protected void init() {
        pageWidth = Math.min(420, width - 12);
        pageHeight = Math.min(320, height - 8);
        left = (width - pageWidth) / 2;
        top = (height - pageHeight) / 2;
        int x = left + 10, w = pageWidth - 24;
        var doc = packet.document();
        if (doc != null && doc.seal() != null) {
            var text = Component.literal(doc.fields().title()).append("\n").append(msg("type.line", doc.fields().type()))
                    .append("\n").append(doc.issuer().label()).append("\n");
            if (!doc.fields().recipient().isEmpty()) text.append(msg("recipient.line", doc.fields().recipient())).append("\n");
            text.append("\n").append(doc.fields().body()).append("\n\n")
                    .append(msg(doc.issuer().scope() == Document.Scope.TOWN ? "seal.town" : "seal.nation"))
                    .append("\n").append(packet.history())
                    .append("\n").append(doc.id().toString());
            addRenderableWidget(new FittingMultiLineTextWidget(x, top + 30, w, pageHeight - 66, text, font));
            button(x, top + pageHeight - 25, w, msg("close"), b -> onClose());
            return;
        }

        // Preserve unsaved input when the window or GUI scale changes.
        var fields = titleField != null ? fields() : doc != null ? doc.fields() : new Document.Fields("", "", "", "");
        int half = (w - 6) / 2;
        titleField = field(x, top + 35, w, "title", Document.TITLE, fields.title());
        typeField = field(x, top + 66, half, "type", Document.TYPE, fields.type());
        recipientField = field(x + half + 6, top + 66, w - half - 6, "recipient", Document.RECIPIENT, fields.recipient());
        var issuerButton = button(x, top + 96, w, issuerLabel(), b -> {
            choice = (choice + 1) % packet.choices().size();
            b.setMessage(issuerLabel());
        });
        issuerButton.active = packet.choices().size() > 1;
        body = addRenderableWidget(new MultiLineEditBox(font, x, top + 128, w, pageHeight - 174,
                msg("body.hint"), msg("body")));
        body.setCharacterLimit(Document.BODY);
        body.setValue(fields.body());
        var y = top + pageHeight - 25;
        int buttons = packet.maySign() ? 3 : 2;
        int bw = (w - (buttons - 1) * 4) / buttons;
        save = button(x, y, bw, msg("save"), b -> {
            DocumentNetwork.CHANNEL.sendToServer(new DocumentNetwork.Save(packet.token(),
                    packet.choices().get(choice).scope(), fields()));
            onClose();
        });
        if (packet.maySign()) {
            sign = button(x + bw + 4, y, bw, msg("sign"), b -> {
                DocumentNetwork.CHANNEL.sendToServer(new DocumentNetwork.Sign(packet.token()));
                onClose();
            });
        }
        button(x + (buttons - 1) * (bw + 4), y, bw, msg("close"), b -> onClose());
        refresh();
        setInitialFocus(titleField);
    }

    private Component issuerLabel() { return packet.choices().get(choice).label(); }

    private Button button(int x, int y, int w, Component text, Button.OnPress press) {
        return addRenderableWidget(Button.builder(text, press).bounds(x, y, w, 19).build());
    }

    private EditBox field(int x, int y, int w, String label, int max, String initial) {
        var box = addRenderableWidget(new EditBox(font, x, y, w, 16, msg(label)));
        box.setMaxLength(max);
        box.setValue(initial);
        return box;
    }

    private Document.Fields fields() {
        return new Document.Fields(titleField.getValue(), typeField.getValue(), recipientField.getValue(), body.getValue());
    }

    private void refresh() {
        var current = fields();
        save.active = current.valid();
        if (sign != null) sign.active = current.equals(packet.document().fields());
    }

    @Override
    public void tick() {
        if (body != null) {
            titleField.tick();
            typeField.tick();
            recipientField.tick();
            body.tick();
            refresh();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g);
        g.fill(left, top, left + pageWidth, top + pageHeight, 0xffe7d8b6);
        g.fill(left + 8, top + 19, left + pageWidth - 8, top + 20, RED);
        g.drawString(font, title, left + 10, top + 7, INK, false);
        if (body != null) {
            label(g, "title", titleField.getX(), 24);
            label(g, "type", typeField.getX(), 55);
            label(g, "recipient", recipientField.getX(), 55);
            label(g, "issuer", left + 10, 85);
            label(g, "body", left + 10, 117);
            refresh();
        } else {
            int x = left + pageWidth - 22;
            g.fill(x, top + 4, x + 13, top + 17, RED);
            g.drawCenteredString(font, "◆", x + 6, top + 6, 0xfff0ddba);
            g.fill(left + 10, top + 30, left + pageWidth - 14, top + pageHeight - 36, 0xff332d27);
        }
        super.render(g, mx, my, delta);
    }

    private void label(GuiGraphics g, String key, int x, int offset) {
        g.drawString(font, msg(key), x, top + offset, INK, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
