package org.millenaire.companion.capability;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.companion.CompanionIntent;
import org.millenaire.entity.MillVillager;

public final class CompanionCapabilityRegistry {
   private static final List<CompanionCapability> MODULES = List.of(
      new TaczCompanionCapability(),
      new WaystonesCompanionCapability(),
      new IronsSpellsCompanionCapability(),
      new AdAstraCompanionCapability(),
      new CreateCompanionCapability()
   );

   private CompanionCapabilityRegistry() {
   }

   public static List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion) {
      List<String> out = new ArrayList<>();
      for (CompanionCapability cap : MODULES) {
         if (!cap.isAvailable()) {
            continue;
         }
         out.addAll(cap.sensorLines(level, player, companion));
      }
      return out;
   }

   public static String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent) {
      for (CompanionCapability cap : MODULES) {
         if (!cap.isAvailable()) {
            continue;
         }
         String result = cap.tryExecute(level, player, companion, intent);
         if (result != null) {
            return result;
         }
      }
      return null;
   }

   public static List<String> availableIds() {
      List<String> ids = new ArrayList<>();
      for (CompanionCapability cap : MODULES) {
         if (cap.isAvailable()) {
            ids.add(cap.id());
         }
      }
      return ids;
   }
}
