package org.millenaire.compat;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Soft-loads optional armor/weapon packs into culture equipment pools for soldiers/guards.
 */
public final class CompatEquipmentRegistry {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<String, Map<EquipmentSlot, List<ResourceLocation>>> BY_CULTURE = new HashMap<>();
   private static boolean loaded;

   private CompatEquipmentRegistry() {
   }

   public static synchronized void ensureLoaded() {
      if (loaded) {
         return;
      }
      loaded = true;
      registerCulture(
         "norman",
         Map.of(
            EquipmentSlot.HEAD,
            ids("magistuarmory:armet", "magistuarmory:kettlehat", "immersive_armors:heavy_helmet", "fantasy_armor:knight_helmet"),
            EquipmentSlot.CHEST,
            ids("magistuarmory:halfarmor", "immersive_armors:heavy_chestplate", "fantasy_armor:knight_chestplate"),
            EquipmentSlot.LEGS,
            ids("magistuarmory:cuisse", "immersive_armors:heavy_leggings", "fantasy_armor:knight_leggings"),
            EquipmentSlot.FEET,
            ids("magistuarmory:sabaton", "immersive_armors:heavy_boots", "fantasy_armor:knight_boots"),
            EquipmentSlot.MAINHAND,
            ids("magistuarmory:noble_sword", "magistuarmory:arming_sword", "simplyswords:iron_longsword")
         )
      );
      registerCulture(
         "byzantines",
         Map.of(
            EquipmentSlot.HEAD,
            ids("magistuarmory:greathelm", "armorofages:byzantine_helmet", "immersive_armors:divine_helmet"),
            EquipmentSlot.CHEST,
            ids("armorofages:byzantine_chestplate", "immersive_armors:divine_chestplate"),
            EquipmentSlot.LEGS,
            ids("armorofages:byzantine_leggings", "immersive_armors:divine_leggings"),
            EquipmentSlot.FEET,
            ids("armorofages:byzantine_boots", "immersive_armors:divine_boots"),
            EquipmentSlot.MAINHAND,
            ids("magistuarmory:estoc", "magistuarmory:claymore")
         )
      );
      registerCulture(
         "seljuk",
         Map.of(
            EquipmentSlot.HEAD,
            ids("exotic_armory:oriental_helmet", "magistuarmory:barbute"),
            EquipmentSlot.CHEST,
            ids("exotic_armory:oriental_chestplate", "immersive_armors:warrior_chestplate"),
            EquipmentSlot.LEGS,
            ids("exotic_armory:oriental_leggings"),
            EquipmentSlot.FEET,
            ids("exotic_armory:oriental_boots"),
            EquipmentSlot.MAINHAND,
            ids("magistuarmory:scimitar", "magistuarmory:falchion")
         )
      );
      registerCulture(
         "japanese",
         Map.of(
            EquipmentSlot.HEAD,
            ids("immersive_armors:warrior_helmet", "fantasy_armor:samurai_helmet"),
            EquipmentSlot.CHEST,
            ids("immersive_armors:warrior_chestplate", "fantasy_armor:samurai_chestplate"),
            EquipmentSlot.LEGS,
            ids("immersive_armors:warrior_leggings", "fantasy_armor:samurai_leggings"),
            EquipmentSlot.FEET,
            ids("immersive_armors:warrior_boots", "fantasy_armor:samurai_boots"),
            EquipmentSlot.MAINHAND,
            ids("simplyswords:iron_katana", "magistuarmory:bastard_sword")
         )
      );
      registerCulture(
         "bandit",
         Map.of(
            EquipmentSlot.HEAD,
            ids("armorpp:leather_helmet", "immersive_armors:bone_helmet"),
            EquipmentSlot.CHEST,
            ids("armorpp:leather_chestplate", "immersive_armors:bone_chestplate"),
            EquipmentSlot.MAINHAND,
            ids("magistuarmory:shortsword", "minecraft:iron_sword")
         )
      );
      int resolved = 0;
      for (Map<EquipmentSlot, List<ResourceLocation>> slots : BY_CULTURE.values()) {
         for (List<ResourceLocation> list : slots.values()) {
            for (ResourceLocation id : list) {
               if (BuiltInRegistries.ITEM.getOptional(id).isPresent()) {
                  resolved++;
               }
            }
         }
      }
      LOGGER.info("[Millenaire] Compat equipment pools ready ({} known items across cultures)", resolved);
   }

   private static void registerCulture(String culture, Map<EquipmentSlot, List<ResourceLocation>> slots) {
      Map<EquipmentSlot, List<ResourceLocation>> filtered = new EnumMap<>(EquipmentSlot.class);
      for (Map.Entry<EquipmentSlot, List<ResourceLocation>> e : slots.entrySet()) {
         List<ResourceLocation> present = new ArrayList<>();
         for (ResourceLocation id : e.getValue()) {
            if (!ModList.get().isLoaded(id.getNamespace()) && !"minecraft".equals(id.getNamespace())) {
               continue;
            }
            present.add(id);
         }
         if (!present.isEmpty()) {
            filtered.put(e.getKey(), List.copyOf(present));
         }
      }
      if (!filtered.isEmpty()) {
         BY_CULTURE.put(culture, filtered);
      }
   }

   private static List<ResourceLocation> ids(String... raw) {
      List<ResourceLocation> out = new ArrayList<>();
      for (String s : raw) {
         ResourceLocation id = ResourceLocation.tryParse(s);
         if (id != null) {
            out.add(id);
         }
      }
      return List.copyOf(out);
   }

   @Nullable
   public static ItemStack pick(String culturePath, EquipmentSlot slot, RandomSource random) {
      ensureLoaded();
      if (culturePath == null) {
         return null;
      }
      String key = culturePath.toLowerCase(Locale.ROOT);
      Map<EquipmentSlot, List<ResourceLocation>> slots = BY_CULTURE.get(key);
      if (slots == null && key.contains("bandit")) {
         slots = BY_CULTURE.get("bandit");
      }
      if (slots == null) {
         return null;
      }
      List<ResourceLocation> options = slots.get(slot);
      if (options == null || options.isEmpty()) {
         return null;
      }
      ResourceLocation chosen = options.get(random.nextInt(options.size()));
      Item item = BuiltInRegistries.ITEM.getOptional(chosen).orElse(null);
      return item == null ? null : new ItemStack(item);
   }

   public static void applyRandomKit(String culturePath, LivingEntity entity, RandomSource random, float chance) {
      ensureLoaded();
      if (random.nextFloat() > chance) {
         return;
      }
      for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND)) {
         if (random.nextFloat() > 0.55F && slot != EquipmentSlot.MAINHAND) {
            continue;
         }
         ItemStack stack = pick(culturePath, slot, random);
         if (stack != null && !stack.isEmpty()) {
            entity.setItemSlot(slot, stack);
         }
      }
   }
}
