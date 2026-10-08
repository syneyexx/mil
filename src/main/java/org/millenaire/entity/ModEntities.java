package org.millenaire.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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

   private ModEntities() {
   }

   public static void register(IEventBus modEventBus) {
      ENTITY_TYPES.register(modEventBus);
      modEventBus.addListener(ModEntities::onAttributeCreation);
   }

   private static void onAttributeCreation(EntityAttributeCreationEvent event) {
      event.put((EntityType)MILL_VILLAGER.get(), MillVillager.createAttributes().build());
   }
}
