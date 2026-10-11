package org.millenaire.item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

public final class BountyHunterMaterials {
   public static final int ENCHANTABILITY = 14;
   public static final float TOUGHNESS = 2.0F;
   public static final float MATERIAL_KNOCKBACK_RESISTANCE = 0.0F;

   public static final int HELMET_ARMOR = 3;
   public static final int CHEST_ARMOR = 7;
   public static final int LEGGINGS_ARMOR = 5;
   public static final int BOOTS_ARMOR = 3;

   public static final int HELMET_DURABILITY = 275;
   public static final int CHEST_DURABILITY = 400;
   public static final int LEGGINGS_DURABILITY = 380;
   public static final int BOOTS_DURABILITY = 320;

   public static final Holder<ArmorMaterial> BOUNTY_HUNTER_ARMOR = Registry.registerForHolder(
      BuiltInRegistries.ARMOR_MATERIAL,
      ResourceLocation.fromNamespaceAndPath("millenaire", "bounty_hunter"),
      new ArmorMaterial(
         (Map)Util.make(new EnumMap(Type.class), map -> {
            map.put(Type.HELMET, HELMET_ARMOR);
            map.put(Type.CHESTPLATE, CHEST_ARMOR);
            map.put(Type.LEGGINGS, LEGGINGS_ARMOR);
            map.put(Type.BOOTS, BOOTS_ARMOR);
         }),
         ENCHANTABILITY,
         SoundEvents.ARMOR_EQUIP_LEATHER,
         () -> Ingredient.of(new ItemLike[]{Items.IRON_INGOT}),
         List.of(new Layer(ResourceLocation.fromNamespaceAndPath("millenaire", "bounty_hunter"))),
         TOUGHNESS,
         MATERIAL_KNOCKBACK_RESISTANCE
      )
   );

   private BountyHunterMaterials() {
   }

   public static int durabilityFor(Type type) {
      return switch (type) {
         case HELMET -> HELMET_DURABILITY;
         case CHESTPLATE -> CHEST_DURABILITY;
         case LEGGINGS -> LEGGINGS_DURABILITY;
         case BOOTS -> BOOTS_DURABILITY;
         default -> type.getDurability(15);
      };
   }
}
