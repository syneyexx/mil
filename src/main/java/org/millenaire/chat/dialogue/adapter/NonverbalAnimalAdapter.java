package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

public final class NonverbalAnimalAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 300;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof Animal && !(entity instanceof net.minecraft.world.entity.animal.allay.Allay);
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.NONVERBAL_ANIMAL;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.nonverbal(false);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
      return null;
   }

   @Override
   public NpcDialogueIdentity identity(LivingEntity entity) {
      boolean persist = entity instanceof AbstractHorse;
      return NpcDialogueIdentity.from(entity, persist);
   }

   @Override
   public NpcDialogueContext buildContext(
      ServerPlayer player, LivingEntity entity, NpcMind mind, NpcMindSavedData data, String speech, String language
   ) {
      String type = AdapterContextSupport.entityTypeLabel(entity);
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .speciesOrTypeLabel(type)
         .child(entity.isBaby())
         .ageCategory(entity.isBaby() ? "young" : "adult")
         .communicationInstructions(
            "Describe sounds, body language and behaviour only. Never discuss politics, money, villages, or human affairs."
         )
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.animal").getString())
         .systemPromptOverride(
            AdapterContextSupport.nonverbalSystemPrompt(
               "You are a Minecraft " + type + ". Only animal sounds and body language. Never speak as a human."
            )
         )
         .build();
   }
}
