package org.millenaire.worldboss;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.entity.ModEntities;
import org.millenaire.worldboss.spawn.AerialNearPlayerSpawnStrategy;

public final class WorldBossDefinitions {
   public static final ResourceLocation HEMELWACHTER_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "hemelwachter");

   private WorldBossDefinitions() {
   }

   public static void bootstrap() {
      WorldBossDefinitionRegistry registry = WorldBossDefinitionRegistry.get();
      if (registry.isFrozen()) {
         return;
      }
      registry.register(
         WorldBossDefinition.builder(HEMELWACHTER_ID)
            .entityType(() -> ModEntities.HEMELWACHTER.get())
            .weight(100)
            .availability(level -> WorldBossEligibility.eventsEnabled() && WorldBossEligibility.isAllowedDimension(level.dimension()))
            .spawnStrategy(new AerialNearPlayerSpawnStrategy(12.0, 14.0))
            .bossbarNameKey("entity.millenaire.hemelwachter")
            .bossBar(BossBarColor.YELLOW, BossBarOverlay.NOTCHED_10)
            .neutralDwellTicks(6000)
            .combatTimeoutTicks(12000)
            .announceTicks(80)
            .arriveTicks(100)
            .lootTable(ResourceLocation.fromNamespaceAndPath("millenaire", "entities/hemelwachter"))
            .advancementId(MillAdvancements.HEMELWACHTER_DEFEAT)
            .messages(
               "millenaire.worldboss.hemelwachter.announce",
               "millenaire.worldboss.hemelwachter.arrive",
               "millenaire.worldboss.hemelwachter.depart",
               "millenaire.worldboss.hemelwachter.engage",
               "millenaire.worldboss.hemelwachter.defeat"
            )
            .build()
      );
      registry.freeze();
   }
}
