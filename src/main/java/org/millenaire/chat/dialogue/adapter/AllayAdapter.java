package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

public final class AllayAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 640;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof Allay;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.ALLAY;
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
      return NpcDialogueIdentity.from(entity, false);
   }

   @Override
   public NpcDialogueContext buildContext(
      ServerPlayer player, LivingEntity entity, NpcMind mind, NpcMindSavedData data, String speech, String language
   ) {
      return AdapterContextSupport.base(player, entity)
         .occupation("allay")
         .roleLine("allay")
         .speciesOrTypeLabel("allay")
         .communicationInstructions("Expressive, playful, primarily nonverbal chimes and motion.")
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.allay").getString())
         .systemPromptOverride(
            AdapterContextSupport.nonverbalSystemPrompt(
               "You are a Minecraft allay. Respond with soft chimes, playful loops, and item-seeking gestures. No human politics."
            )
         )
         .build();
   }
}
