package org.millenaire.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
   private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "millenaire");
   public static final DeferredHolder<EntityType<?>, EntityType<MillVillager>> MILL_VILLAGER = ENTITY_TYPES.register(
      "villager", () -> Builder.of(MillVillager::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10).build("millenaire:villager")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<MillWallDecoration>> WALL_DECORATION = ENTITY_TYPES.register(
      "wall_decoration",
      () -> Builder.<MillWallDecoration>of(MillWallDecoration::new, MobCategory.MISC)
         .sized(0.5F, 0.5F)
         .clientTrackingRange(10)
         .updateInterval(Integer.MAX_VALUE)
         .build("millenaire:wall_decoration")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<NetherWraithEntity>> NETHER_WRAITH = ENTITY_TYPES.register(
      "nether_wraith",
      () -> Builder.of(NetherWraithEntity::new, MobCategory.MONSTER)
         .sized(1.15F, 3.2F)
         .clientTrackingRange(8)
         .fireImmune()
         .build("millenaire:nether_wraith")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<InfernalHarrowEntity>> INFERNAL_HARROW = ENTITY_TYPES.register(
      "infernal_harrow",
      () -> Builder.of(InfernalHarrowEntity::new, MobCategory.MONSTER)
         .sized(1.0F, 2.35F)
         .clientTrackingRange(10)
         .fireImmune()
         .build("millenaire:infernal_harrow")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<InfernalHellhoundEntity>> INFERNAL_HELLHOUND = ENTITY_TYPES.register(
      "infernal_hellhound",
      () -> Builder.of(InfernalHellhoundEntity::new, MobCategory.MONSTER)
         .sized(1.15F, 1.5F)
         .clientTrackingRange(10)
         .fireImmune()
         .build("millenaire:infernal_hellhound")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<InfernalFireballEntity>> INFERNAL_FIREBALL = ENTITY_TYPES.register(
      "infernal_fireball",
      () -> Builder.<InfernalFireballEntity>of(InfernalFireballEntity::new, MobCategory.MISC)
         .sized(0.4F, 0.4F)
         .clientTrackingRange(4)
         .updateInterval(10)
         .fireImmune()
         .build("millenaire:infernal_fireball")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<AntichristPriestEntity>> ANTICHRIST_PRIEST = ENTITY_TYPES.register(
      "antichrist_priest",
      () -> Builder.of(AntichristPriestEntity::new, MobCategory.MONSTER)
         .sized(0.9F, 2.65F)
         .clientTrackingRange(10)
         .fireImmune()
         .build("millenaire:antichrist_priest")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<InfernalCultistEntity>> INFERNAL_CULTIST = ENTITY_TYPES.register(
      "infernal_cultist",
      () -> Builder.of(InfernalCultistEntity::new, MobCategory.MONSTER)
         .sized(0.65F, 1.95F)
         .clientTrackingRange(10)
         .fireImmune()
         .build("millenaire:infernal_cultist")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<InfernalHexBoltEntity>> INFERNAL_HEX_BOLT = ENTITY_TYPES.register(
      "infernal_hex_bolt",
      () -> Builder.<InfernalHexBoltEntity>of(InfernalHexBoltEntity::new, MobCategory.MISC)
         .sized(0.35F, 0.35F)
         .clientTrackingRange(4)
         .updateInterval(10)
         .fireImmune()
         .build("millenaire:infernal_hex_bolt")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<BountyHunterEntity>> BOUNTY_HUNTER = ENTITY_TYPES.register(
      "bounty_hunter",
      () -> Builder.of(BountyHunterEntity::new, MobCategory.CREATURE)
         .sized(0.7F, 2.15F)
         .clientTrackingRange(10)
         .build("millenaire:bounty_hunter")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<BountyHunterBolaEntity>> BOUNTY_HUNTER_BOLA = ENTITY_TYPES.register(
      "bounty_hunter_bola",
      () -> Builder.<BountyHunterBolaEntity>of(BountyHunterBolaEntity::new, MobCategory.MISC)
         .sized(0.35F, 0.35F)
         .clientTrackingRange(4)
         .updateInterval(10)
         .build("millenaire:bounty_hunter_bola")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<HemelwachterEntity>> HEMELWACHTER = ENTITY_TYPES.register(
      "hemelwachter",
      () -> Builder.of(HemelwachterEntity::new, MobCategory.MONSTER)
         .sized(3.2F, 4.5F)
         .clientTrackingRange(12)
         .updateInterval(2)
         .fireImmune()
         .build("millenaire:hemelwachter")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<CelestialBoltEntity>> CELESTIAL_BOLT = ENTITY_TYPES.register(
      "celestial_bolt",
      () -> Builder.<CelestialBoltEntity>of(CelestialBoltEntity::new, MobCategory.MISC)
         .sized(0.45F, 0.45F)
         .clientTrackingRange(6)
         .updateInterval(8)
         .fireImmune()
         .build("millenaire:celestial_bolt")
   );

   private ModEntities() {
   }

   public static void register(IEventBus modEventBus) {
      ENTITY_TYPES.register(modEventBus);
      modEventBus.addListener(ModEntities::onAttributeCreation);
      modEventBus.addListener(ModEntities::onRegisterSpawnPlacements);
   }

   private static void onAttributeCreation(EntityAttributeCreationEvent event) {
      event.put((EntityType)MILL_VILLAGER.get(), MillVillager.createAttributes().build());
      event.put((EntityType)NETHER_WRAITH.get(), NetherWraithEntity.createAttributes().build());
      event.put((EntityType)INFERNAL_HARROW.get(), InfernalHarrowEntity.createAttributes().build());
      event.put((EntityType)INFERNAL_HELLHOUND.get(), InfernalHellhoundEntity.createAttributes().build());
      event.put((EntityType)ANTICHRIST_PRIEST.get(), AntichristPriestEntity.createAttributes().build());
      event.put((EntityType)INFERNAL_CULTIST.get(), InfernalCultistEntity.createAttributes().build());
      event.put((EntityType)BOUNTY_HUNTER.get(), BountyHunterEntity.createAttributes().build());
      event.put((EntityType)HEMELWACHTER.get(), HemelwachterEntity.createAttributes().build());
   }

   private static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
      event.register(
         NETHER_WRAITH.get(),
         SpawnPlacementTypes.ON_GROUND,
         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
         NetherWraithEntity::checkNetherWraithSpawnRules,
         RegisterSpawnPlacementsEvent.Operation.OR
      );
      event.register(
         INFERNAL_HARROW.get(),
         SpawnPlacementTypes.ON_GROUND,
         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
         InfernalHarrowEntity::checkInfernalHarrowSpawnRules,
         RegisterSpawnPlacementsEvent.Operation.OR
      );
      event.register(
         INFERNAL_HELLHOUND.get(),
         SpawnPlacementTypes.ON_GROUND,
         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
         InfernalHellhoundEntity::checkInfernalHellhoundSpawnRules,
         RegisterSpawnPlacementsEvent.Operation.OR
      );
      event.register(
         ANTICHRIST_PRIEST.get(),
         SpawnPlacementTypes.ON_GROUND,
         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
         AntichristPriestEntity::checkAntichristPriestSpawnRules,
         RegisterSpawnPlacementsEvent.Operation.OR
      );
      event.register(
         INFERNAL_CULTIST.get(),
         SpawnPlacementTypes.ON_GROUND,
         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
         InfernalCultistEntity::checkInfernalCultistSpawnRules,
         RegisterSpawnPlacementsEvent.Operation.OR
      );
   }
}
