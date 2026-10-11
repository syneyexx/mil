package org.millenaire.companion;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.millenaire.chat.NpcChatSanitize;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;

/**
 * Routes vanilla T-chat to the bonded companion (no NPC chat UI).
 */
public final class CompanionChatBridge {
   private CompanionChatBridge() {
   }

   public static void bootstrap() {
      NeoForge.EVENT_BUS.addListener(CompanionChatBridge::onServerChat);
   }

   private static void onServerChat(ServerChatEvent event) {
      if (!CompanionService.enabled() || !chatViaTEnabled()) {
         return;
      }
      ServerPlayer player = event.getPlayer();
      String sanitized = NpcChatSanitize.sanitize(event.getMessage().getString());
      if (sanitized.isBlank() || sanitized.startsWith("/")) {
         return;
      }

      ResolvedCompanion resolved = resolveNearbyCompanion(player);
      if (resolved == null) {
         return;
      }
      if (!resolved.inRange) {
         player.sendSystemMessage(Component.translatable("message.millenaire.companion.chat_too_far"));
         return;
      }

      boolean started = CompanionBrain.handleVanillaChat(
         player.serverLevel(), player, resolved.companion, resolved.bond, sanitized
      );
      if (started) {
         player.sendSystemMessage(
            Component.translatable("message.millenaire.companion.chat_thinking", resolved.companion.getName())
         );
      } else {
         player.sendSystemMessage(Component.translatable("message.millenaire.companion.chat_busy"));
      }
   }

   @Nullable
   static ResolvedCompanion resolveNearbyCompanion(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionBondSavedData data = CompanionBondSavedData.get(level);
      CompanionBond bond = data.get(player.getUUID());
      if (bond == null || bond.dismissed() || bond.companionEntityId() == null) {
         return null;
      }
      MillVillager companion = findCompanionEntity(level, bond.companionEntityId());
      if (companion == null) {
         return null;
      }
      double range = chatRange();
      boolean inRange = companion.level() == player.level() && companion.distanceToSqr(player) <= range * range;
      return new ResolvedCompanion(companion, bond, inRange);
   }

   @Nullable
   private static MillVillager findCompanionEntity(ServerLevel playerLevel, java.util.UUID entityId) {
      Entity entity = playerLevel.getEntity(entityId);
      if (entity instanceof MillVillager villager && villager.isAlive() && villager.isCompanion()) {
         return villager;
      }
      ServerLevel overworld = playerLevel.getServer().getLevel(Level.OVERWORLD);
      if (overworld != null && overworld != playerLevel) {
         Entity other = overworld.getEntity(entityId);
         if (other instanceof MillVillager villager && villager.isAlive() && villager.isCompanion()) {
            return villager;
         }
      }
      return null;
   }

   private static boolean chatViaTEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.companionChatViaT.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static double chatRange() {
      try {
         return MillenaireServerConfig.SERVER.companionChatRange.getAsInt();
      } catch (IllegalStateException e) {
         return 48;
      }
   }

   record ResolvedCompanion(MillVillager companion, CompanionBond bond, boolean inRange) {
   }
}
