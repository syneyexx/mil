package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

public final class GolemAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 650;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof IronGolem || entity instanceof SnowGolem;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.GOLEM;
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
      String type = entity instanceof IronGolem ? "iron golem" : "snow golem";
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .speciesOrTypeLabel(type)
         .communicationInstructions("Short, restrained, mostly nonverbal. Heavy presence. Never chatty.")
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.golem").getString())
         .systemPromptOverride(
            AdapterContextSupport.nonverbalSystemPrompt(
               "You are a Minecraft " + type + ". Express with rumble, stance, and brief gesture descriptions. No fluent speech."
            )
         )
         .build();
   }
}
