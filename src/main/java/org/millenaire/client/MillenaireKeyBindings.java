package org.millenaire.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.network.InfoPanelRequestPayload;
import org.millenaire.network.NpcChatOpenRequestPayload;
import org.millenaire.network.ToggleStancePayload;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class MillenaireKeyBindings {
   public static final KeyMapping KEY_VILLAGES = new KeyMapping("key.millenaire.villages", Type.KEYSYM, 86, "key.categories.millenaire");
   public static final KeyMapping KEY_INFO_PANEL = new KeyMapping("key.millenaire.info_panel", Type.KEYSYM, 77, "key.categories.millenaire");
   public static final KeyMapping KEY_STANCE = new KeyMapping("key.millenaire.escorts", Type.KEYSYM, 71, "key.categories.millenaire");
   public static final KeyMapping KEY_TALK = new KeyMapping("key.millenaire.talk", Type.KEYSYM, 72, "key.categories.millenaire");
   private static int lastHintEntityId = Integer.MIN_VALUE;
   private static int hintCooldown;

   private MillenaireKeyBindings() {
   }

   @SubscribeEvent
   public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
      event.register(KEY_VILLAGES);
      event.register(KEY_INFO_PANEL);
      event.register(KEY_STANCE);
      event.register(KEY_TALK);
      NeoForge.EVENT_BUS.addListener(MillenaireKeyBindings::onClientTick);
   }

   private static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      if (mc.screen == null) {
         while (KEY_VILLAGES.consumeClick()) {
            mc.player.connection.sendCommand("millenaire villages");
         }

         while (KEY_INFO_PANEL.consumeClick()) {
            PacketDistributor.sendToServer(new InfoPanelRequestPayload(), new CustomPacketPayload[0]);
         }

         while (KEY_STANCE.consumeClick()) {
            boolean aggressive = !Screen.hasShiftDown();
            PacketDistributor.sendToServer(new ToggleStancePayload(aggressive), new CustomPacketPayload[0]);
         }

         while (KEY_TALK.consumeClick()) {
            LivingEntity target = findTalkTarget(mc);
            if (target != null) {
               PacketDistributor.sendToServer(new NpcChatOpenRequestPayload(target.getId()), new CustomPacketPayload[0]);
            } else {
               mc.player.displayClientMessage(Component.translatable("hint.millenaire.talk.no_target"), true);
            }
         }

         updateTalkHint(mc);
      } else {
         lastHintEntityId = Integer.MIN_VALUE;
         hintCooldown = 0;
      }
   }

   private static void updateTalkHint(Minecraft mc) {
      LivingEntity underCrosshair = findCrosshairNpc(mc);
      int chatRange = 4;
      try {
         chatRange = MillenaireServerConfig.SERVER.npcChatChatRange.getAsInt();
      } catch (Exception ignored) {
      }
      if (underCrosshair == null || mc.player.distanceTo(underCrosshair) > chatRange) {
         if (hintCooldown > 0) {
            hintCooldown--;
         }
         if (hintCooldown <= 0) {
            lastHintEntityId = Integer.MIN_VALUE;
         }
         return;
      }
      int id = underCrosshair.getId();
      if (id != lastHintEntityId || hintCooldown <= 0) {
         String keyName = KEY_TALK.getTranslatedKeyMessage().getString();
         mc.player.displayClientMessage(Component.translatable("hint.millenaire.talk", keyName), true);
         lastHintEntityId = id;
         hintCooldown = 40;
      } else {
         hintCooldown = 40;
      }
   }

   /**
    * Crosshair-only targeting. Never silently picks a different nearby NPC.
    * Server independently validates eligibility and distance.
    */
   private static LivingEntity findTalkTarget(Minecraft mc) {
      int chatRange = 4;
      try {
         chatRange = MillenaireServerConfig.SERVER.npcChatChatRange.getAsInt();
      } catch (Exception ignored) {
      }
      LivingEntity under = findCrosshairNpc(mc);
      if (under != null && mc.player.distanceTo(under) <= chatRange) {
         return under;
      }
      return null;
   }

   private static LivingEntity findCrosshairNpc(Minecraft mc) {
      HitResult hit = mc.hitResult;
      if (hit instanceof EntityHitResult entityHit) {
         Entity entity = entityHit.getEntity();
         if (entity instanceof LivingEntity living && !(living instanceof Player)) {
            return living;
         }
      }
      return null;
   }
}
