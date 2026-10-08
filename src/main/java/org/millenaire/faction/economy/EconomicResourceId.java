package org.millenaire.faction.economy;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Stable economic resource identifier backed by a Minecraft item {@link ResourceLocation}.
 * Does not invent abstract goods outside the game registry.
 */
public record EconomicResourceId(ResourceLocation id) {
   public EconomicResourceId {
      Objects.requireNonNull(id, "id");
   }

   public static EconomicResourceId of(ResourceLocation id) {
      return new EconomicResourceId(id);
   }

   public static EconomicResourceId of(Item item) {
      Objects.requireNonNull(item, "item");
      ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
      return new EconomicResourceId(key);
   }

   @Nullable
   public static EconomicResourceId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new EconomicResourceId(ResourceLocation.parse(raw));
      } catch (RuntimeException e) {
         return null;
      }
   }

   @Nullable
   public Item resolveItem() {
      return BuiltInRegistries.ITEM.getOptional(this.id).orElse(null);
   }

   public String asString() {
      return this.id.toString();
   }

   @Override
   public String toString() {
      return this.id.toString();
   }
}
