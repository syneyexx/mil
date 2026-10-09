package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

public final class VanillaVillagerAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 800;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof Villager;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.VANILLA_VILLAGER;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.spoken(false, true, false);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
      Villager villager = (Villager)entity;
      if (villager.isSleeping()) {
         return "gui.millenaire.npcchat.asleep";
      }
      if (villager.isAggressive() || villager.getLastHurtByMob() instanceof ServerPlayer) {
         return "gui.millenaire.npcchat.combat";
      }
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
      Villager villager = (Villager)entity;
      VillagerData vd = villager.getVillagerData();
      VillagerProfession profession = vd.getProfession();
      String occupation = profession == VillagerProfession.NONE ? "unemployed villager" : profession.name();
      if (profession == VillagerProfession.NITWIT) {
         occupation = "nitwit";
      }
      String level = "level " + vd.getLevel();
      boolean baby = villager.isBaby();
      return AdapterContextSupport.base(player, entity)
         .occupation(occupation)
         .roleLine(occupation + (baby ? " (child)" : " · " + level))
         .speciesOrTypeLabel("villager")
         .ageCategory(baby ? "child" : "adult")
         .child(baby)
         .settlementName("")
         .currentTask(villager.isTrading() ? "trading" : "village life")
         .communicationInstructions(
            "Human-readable conversation fitting occupation and village life. Do not invent Millénaire culture, chiefs, or foreign villages. Do not invent exact trade prices."
         )
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.villager").getString())
         .systemPromptOverride(
            AdapterContextSupport.spokenSystemPrompt(
               "You are a Minecraft villager. Speak simply. Your job is " + occupation + ". You know only your village life and what this player told you."
            )
         )
         .build();
   }
}
