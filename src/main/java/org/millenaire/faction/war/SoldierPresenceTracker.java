package org.millenaire.faction.war;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.UnitId;

/**
 * Tracks optional physical soldier entity UUIDs representing a logical army presence.
 * Logical unit counts remain authoritative; entities are presence only.
 */
public final class SoldierPresenceTracker {
   private final Map<UUID, Binding> bindings = new LinkedHashMap<>();

   public void bind(UUID entityUuid, ArmyId armyId, UnitId unitId) {
      Objects.requireNonNull(entityUuid, "entityUuid");
      Objects.requireNonNull(armyId, "armyId");
      Objects.requireNonNull(unitId, "unitId");
      this.bindings.put(entityUuid, new Binding(armyId, unitId));
   }

   public void unbind(UUID entityUuid) {
      if (entityUuid != null) {
         this.bindings.remove(entityUuid);
      }
   }

   @Nullable
   public Binding get(UUID entityUuid) {
      return entityUuid == null ? null : this.bindings.get(entityUuid);
   }

   public void clearArmy(ArmyId armyId) {
      if (armyId == null) {
         return;
      }

      this.bindings.entrySet().removeIf(entry -> armyId.equals(entry.getValue().armyId()));
   }

   public void clearUnit(UnitId unitId) {
      if (unitId == null) {
         return;
      }

      this.bindings.entrySet().removeIf(entry -> unitId.equals(entry.getValue().unitId()));
   }

   public int countForArmy(ArmyId armyId) {
      if (armyId == null) {
         return 0;
      }

      int count = 0;
      for (Binding binding : this.bindings.values()) {
         if (armyId.equals(binding.armyId())) {
            count++;
         }
      }

      return count;
   }

   public Map<UUID, Binding> snapshot() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(this.bindings));
   }

   public int desiredRepresentatives(int logicalSoldiers) {
      if (logicalSoldiers <= 0) {
         return 0;
      }

      return Math.min(WarConfig.MAX_PHYSICAL_SOLDIER_REPRESENTATIVES, Math.max(1, logicalSoldiers / 5));
   }

   public record Binding(ArmyId armyId, UnitId unitId) {
   }
}
