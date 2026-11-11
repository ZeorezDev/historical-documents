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

    public static final RegistryObject<Item> PRINTED = ITEMS.register("printed_copy", DocumentItem::new);
    public static final RegistryObject<Item> CERTIFIED = ITEMS.register("certified_copy", DocumentItem::new);

    public HistoricalDocuments() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        if (net.minecraftforge.fml.ModList.get().isLoaded("historicallinotype")) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setupPrinting);
        }
        DocumentNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(DocumentCommands::register);
    }

    private void setupPrinting(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LinotypeIntegration.register());
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(ID + "." + key, args);
    }
}
