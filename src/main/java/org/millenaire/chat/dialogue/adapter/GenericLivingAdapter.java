package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.millenaire.chat.NpcMind;
import org.millenaire.entity.MillVillager;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Catch-all for registered living entities (including future/modded types) without a dedicated adapter.
 * Does not claim automatic deep integration with third-party scripted NPC systems.
 */
public final class GenericLivingAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 1;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      if (entity instanceof Player || entity instanceof MillVillager) {
         return false;
      }
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatGenericLiving.get()
         && !(Boolean)MillenaireServerConfig.SERVER.npcChatModded.get()) {
         return false;
      }
      ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      if (id == null) {
         return false;
      }
      // Allow any living entity that reached this fallback; deny-list via config strings.
      String deny = MillenaireServerConfig.SERVER.npcChatEntityDenyList.get();
      if (deny != null && !deny.isBlank()) {
         String full = id.toString();
         for (String part : deny.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && (full.equals(trimmed) || id.getPath().equals(trimmed))) {
               return false;
            }
         }
      }
      String allow = MillenaireServerConfig.SERVER.npcChatEntityAllowList.get();
      if (allow != null && !allow.isBlank()) {
         String full = id.toString();
         for (String part : allow.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && (full.equals(trimmed) || id.getPath().equals(trimmed))) {
               return true;
            }
         }
         return false;
      }
      return true;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.GENERIC_LIVING;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      boolean looksIntelligent = entity instanceof Mob;
      if (looksIntelligent) {
         return NpcDialoguePolicy.limited(false);
      }
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
      ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      String type = AdapterContextSupport.entityTypeLabel(entity);
      boolean modded = id != null && !"minecraft".equals(id.getNamespace());
      String instructions = modded
         ? "Generic support for a modded living entity. Stay in character as this creature. Do not invent owning-mod systems you were not given."
         : "Generic living entity. Keep communication modest and contextual.";
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .speciesOrTypeLabel(type)
         .faction(id != null ? id.getNamespace() : "")
         .communicationInstructions(instructions)
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.generic").getString())
         .systemPromptOverride(
            AdapterContextSupport.spokenSystemPrompt(
               "You are a Minecraft living entity of type "
                  + type
                  + (modded ? " from mod namespace " + id.getNamespace() : "")
                  + ". Communicate in a style fitting a non-player creature. Prefer short lines. If speech feels wrong, use brief behavioural description instead."
            )
         )
         .build();
   }
}
