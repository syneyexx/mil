package org.millenaire.village;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record CultureDiplomacyLogEntry(
   long day,
   String action,
   ResourceLocation cultureA,
   ResourceLocation cultureB,
   int standingAfter,
   @Nullable String detail
) {
   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putLong("day", this.day);
      tag.putString("action", this.action);
      tag.putString("a", this.cultureA.toString());
      tag.putString("b", this.cultureB.toString());
      tag.putInt("standing", this.standingAfter);
      if (this.detail != null) {
         tag.putString("detail", this.detail);
      }
      return tag;
   }

   public static CultureDiplomacyLogEntry load(CompoundTag tag) {
      return new CultureDiplomacyLogEntry(
         tag.getLong("day"),
         tag.getString("action"),
         ResourceLocation.parse(tag.getString("a")),
         ResourceLocation.parse(tag.getString("b")),
         tag.getInt("standing"),
         tag.contains("detail") ? tag.getString("detail") : null
      );
   }
}
