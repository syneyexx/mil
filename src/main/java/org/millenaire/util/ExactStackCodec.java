package org.millenaire.util;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Shared exact {@link ItemStack} list codec for civilization / colonization escrow.
 * Uses the supported Minecraft ItemStack codec when a {@link Provider} is available.
 * Decode failures keep raw NBT evidence — stacks are never silently dropped.
 */
public final class ExactStackCodec {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String ITEM_KEY = "Item";
   private static final String LEGACY_ID = "id";
   private static final String LEGACY_COUNT = "Count";
   private static final String UNRESOLVED_FLAG = "unresolved";
   private static final String RAW_KEY = "raw";
   private static final int MAX_WARN_PER_CALL = 3;

   private ExactStackCodec() {
   }

   public record DecodeResult(List<ItemStack> stacks, List<CompoundTag> unresolvedRaw, int decodeFailures, int skippedZeroCount) {
      public boolean hasUnresolved() {
         return !this.unresolvedRaw.isEmpty() || this.decodeFailures > 0;
      }

      public boolean isLegitimatelyEmpty() {
         return this.stacks.isEmpty() && this.unresolvedRaw.isEmpty() && this.decodeFailures == 0;
      }
   }

   public static ListTag encode(@Nullable List<ItemStack> stacks, @Nullable List<CompoundTag> unresolvedRaw, @Nullable Provider registries) {
      ListTag list = new ListTag();
      if (stacks != null) {
         for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
               continue;
            }
            CompoundTag entry = encodeOne(stack, registries);
            if (entry != null) {
               list.add(entry);
            }
         }
      }
      if (unresolvedRaw != null) {
         for (CompoundTag raw : unresolvedRaw) {
            if (raw == null) {
               continue;
            }
            CompoundTag wrapper = new CompoundTag();
            wrapper.putBoolean(UNRESOLVED_FLAG, true);
            wrapper.put(RAW_KEY, raw.copy());
            list.add(wrapper);
         }
      }
      return list;
   }

   public static DecodeResult decode(@Nullable ListTag list, @Nullable Provider registries) {
      List<ItemStack> stacks = new ArrayList<>();
      List<CompoundTag> unresolved = new ArrayList<>();
      int failures = 0;
      int skippedZero = 0;
      int warnings = 0;
      if (list == null || list.isEmpty()) {
         return new DecodeResult(stacks, unresolved, 0, 0);
      }
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         if (entry.getBoolean(UNRESOLVED_FLAG) && entry.contains(RAW_KEY, Tag.TAG_COMPOUND)) {
            unresolved.add(entry.getCompound(RAW_KEY).copy());
            failures++;
            continue;
         }
         ItemStack parsed = ItemStack.EMPTY;
         boolean attemptedCodec = false;
         if (entry.contains(ITEM_KEY) && registries != null) {
            attemptedCodec = true;
            try {
               Optional<ItemStack> opt = ItemStack.parse(registries, entry.get(ITEM_KEY));
               if (opt.isPresent() && !opt.get().isEmpty()) {
                  parsed = opt.get();
               }
            } catch (Exception e) {
               if (warnings++ < MAX_WARN_PER_CALL) {
                  LOGGER.warn("[Millenaire] ExactStackCodec: failed to parse Item entry ({})", e.toString());
               }
            }
         }
         if (parsed.isEmpty() && entry.contains(LEGACY_ID)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(LEGACY_ID));
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            int count = entry.contains(LEGACY_COUNT) ? entry.getInt(LEGACY_COUNT) : 0;
            if (item != null && count <= 0) {
               // Invalid zero/negative legacy count must not invent a stack.
               skippedZero++;
               continue;
            }
            if (item != null && count > 0) {
               parsed = new ItemStack(item, count);
            } else if (id != null) {
               // Unknown item id — keep evidence.
               unresolved.add(entry.copy());
               failures++;
               continue;
            }
         }
         if (!parsed.isEmpty()) {
            stacks.add(parsed);
         } else if (attemptedCodec || entry.contains(ITEM_KEY) || entry.contains(LEGACY_ID)) {
            unresolved.add(entry.copy());
            failures++;
         } else if (!entry.isEmpty()) {
            unresolved.add(entry.copy());
            failures++;
         }
      }
      return new DecodeResult(stacks, unresolved, failures, skippedZero);
   }

   @Nullable
   private static CompoundTag encodeOne(ItemStack stack, @Nullable Provider registries) {
      CompoundTag entry = new CompoundTag();
      if (registries != null) {
         try {
            Tag encoded = stack.save(registries);
            if (encoded instanceof CompoundTag compound) {
               entry.put(ITEM_KEY, compound);
            } else {
               entry.put(ITEM_KEY, encoded);
            }
            return entry;
         } catch (Exception e) {
            LOGGER.warn("[Millenaire] ExactStackCodec: encode via Provider failed ({}); falling back to id/Count", e.toString());
         }
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (id == null) {
         return null;
      }
      entry.putString(LEGACY_ID, id.toString());
      entry.putInt(LEGACY_COUNT, stack.getCount());
      return entry;
   }
}
