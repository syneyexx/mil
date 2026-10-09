package org.millenaire.living.actions;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public record NpcActionContext(
   ServerLevel level,
   ServerPlayer player,
   LivingEntity npcEntity,
   @Nullable MillVillager millVillager,
   @Nullable Village village,
   UUID npcId,
   @Nullable UUID villageId,
   String sessionId,
   double distance
) {
}
