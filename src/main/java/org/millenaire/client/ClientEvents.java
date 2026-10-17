package org.millenaire.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.millenaire.Millenaire;
import org.millenaire.block.ModBlockEntities;
import org.millenaire.block.ModBlocks;
import org.millenaire.client.gui.FirePitScreen;
import org.millenaire.client.gui.LockedChestScreen;
import org.millenaire.client.gui.TradeScreen;
import org.millenaire.client.model.MillFemaleAsymModel;
import org.millenaire.client.model.MillFemaleSymModel;
import org.millenaire.client.model.MillMaleModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.client.render.FirePitRenderer;
import org.millenaire.client.render.LockedChestRenderer;
import org.millenaire.client.render.MillVillagerRenderer;
import org.millenaire.client.render.MillWallDecorationRenderer;
import org.millenaire.client.render.VillagePanelRenderer;
import org.millenaire.commerce.ModMenuTypes;
import org.millenaire.entity.ModEntities;
import org.millenaire.item.ModItems;
import org.millenaire.monster.client.DeAlziendeRenderer;
import org.millenaire.monster.client.DrakenkoningRenderer;
import org.millenaire.monster.client.DrievoudigeVorstRenderer;
import org.millenaire.monster.client.GevallenSerafRenderer;
import org.millenaire.monster.client.ScharlakenKoninginRenderer;
import org.millenaire.monster.client.ZesarmigeRazernijRenderer;
import org.millenaire.monster.pack.client.PackMonsterRenderer;
import org.millenaire.labyrinth.client.GebondeneRenderer;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class ClientEvents {
   private ClientEvents() {
   }

   @SubscribeEvent
   public static void onRegisterRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)ModEntities.MILL_VILLAGER.get(), MillVillagerRenderer::new);
      event.registerEntityRenderer((EntityType)ModEntities.WALL_DECORATION.get(), MillWallDecorationRenderer::new);
      event.registerEntityRenderer(ModEntities.DRAKENKONING.get(), DrakenkoningRenderer::new);
      event.registerEntityRenderer(ModEntities.DE_ALZIENDE.get(), DeAlziendeRenderer::new);
      event.registerEntityRenderer(ModEntities.DRIEVOUDIGE_VORST.get(), DrievoudigeVorstRenderer::new);
      event.registerEntityRenderer(ModEntities.GEVALLEN_SERAF.get(), GevallenSerafRenderer::new);
      event.registerEntityRenderer(ModEntities.SCHARLAKEN_KONINGIN.get(), ScharlakenKoninginRenderer::new);
      event.registerEntityRenderer(ModEntities.ZESARMIGE_RAZERNIJ.get(), ZesarmigeRazernijRenderer::new);
      event.registerEntityRenderer(ModEntities.HORSE_WIZARD.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.PEGASUS.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.ZUZU.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.BIRDMAN.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.CTHULU.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.JUNRA.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.NEBULA.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.SDA.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.ZAVA.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.DISCD.get(), PackMonsterRenderer::new);
      event.registerEntityRenderer(ModEntities.GEBONDENE.get(), GebondeneRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.LOCKED_CHEST.get(), LockedChestRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.VILLAGE_PANEL.get(), VillagePanelRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.FIRE_PIT.get(), FirePitRenderer::new);
   }

   @SubscribeEvent
   public static void onRegisterLayerDefinitions(RegisterLayerDefinitions event) {
      CubeDeformation none = CubeDeformation.NONE;
      CubeDeformation cloth0 = new CubeDeformation(0.1F);
      CubeDeformation cloth1 = new CubeDeformation(0.2F);
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_MALE, () -> MillMaleModel.createBodyLayer(none));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_SYM, () -> MillFemaleSymModel.createBodyLayer(none));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM, () -> MillFemaleAsymModel.createBodyLayer(none));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_0, () -> MillMaleModel.createBodyLayer(cloth0));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_0, () -> MillFemaleSymModel.createBodyLayer(cloth0));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_0, () -> MillFemaleAsymModel.createBodyLayer(cloth0));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_1, () -> MillMaleModel.createBodyLayer(cloth1));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_1, () -> MillFemaleSymModel.createBodyLayer(cloth1));
      event.registerLayerDefinition(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_1, () -> MillFemaleAsymModel.createBodyLayer(cloth1));
   }

   @SubscribeEvent
   public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
      event.register(ModMenuTypes.TRADE.get(), TradeScreen::new);
      event.register(ModMenuTypes.LOCKED_CHEST.get(), LockedChestScreen::new);
      event.register(ModMenuTypes.FIRE_PIT.get(), FirePitScreen::new);
   }

   @SubscribeEvent
   public static void onRegisterBlockColors(Block event) {
      event.register(
         (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(),
         new net.minecraft.world.level.block.Block[]{
            (net.minecraft.world.level.block.Block)ModBlocks.PISTACHIO_TREE_LEAVES.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.APPLE_TREE_LEAVES.get(),
            (net.minecraft.world.level.block.Block)ModBlocks.OLIVE_TREE_LEAVES.get()
         }
      );
   }

   @SubscribeEvent
   public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(new LanguageFallbackListener());
   }

   @SubscribeEvent
   public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
      event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("millenaire", "bounty_hud"), BountyHudClient::render);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      ModContainer container = Millenaire.getModContainer();
      container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)(mc, parent) -> new ConfigurationScreen(container, parent));
      event.enqueueWork(() -> {
         registerBowProperties((BowItem)ModItems.YUMIBOW.get());
         registerBowProperties((BowItem)ModItems.SELJUK_BOW.get());
         registerBowProperties((BowItem)ModItems.INUIT_BOW.get());
      });
   }

   private static void registerBowProperties(BowItem bow) {
      ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
         if (entity == null) {
            return 0.0F;
         } else {
            return entity.getUseItem() != stack ? 0.0F : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
         }
      });
      ItemProperties.register(
         bow,
         ResourceLocation.withDefaultNamespace("pulling"),
         (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
      );
   }

   @SubscribeEvent
   public static void onComputeFovModifier(ComputeFovModifierEvent event) {
      Player player = event.getPlayer();
      if (player.isUsingItem()) {
         ItemStack used = player.getUseItem();
         if (!used.is(Items.BOW)) {
            if (used.getItem() instanceof BowItem) {
               float vanillaRaw = event.getFovModifier();
               float withBow = vanillaRaw * computeBowFovMultiplier(player.getTicksUsingItem());
               float scale = ((Double)Minecraft.getInstance().options.fovEffectScale().get()).floatValue();
               event.setNewFovModifier(Mth.lerp(scale, 1.0F, withBow));
            }
         }
      }
   }

   public static float computeBowFovMultiplier(int ticksUsingItem) {
      float charge = ticksUsingItem / 20.0F;
      if (charge > 1.0F) {
         charge = 1.0F;
      } else {
         charge *= charge;
      }

      return 1.0F - charge * 0.15F;
   }
}
