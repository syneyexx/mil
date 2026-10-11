package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.millenaire.Millenaire;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class IllagerAdapter implements NpcDialogueAdapter {
   private static final ResourceLocation ILLAGER_CULTURE =
      ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, "illagers");
   private static final double NEARBY_VILLAGE_RANGE_SQ = 96.0 * 96.0;

   @Override
   public int priority() {
      return 700;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof AbstractIllager;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.ILLAGER;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.spoken(false, false, false);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
      AbstractIllager illager = (AbstractIllager)entity;
      if (illager.getTarget() == player && illager.getHealth() < illager.getMaxHealth() * 0.35F) {
         return "gui.millenaire.npcchat.combat";
      }
      return null;
   }

   @Override
   public NpcDialogueIdentity identity(LivingEntity entity) {
      return NpcDialogueIdentity.from(entity, false);
   }

   @Override
   public NpcDialogueContext buildContext(
      ServerPlayer player, LivingEntity entity, NpcMind mind, NpcMindSavedData data, String speech, String language
   ) {
      String type = AdapterContextSupport.entityTypeLabel(entity);
      boolean nearMillIllagers = nearbyIllagerSettlement(player);
      String prompt = nearMillIllagers
         ? "You are a Minecraft illager ("
            + type
            + "). Suspicious and hostile. You may acknowledge distant Illager strongholds of your people, but you are NOT a Millenaire villager citizen and do not invent membership, trades, or loot."
         : "You are a Minecraft illager ("
            + type
            + "). Suspicious and hostile. Do not become a helpful guide. Do not invent raid loot for the player.";
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .faction(nearMillIllagers ? "illager (related to Illager strongholds)" : "illager")
         .underAttack(entity.getLastHurtByMob() != null)
         .communicationInstructions("Threatening, suspicious or hostile tone. Short lines. No friendly village gossip.")
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.illager").getString())
         .systemPromptOverride(AdapterContextSupport.spokenSystemPrompt(prompt))
         .build();
   }

   private static boolean nearbyIllagerSettlement(ServerPlayer player) {
      if (!(player.level() instanceof ServerLevel level)) {
         return false;
      }
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (!ILLAGER_CULTURE.equals(village.getCultureId())) {
            continue;
         }
         if (village.getCenter().distToCenterSqr(player.getX(), player.getY(), player.getZ()) <= NEARBY_VILLAGE_RANGE_SQ) {
            return true;
         }
      }
      return false;
   }
}
