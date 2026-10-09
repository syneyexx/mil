package org.millenaire.civilization.population;

import net.minecraft.nbt.CompoundTag;

/**
 * Derived demographic summary. Authoritative identities remain VillagerRecord entries.
 */
public record PopulationSnapshot(
   int residents,
   int adults,
   int children,
   int housingCapacity,
   int workers,
   int militaryEligible,
   long computedAtGameTime
) {
   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("residents", this.residents);
      tag.putInt("adults", this.adults);
      tag.putInt("children", this.children);
      tag.putInt("housing", this.housingCapacity);
      tag.putInt("workers", this.workers);
      tag.putInt("military", this.militaryEligible);
      tag.putLong("at", this.computedAtGameTime);
      return tag;
   }

   public static PopulationSnapshot load(CompoundTag tag) {
      return new PopulationSnapshot(
         tag.getInt("residents"),
         tag.getInt("adults"),
         tag.getInt("children"),
         tag.getInt("housing"),
         tag.getInt("workers"),
         tag.getInt("military"),
         tag.getLong("at")
      );
   }
}
