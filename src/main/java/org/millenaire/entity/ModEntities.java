package org.millenaire.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.millenaire.monster.DeAlziendeEntity;
import org.millenaire.monster.DrakenkoningEntity;
import org.millenaire.monster.DrievoudigeVorstEntity;
import org.millenaire.monster.GevallenSerafEntity;
import org.millenaire.monster.MonsterConstants;
import org.millenaire.monster.ScharlakenKoninginEntity;
import org.millenaire.monster.ZesarmigeRazernijEntity;
import org.millenaire.monster.pack.MobKind;
import org.millenaire.monster.pack.PackMonster;
import org.millenaire.labyrinth.entity.GebondeneEntity;

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

   public static final DeferredHolder<EntityType<?>, EntityType<DrakenkoningEntity>> DRAKENKONING = ENTITY_TYPES.register(
      "drakenkoning",
      () -> Builder.of(DrakenkoningEntity::new, MobCategory.MONSTER)
         .sized(MonsterConstants.Drakenkoning.WIDTH, MonsterConstants.Drakenkoning.HEIGHT)
         .clientTrackingRange(10)
         .fireImmune()
         .build("millenaire:drakenkoning")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<DeAlziendeEntity>> DE_ALZIENDE = ENTITY_TYPES.register(
      "de_alziende",
      () -> Builder.of(DeAlziendeEntity::new, MobCategory.MONSTER)
         .sized(MonsterConstants.DeAlziende.WIDTH, MonsterConstants.DeAlziende.HEIGHT)
         .clientTrackingRange(10)
         .build("millenaire:de_alziende")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<DrievoudigeVorstEntity>> DRIEVOUDIGE_VORST = ENTITY_TYPES.register(
      "drievoudige_vorst",
      () -> Builder.of(DrievoudigeVorstEntity::new, MobCategory.MONSTER)
         .sized(MonsterConstants.DrievoudigeVorst.WIDTH, MonsterConstants.DrievoudigeVorst.HEIGHT)
         .clientTrackingRange(10)
         .build("millenaire:drievoudige_vorst")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<GevallenSerafEntity>> GEVALLEN_SERAF = ENTITY_TYPES.register(
      "gevallen_seraf",
      () -> Builder.of(GevallenSerafEntity::new, MobCategory.MONSTER)
         .sized(MonsterConstants.GevallenSeraf.WIDTH, MonsterConstants.GevallenSeraf.HEIGHT)
         .clientTrackingRange(10)
         .build("millenaire:gevallen_seraf")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<ScharlakenKoninginEntity>> SCHARLAKEN_KONINGIN = ENTITY_TYPES.register(
      "scharlaken_koningin",
      () -> Builder.of(ScharlakenKoninginEntity::new, MobCategory.MONSTER)
         .sized(MonsterConstants.ScharlakenKoningin.WIDTH, MonsterConstants.ScharlakenKoningin.HEIGHT)
         .clientTrackingRange(10)
         .build("millenaire:scharlaken_koningin")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<ZesarmigeRazernijEntity>> ZESARMIGE_RAZERNIJ = ENTITY_TYPES.register(
      "zesarmige_razernij",
      () -> Builder.of(ZesarmigeRazernijEntity::new, MobCategory.MONSTER)
         .sized(MonsterConstants.ZesarmigeRazernij.WIDTH, MonsterConstants.ZesarmigeRazernij.HEIGHT)
         .clientTrackingRange(10)
         .build("millenaire:zesarmige_razernij")
   );

   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> HORSE_WIZARD = ENTITY_TYPES.register(
      "horse_wizard",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.HORSE_WIZARD), MobCategory.MONSTER)
         .sized(MobKind.HORSE_WIZARD.width, MobKind.HORSE_WIZARD.height)
         .clientTrackingRange(8)
         .build("millenaire:horse_wizard")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> PEGASUS = ENTITY_TYPES.register(
      "pegasus",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.PEGASUS), MobCategory.MONSTER)
         .sized(MobKind.PEGASUS.width, MobKind.PEGASUS.height)
         .clientTrackingRange(8)
         .build("millenaire:pegasus")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> ZUZU = ENTITY_TYPES.register(
      "zuzu",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.ZUZU), MobCategory.MONSTER)
         .sized(MobKind.ZUZU.width, MobKind.ZUZU.height)
         .clientTrackingRange(8)
         .build("millenaire:zuzu")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> BIRDMAN = ENTITY_TYPES.register(
      "birdman",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.BIRDMAN), MobCategory.MONSTER)
         .sized(MobKind.BIRDMAN.width, MobKind.BIRDMAN.height)
         .clientTrackingRange(8)
         .build("millenaire:birdman")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> CTHULU = ENTITY_TYPES.register(
      "cthulu",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.CTHULU), MobCategory.MONSTER)
         .sized(MobKind.CTHULU.width, MobKind.CTHULU.height)
         .clientTrackingRange(8)
         .build("millenaire:cthulu")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> JUNRA = ENTITY_TYPES.register(
      "junra",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.JUNRA), MobCategory.MONSTER)
         .sized(MobKind.JUNRA.width, MobKind.JUNRA.height)
         .clientTrackingRange(8)
         .build("millenaire:junra")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> NEBULA = ENTITY_TYPES.register(
      "nebula",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.NEBULA), MobCategory.MONSTER)
         .sized(MobKind.NEBULA.width, MobKind.NEBULA.height)
         .clientTrackingRange(8)
         .build("millenaire:nebula")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> SDA = ENTITY_TYPES.register(
      "sda",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.SDA), MobCategory.MONSTER)
         .sized(MobKind.SDA.width, MobKind.SDA.height)
         .clientTrackingRange(8)
         .build("millenaire:sda")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> ZAVA = ENTITY_TYPES.register(
      "zava",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.ZAVA), MobCategory.MONSTER)
         .sized(MobKind.ZAVA.width, MobKind.ZAVA.height)
         .clientTrackingRange(8)
         .build("millenaire:zava")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PackMonster>> DISCD = ENTITY_TYPES.register(
      "discd",
      () -> Builder.<PackMonster>of((type, level) -> new PackMonster(type, level, MobKind.DISCD), MobCategory.MONSTER)
         .sized(MobKind.DISCD.width, MobKind.DISCD.height)
         .clientTrackingRange(8)
         .build("millenaire:discd")
   );

   public static final DeferredHolder<EntityType<?>, EntityType<GebondeneEntity>> GEBONDENE = ENTITY_TYPES.register(
      "gebondene",
      () -> Builder.of(GebondeneEntity::new, MobCategory.CREATURE)
         .sized(0.6F, 1.95F)
         .clientTrackingRange(10)
         .fireImmune()
         .build("millenaire:gebondene")
   );

   private ModEntities() {
   }

   public static void register(IEventBus modEventBus) {
      ENTITY_TYPES.register(modEventBus);
      modEventBus.addListener(ModEntities::onAttributeCreation);
      modEventBus.addListener(ModEntities::onSpawnPlacements);
   }

   private static void onAttributeCreation(EntityAttributeCreationEvent event) {
      event.put(MILL_VILLAGER.get(), MillVillager.createAttributes().build());
      event.put(DRAKENKONING.get(), DrakenkoningEntity.createAttributes().build());
      event.put(DE_ALZIENDE.get(), DeAlziendeEntity.createAttributes().build());
      event.put(DRIEVOUDIGE_VORST.get(), DrievoudigeVorstEntity.createAttributes().build());
      event.put(GEVALLEN_SERAF.get(), GevallenSerafEntity.createAttributes().build());
      event.put(SCHARLAKEN_KONINGIN.get(), ScharlakenKoninginEntity.createAttributes().build());
      event.put(ZESARMIGE_RAZERNIJ.get(), ZesarmigeRazernijEntity.createAttributes().build());
      event.put(HORSE_WIZARD.get(), PackMonster.attributes(MobKind.HORSE_WIZARD).build());
      event.put(PEGASUS.get(), PackMonster.attributes(MobKind.PEGASUS).build());
      event.put(ZUZU.get(), PackMonster.attributes(MobKind.ZUZU).build());
      event.put(BIRDMAN.get(), PackMonster.attributes(MobKind.BIRDMAN).build());
      event.put(CTHULU.get(), PackMonster.attributes(MobKind.CTHULU).build());
      event.put(JUNRA.get(), PackMonster.attributes(MobKind.JUNRA).build());
      event.put(NEBULA.get(), PackMonster.attributes(MobKind.NEBULA).build());
      event.put(SDA.get(), PackMonster.attributes(MobKind.SDA).build());
      event.put(ZAVA.get(), PackMonster.attributes(MobKind.ZAVA).build());
      event.put(DISCD.get(), PackMonster.attributes(MobKind.DISCD).build());
      event.put(GEBONDENE.get(), GebondeneEntity.createAttributes().build());
   }

   private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
      event.register(DRAKENKONING.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(DE_ALZIENDE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(DRIEVOUDIGE_VORST.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(GEVALLEN_SERAF.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(SCHARLAKEN_KONINGIN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(ZESARMIGE_RAZERNIJ.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(HORSE_WIZARD.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(PEGASUS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(ZUZU.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(BIRDMAN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(CTHULU.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(JUNRA.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(NEBULA.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(SDA.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(ZAVA.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
      event.register(DISCD.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.OR);
   }
}
