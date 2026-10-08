package org.millenaire.faction;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable, serialization-friendly faction identifier.
 * <p>
 * Identity is independent of display names so renaming a faction never invalidates saved data.
 * Prefer {@link FactionIds} helpers when creating well-known Phase 1 identifiers.
 */
public record FactionId(ResourceLocation id) {
   public FactionId {
      Objects.requireNonNull(id, "faction id");
   }

   public static FactionId of(ResourceLocation id) {
      return new FactionId(id);
   }

   public static FactionId of(String namespace, String path) {
      return new FactionId(ResourceLocation.fromNamespaceAndPath(namespace, path));
   }

   /**
    * Parses a stored faction id string. Returns {@code null} when the value is missing or malformed.
    */
   @Nullable
   public static FactionId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new FactionId(ResourceLocation.parse(raw));
      } catch (RuntimeException e) {
         return null;
      }
   }

   public String asString() {
      return this.id.toString();
   }

   @Override
   public String toString() {
      return this.id.toString();
   }
}
