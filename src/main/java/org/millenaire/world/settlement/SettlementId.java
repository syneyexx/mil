package org.millenaire.world.settlement;

import java.util.Objects;
import java.util.UUID;

public final class SettlementId {
   private final String value;

   private SettlementId(String value) {
      this.value = value;
   }

   public String value() {
      return this.value;
   }

   public static SettlementId of(String value) {
      return new SettlementId(value);
   }

   public static SettlementId millenaire(UUID villageUuid) {
      return new SettlementId("millenaire:" + villageUuid);
   }

   public static SettlementId vanilla(int x, int z, String structureKey) {
      return new SettlementId("vanilla:" + structureKey + ":" + x + ":" + z);
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof SettlementId other && this.value.equals(other.value);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.value);
   }

   @Override
   public String toString() {
      return this.value;
   }
}
