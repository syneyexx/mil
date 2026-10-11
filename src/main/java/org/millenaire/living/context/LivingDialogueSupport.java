package org.millenaire.living.context;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.living.actions.NpcActionContext;
import org.millenaire.living.actions.NpcActionRegistry;
import org.millenaire.living.actions.NpcActionType;
import org.millenaire.living.actions.NpcActionValidator;
import org.millenaire.living.net.LivingActionChoicesPayload;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestSavedData;
import org.millenaire.living.requests.VillageRequestState;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class LivingDialogueSupport {
   private LivingDialogueSupport() {
   }

   public static DialogueGameplayContext buildForMillenaire(
      ServerLevel level, ServerPlayer player, MillVillager villager, @Nullable Village village
   ) {
      DialogueGameplayContext ctx = new DialogueGameplayContext();
      if (!enabled()) {
         return ctx;
      }
      VillagerRecord record = null;
      if (village != null) {
         record = village.getVillagerRecords().get(villager.getUUID());
      }
      if (village != null && record != null) {
         NpcWorldKnowledgeProvider.appendRoleKnowledge(level, village, record, ctx);
         NpcSocialContextProvider.appendSocial(level, record, player, ctx);
         try {
            if (MillenaireServerConfig.SERVER.religionEnabled.get()) {
               for (String line : org.millenaire.religion.ReligionService.get().presentation().dialogueContextLines(level, village, record)) {
                  ctx.addVerified("religion: " + line);
               }
            }
         } catch (IllegalStateException ignored) {
         }
      } else if (village != null) {
         VillageContextProvider.appendVerifiedVillageState(level, village, ctx);
      }
      return ctx;
   }

   public static String appendToInstructions(String base, DialogueGameplayContext living) {
      if (living == null || living.isEmpty()) {
         return base;
      }
      StringBuilder sb = new StringBuilder(base == null ? "" : base);
      sb.append('\n');
      for (String line : living.toPromptLines()) {
         sb.append(line).append('\n');
      }
      return sb.toString().trim();
   }

   public static void sendActionChoices(ServerPlayer player, int entityId) {
      if (!enabled() || !suggestionsEnabled()) {
         return;
      }
      NpcActionContext ctx = NpcActionValidator.validateSession(player.serverLevel(), player, entityId, null);
      if (ctx == null || ctx.millVillager() == null) {
         return;
      }
      List<String> actions = new ArrayList<>();
      for (NpcActionType type : NpcActionRegistry.availableActions(ctx)) {
         actions.add(type.name());
      }
      String activeRequest = "";
      for (VillageRequest r : VillageRequestSavedData.get(player.serverLevel()).forPlayer(player.getUUID())) {
         if (VillageRequestState.activeStates().contains(r.state())
            && (ctx.villageId() == null || r.villageId().equals(ctx.villageId()))) {
            activeRequest = r.requestId();
            break;
         }
      }
      PacketDistributor.sendToPlayer(player, new LivingActionChoicesPayload(entityId, actions, activeRequest), new CustomPacketPayload[0]);
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static boolean suggestionsEnabled() {
      try {
         return MillenaireServerConfig.SERVER.npcDialogueActionSuggestionsEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
