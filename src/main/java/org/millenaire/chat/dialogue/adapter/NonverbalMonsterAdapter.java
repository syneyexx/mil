package org.millenaire.chat.dialogue.adapter;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.dialogue.NpcDialogueAdapter;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueIdentity;
import org.millenaire.chat.dialogue.NpcDialoguePolicy;

/**
 * Atmospheric / nonverbal path for remaining hostiles including bosses (non-intrusive).
 */
public final class NonverbalMonsterAdapter implements NpcDialogueAdapter {
   @Override
   public int priority() {
      return 200;
   }

   @Override
   public boolean supports(LivingEntity entity) {
      if (!(entity instanceof Monster) && !(entity instanceof Enemy)) {
         return false;
      }
      // Higher-priority adapters already claimed illagers, piglins, witch, enderman.
      return true;
   }

   @Override
   public NpcDialogueCategory category() {
      return NpcDialogueCategory.NONVERBAL_MONSTER;
   }

   @Override
   public NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity) {
      boolean boss = entity instanceof EnderDragon || entity instanceof WitherBoss;
      return new NpcDialoguePolicy(true, false, false, false, false, boss, org.millenaire.chat.dialogue.NpcCommMode.ATMOSPHERIC, null);
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
      boolean creeper = entity instanceof Creeper;
      boolean boss = entity instanceof EnderDragon || entity instanceof WitherBoss;
      String style = creeper
         ? "You are a creeper. Only hiss, swell, and silence. Never speak words."
         : boss
            ? "You are a Minecraft boss ("
               + type
               + "). Strictly nonintrusive atmospheric reaction. Never freeze, despawn, cancel abilities, or change encounter mechanics."
            : "You are a Minecraft " + type + ". Limited atmospheric growls and motion. No fluent speech.";
      return AdapterContextSupport.base(player, entity)
         .occupation(type)
         .roleLine(type)
         .speciesOrTypeLabel(type)
         .underAttack(entity instanceof Mob mob && mob.getTarget() != null)
         .communicationInstructions("Atmospheric nonverbal reaction only.")
         .fallbackLine(Component.translatable("gui.millenaire.npcchat.fallback.monster").getString())
         .systemPromptOverride(AdapterContextSupport.nonverbalSystemPrompt(style))
         .build();
   }
}
