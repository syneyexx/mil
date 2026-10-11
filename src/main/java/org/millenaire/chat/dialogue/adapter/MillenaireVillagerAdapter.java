package org.millenaire.chat.dialogue.adapter;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.NpcPersona;
import org.millenaire.chat.NpcPersonaBuilder;
import org.millenaire.chat.NpcPromptBuilder;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.dialogue.SentenceLoader;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInteraction;
import org.millenaire.living.context.DialogueGameplayContext;
import org.millenaire.living.context.LivingDialogueSupport;
import org.millenaire.network.SpeechChatPayload;
import org.millenaire.village.Village;

public final class MillenaireVillagerAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 1000;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      return entity instanceof MillVillager;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.MILLENAIRE_VILLAGER;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      return NpcDialoguePolicy.spoken(true, true, true);
   }

   @Override
   @Nullable
   public String refusalKey(ServerPlayer player, LivingEntity entity) {
      MillVillager villager = (MillVillager)entity;
      if (villager.isSleeping() || villager.isVillagerSleeping()) {
         return "gui.millenaire.npcchat.asleep";
      }
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      if (villager.getTarget() != null || village != null && village.isUnderAttack()) {
         return "gui.millenaire.npcchat.combat";
      }
      ResourceLocation cultureId = village != null ? village.getCultureId() : null;
      if (cultureId == null && villager.getVillagerTypeId() != null) {
         VillagerType typed = ModCultures.getVillagerType(villager.getVillagerTypeId());
         cultureId = typed != null ? typed.culture() : null;
      }
      if (cultureId != null) {
         var bountyTier = org.millenaire.faction.crime.CultureBountySavedData.get(player.serverLevel())
            .getManager()
            .getTier(player.getUUID(), cultureId);
         if (bountyTier.isAtLeast(org.millenaire.faction.crime.WantedTier.WANTED)) {
            return "gui.millenaire.npcchat.wanted";
         }
      }
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      if (vType != null && vType.hasTag("hostile")) {
         boolean targetingPlayer = villager.getTarget() instanceof Player p && p.getUUID().equals(player.getUUID());
         int rep = village != null ? village.getCombinedReputation(player.serverLevel(), player.getUUID()) : 0;
         if (targetingPlayer || rep < 0) {
            return "gui.millenaire.npcchat.refused";
         }
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
      MillVillager villager = (MillVillager)entity;
      int reputation = 0;
      String repLabel = "Unknown";
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      if (village != null) {
         reputation = village.getCombinedReputation(player.serverLevel(), player.getUUID());
         repLabel = VillagerInteraction.resolveReputationLabelPublic(reputation, village.getCultureId());
      }
      NpcPersona persona = NpcPersonaBuilder.build(player, villager, mind, data, speech, language, reputation, repLabel);
      String roleLine = persona.nativeName().isBlank() ? persona.role() : persona.nativeName();
      if (!persona.villageName().isBlank()) {
         roleLine = roleLine + " — " + persona.villageName();
      }
      DialogueGameplayContext living = LivingDialogueSupport.buildForMillenaire(player.serverLevel(), player, villager, village);
      String instructions = LivingDialogueSupport.appendToInstructions(
         "Natural spoken conversation matching culture, profession, memory and circumstances. Prefer VERIFIED GAME STATE over guesses.",
         living
      );
      return NpcDialogueContext.builder()
         .displayName(persona.name().isBlank() ? villager.getVillagerDisplayName() : persona.name())
         .roleLine(roleLine)
         .reputationLabel(persona.reputationLabel())
         .speciesOrTypeLabel("millenaire villager")
         .occupation(persona.role())
         .cultureId(persona.cultureId())
         .cultureDisplayName(persona.cultureDisplayName())
         .settlementName(persona.villageName())
         .faction(
            village != null && village.getFactionId() != null
               ? village.getFactionId().asString()
               : persona.cultureId()
         )
         .ageCategory(persona.child() ? "child" : "adult")
         .gender(persona.gender())
         .child(persona.child())
         .familyFather(persona.father())
         .familyMother(persona.mother())
         .familyPartner(persona.partner())
         .tags(persona.tags())
         .currentTask(persona.goalLabel())
         .underAttack(persona.underAttack())
         .timeOfDay(persona.timeOfDay())
         .raining(persona.raining())
         .reputation(persona.reputation())
         .languageAside(persona.languageAside())
         .hiredByPlayer(persona.hiredByPlayer())
         .communicationInstructions(instructions)
         .fallbackLine(fallbackLine(villager, language))
         .systemPromptOverride(NpcPromptBuilder.SYSTEM_PROMPT)
         .build();
   }

   @Override
   public void applySpokenLine(ServerPlayer player, LivingEntity entity, String say) {
      MillVillager villager = (MillVillager)entity;
      villager.getSpeech().setSpeechText(say);
      String cultureKey = "";
      int languageScore = 0;
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      if (village != null && village.getCultureId() != null) {
         cultureKey = village.getCultureId().getPath();
         languageScore = org.millenaire.village.PlayerCultureReputation.get(player.serverLevel())
            .getLanguageKnowledge(player.getUUID(), village.getCultureId());
      }
      PacketDistributor.sendToPlayer(
         player, new SpeechChatPayload(villager.getVillagerDisplayName(), say, cultureKey, languageScore, ""), new CustomPacketPayload[0]
      );
   }

   @Override
   public String defaultSystemPrompt() {
      return NpcPromptBuilder.SYSTEM_PROMPT;
   }

   private static String fallbackLine(MillVillager villager, String languageCode) {
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      ResourceLocation culture = vType != null ? vType.culture() : null;
      String role = villager.getRoleName();
      if (role == null || role.isBlank()) {
         role = "villager";
      }
      if (culture != null) {
         List<String> sentences = SentenceLoader.getSentences(culture, languageCode, role.toLowerCase(), "greeting");
         if (sentences.isEmpty()) {
            sentences = SentenceLoader.getSentences(culture, "en_us", role.toLowerCase(), "greeting");
         }
         if (!sentences.isEmpty()) {
            return sentences.get(Math.floorMod((int)villager.getUUID().getLeastSignificantBits(), sentences.size()));
         }
      }
      return net.minecraft.network.chat.Component.translatable("gui.millenaire.npcchat.no_model").getString();
   }
}
