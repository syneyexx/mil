package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

public final class WanderingTraderAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 790;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof WanderingTrader;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.WANDERING_TRADER;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.spoken(false, true, false);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
      return null;
   }

   @Override
   public NpcDialogueIdentity identity(LivingEntity entity) {
      return NpcDialogueIdentity.from(entity, true);
   }

   @Override
   public NpcDialogueContext buildContext(
      ServerPlayer player, LivingEntity entity, NpcMind mind, NpcMindSavedData data, String speech, String language
   ) {
      return AdapterContextSupport.base(player, entity)
         .occupation("wandering trader")
         .roleLine("wandering trader")
         .speciesOrTypeLabel("wandering trader")
         .currentTask("traveling and trading")
         .communicationInstructions(
            "Travel-oriented personality. Aware only of verified local surroundings and what the player said. Do not invent distant structure coordinates."
         )
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.trader").getString())
         .systemPromptOverride(
            AdapterContextSupport.spokenSystemPrompt(
               "You are a Minecraft wandering trader. You speak of roads, rare goods, and caution. Never invent exact prices or unavailable items."
            )
         )
         .build();
   }
}
