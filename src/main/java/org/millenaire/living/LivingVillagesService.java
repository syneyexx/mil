package org.millenaire.living;

import org.millenaire.living.needs.VillageNeedsEngine;

/**
 * Facade for Living Villages 2.0 subsystems. One coherent gameplay layer.
 */
public final class LivingVillagesService {
   private static final LivingVillagesService INSTANCE = new LivingVillagesService();

   private final VillageNeedsEngine needsEngine = new VillageNeedsEngine();

   private LivingVillagesService() {
   }

   public static LivingVillagesService get() {
      return INSTANCE;
   }

   public VillageNeedsEngine needsEngine() {
      return this.needsEngine;
   }
}
