package dev.zeorez.historicaldocuments;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(HistoricalDocuments.ID)
public final class HistoricalDocuments {
    public static final String ID = "historical_documents";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<Item> BLANK = ITEMS.register("blank_document", DocumentItem::new);
    public static final RegistryObject<Item> WRITTEN = ITEMS.register("historical_document", DocumentItem::new);

    public HistoricalDocuments() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        DocumentNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(DocumentCommands::register);
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(ID + "." + key, args);
    }
}
