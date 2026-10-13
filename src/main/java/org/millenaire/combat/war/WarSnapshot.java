package org.millenaire.combat.war;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable compact copy for background war planning. No live world objects. */
public record WarSnapshot(
   UUID campaignId,
   ResourceLocation sideA,
   ResourceLocation sideB,
   WarCampaign.Phase phase,
   @Nullable UUID frontVillageId,
   @Nullable UUID attackerVillageId,
   @Nullable UUID defenderVillageId,
   int waveIndex,
   int wavesTotal,
   int warScoreA,
   int warScoreB,
   long nextWaveGameTime,
   long gameTime,
   long worldSessionId,
   int attackerStrength,
   int defenderStrength,
   List<UUID> candidateFrontVillages,
   List<UUID> candidateSabotageBuildings,
   int waveIntervalTicks,
   boolean warEnabled,
   boolean buildingDamageEnabled
) {
   public WarSnapshot {
      candidateFrontVillages = candidateFrontVillages == null ? List.of() : List.copyOf(candidateFrontVillages);
      candidateSabotageBuildings = candidateSabotageBuildings == null ? List.of() : List.copyOf(candidateSabotageBuildings);
   }
}
