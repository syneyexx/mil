package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

public final class PiglinAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 690;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof AbstractPiglin;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.PIGLIN;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.limited(false);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
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
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .faction("piglin")
         .speciesOrTypeLabel(type)
         .communicationInstructions("Distinctive piglin style: terse, gold-aware, imperfect language. Not a fluent human diplomat.")
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.piglin").getString())
         .systemPromptOverride(
            AdapterContextSupport.spokenSystemPrompt(
               "You are a Minecraft "
                  + type
                  + ". Speak in short broken phrases mixed with growls. Care about gold and barter. Never invent barter outcomes the game did not perform."
            )
         )
         .build();
   }
}
