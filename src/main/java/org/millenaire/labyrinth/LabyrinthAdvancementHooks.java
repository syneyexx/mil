package org.millenaire.labyrinth;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;

/**
 * Feedback when the Architect falls; reinforces Core exploration tag.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthAdvancementHooks {
   private LabyrinthAdvancementHooks() {
   }

   @SubscribeEvent
   public static void onArchitectSlain(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof GebondeneEntity gebondene)) {
         return;
      }
      if (gebondene.getKind() != GebondeneKind.ARCHITECT) {
         return;
      }
      if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
         return;
      }
      PlayerQuestData questData = PlayerQuestData.get(player.server.overworld(), QuestRegistry::get);
      questData.setPlayerTag(player.getUUID(), "explored_labyrinth_core");
      questData.setPlayerTag(player.getUUID(), "labyrinth_architect_fallen");
      player.displayClientMessage(Component.translatable("labyrinth.architect.fallen"), true);
   }
}
