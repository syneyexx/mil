package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

/**
 * Hostiles that can support limited dialogue without altering encounter mechanics.
 * Bosses are excluded — they use atmospheric/non-intrusive handling elsewhere or generic nonverbal.
 */
public final class IntelligentHostileAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 500;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof Witch || entity instanceof EnderMan;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.INTELLIGENT_HOSTILE;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.limited(false);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
      if (entity.getTarget() == player && entity.hurtTime > 0) {
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
      String style = entity instanceof Witch
         ? "You are a Minecraft witch. Cryptic, sharp, suspicious. Short lines."
         : "You are a Minecraft enderman. Alien, sparse speech. Stare and distant words. Do not teleport or alter blocks via dialogue.";
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .speciesOrTypeLabel(type)
         .underAttack(entity.getLastHurtByMob() != null)
         .communicationInstructions("Limited hostile dialogue. Never change combat AI via words.")
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.hostile").getString())
         .systemPromptOverride(AdapterContextSupport.spokenSystemPrompt(style))
         .build();
   }
}
