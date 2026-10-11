package org.millenaire.religion.content;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

/**
 * Validates loaded religion content. Invalid references are logged; broken traditions
 * are unbound from cultures so other content remains usable.
 */
public final class ReligionContentValidator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ReligionContentValidator() {
   }

   public static void validateAll() {
      Set<ResourceLocation> brokenTraditions = new HashSet<>();
      for (ReligionDefinition def : ReligionContentRegistry.allTraditions()) {
         for (ResourceLocation ritualId : def.allRitualIds()) {
            if (ReligionContentRegistry.ritualOrNull(ritualId) == null) {
               LOGGER.error(
                  "[Religion] Tradition {} references missing ritual {} — tradition marked broken",
                  def.id(),
                  ritualId
               );
               brokenTraditions.add(def.id());
            }
         }
      }
      for (RitualDefinition ritual : ReligionContentRegistry.allRituals()) {
         if (ReligionContentRegistry.traditionOrNull(ritual.traditionId()) == null) {
            LOGGER.error("[Religion] Ritual {} references missing tradition {}", ritual.id(), ritual.traditionId());
         }
         for (SupplyRequirement supply : ritual.supplies()) {
            if (supply.itemId() == null || supply.itemId().isEmpty()) {
               continue;
            }
            if (ItemHelper.resolve(supply.itemId()) == null && !"anywood".equalsIgnoreCase(supply.itemId())) {
               LOGGER.warn(
                  "[Religion] Ritual {} supply item '{}' unresolved at load (may resolve later)",
                  ritual.id(),
                  supply.itemId()
               );
            }
         }
         if (ritual.locationTags().isEmpty() && ritual.kind() != org.millenaire.religion.model.RitualKind.FAMILY) {
            LOGGER.warn("[Religion] Ritual {} has no location_tags", ritual.id());
         }
      }
      for (String culture : new HashSet<>(java.util.List.of(
         "norman", "byzantines", "indian", "japanese", "seljuk", "mayan", "inuits", "gabbers", "outlawz", "illagers"
      ))) {
         for (ResourceLocation tid : ReligionContentRegistry.traditionsForCulture(culture)) {
            if (brokenTraditions.contains(tid) || ReligionContentRegistry.traditionOrNull(tid) == null) {
               LOGGER.error(
                  "[Religion] Culture {} bound to missing/broken tradition {} — binding ignored for that id",
                  culture,
                  tid
               );
            }
         }
      }
   }
}
