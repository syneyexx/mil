package org.millenaire.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.world.item.ArmorItem.Type;
import org.junit.jupiter.api.Test;

class BountyHunterArmorTest {
   @Test
   void defenseTotalsEighteen() {
      assertEquals(3, BountyHunterMaterials.HELMET_ARMOR);
      assertEquals(7, BountyHunterMaterials.CHEST_ARMOR);
      assertEquals(5, BountyHunterMaterials.LEGGINGS_ARMOR);
      assertEquals(3, BountyHunterMaterials.BOOTS_ARMOR);
      assertEquals(
         18,
         BountyHunterMaterials.HELMET_ARMOR
            + BountyHunterMaterials.CHEST_ARMOR
            + BountyHunterMaterials.LEGGINGS_ARMOR
            + BountyHunterMaterials.BOOTS_ARMOR
      );
   }

   @Test
   void durabilityMatchesSpec() {
      assertEquals(275, BountyHunterMaterials.durabilityFor(Type.HELMET));
      assertEquals(400, BountyHunterMaterials.durabilityFor(Type.CHESTPLATE));
      assertEquals(380, BountyHunterMaterials.durabilityFor(Type.LEGGINGS));
      assertEquals(320, BountyHunterMaterials.durabilityFor(Type.BOOTS));
   }

   @Test
   void toughnessAndEnchantability() {
      assertEquals(2.0F, BountyHunterMaterials.TOUGHNESS, 0.001F);
      assertEquals(14, BountyHunterMaterials.ENCHANTABILITY);
      assertEquals(0.0F, BountyHunterMaterials.MATERIAL_KNOCKBACK_RESISTANCE, 0.001F);
      assertEquals(0.10, BountyHunterArmorSetBonus.KNOCKBACK_BONUS, 0.001);
   }

   @Test
   void emptyStackIsNotArmor() {
      assertFalse(BountyHunterArmorItem.isBountyHunterArmor(net.minecraft.world.item.ItemStack.EMPTY));
   }
}
