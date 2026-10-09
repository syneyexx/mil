package org.millenaire.living.actions;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;

public final class NpcActionValidator {
   private NpcActionValidator() {
   }

   @Nullable
   public static NpcActionContext validateSession(ServerLevel level, ServerPlayer player, int entityId, @Nullable String sessionId) {
      if (level == null || player == null) {
         return null;
      }
      Entity entity = level.getEntity(entityId);
      if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
         return null;
      }
      double range = MillenaireServerConfig.SERVER.effectiveNpcChatLeaveRange();
      double dist = player.distanceTo(living);
      if (dist > range + 0.5) {
         return null;
      }
      MillVillager mill = living instanceof MillVillager mv ? mv : null;
      Village village = null;
      UUID villageUuid = null;
      if (mill != null && mill.getVillageId() != null) {
         village = Village.resolve(level, mill.getVillageId());
         villageUuid = mill.getVillageId().uuid();
      }
      return new NpcActionContext(
         level,
         player,
         living,
         mill,
         village,
         living.getUUID(),
         villageUuid,
         sessionId == null ? "" : sessionId,
         dist
      );
   }

   public static boolean validateProposal(NpcActionContext ctx, NpcActionProposal proposal) {
      if (ctx == null || proposal == null) {
         return false;
      }
      if (!proposal.playerId().equals(ctx.player().getUUID())) {
         return false;
      }
      if (!proposal.npcId().equals(ctx.npcId())) {
         return false;
      }
      if (proposal.isExpired(ctx.level().getGameTime())) {
         return false;
      }
      if (proposal.state() != NpcActionProposal.State.PENDING && proposal.state() != NpcActionProposal.State.ACCEPTED) {
         return false;
      }
      if (proposal.villageId() != null && ctx.villageId() != null && !proposal.villageId().equals(ctx.villageId())) {
         return false;
      }
      if (proposal.actionType().mutatesWorld() && ctx.millVillager() == null) {
         // Millénaire-specific mutations require Millenaire villagers.
         return proposal.actionType() == NpcActionType.OPEN_EXISTING_QUEST_INTERACTION
            || proposal.actionType() == NpcActionType.OPEN_EXISTING_TRADE_INTERACTION;
      }
      return true;
   }

   public static boolean sameVillage(Village village, UUID villageId) {
      return village != null && villageId != null && village.getId().uuid().equals(villageId);
   }

   public static Village requireVillage(ServerLevel level, UUID villageId) {
      return Village.resolve(level, new VillageId(villageId));
   }
}
