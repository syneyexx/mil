package org.millenaire.living.requests;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Result of revalidating a player assistance request against live village needs.
 * Carries per-item allowed delivery quantities for the delivery service.
 */
public final class NeedRevalidationResult {
   private final boolean valid;
   private final String reasonKey;
   private final Map<ResourceLocation, Integer> allowedQuantities;
   @Nullable
   private final String matchedNeedOrProjectId;

   private NeedRevalidationResult(
      boolean valid,
      String reasonKey,
      Map<ResourceLocation, Integer> allowedQuantities,
      @Nullable String matchedNeedOrProjectId
   ) {
      this.valid = valid;
      this.reasonKey = reasonKey == null ? "" : reasonKey;
      this.allowedQuantities = Collections.unmodifiableMap(new LinkedHashMap<>(allowedQuantities));
      this.matchedNeedOrProjectId = matchedNeedOrProjectId;
   }

   public static NeedRevalidationResult invalid(String reasonKey) {
      return new NeedRevalidationResult(false, reasonKey, Map.of(), null);
   }

   public static NeedRevalidationResult valid(Map<ResourceLocation, Integer> allowed, @Nullable String needRef) {
      return new NeedRevalidationResult(true, "", allowed, needRef);
   }

   public boolean valid() {
      return this.valid;
   }

   public String reasonKey() {
      return this.reasonKey;
   }

   public Map<ResourceLocation, Integer> allowedQuantities() {
      return this.allowedQuantities;
   }

   @Nullable
   public String matchedNeedOrProjectId() {
      return this.matchedNeedOrProjectId;
   }

   public int allowedFor(ResourceLocation itemId) {
      return itemId == null ? 0 : this.allowedQuantities.getOrDefault(itemId, 0);
   }
}
