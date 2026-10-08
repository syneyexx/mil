package org.millenaire.catalog;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.item.ItemHelper;

public final class CatalogVillagerStager {
   @Nullable
   private static MillVillager lastSpawned;

   private CatalogVillagerStager() {
   }

   @Nullable
   public static MillVillager spawn(ServerLevel level, ResourceLocation typeId, BlockPos pos) {
      VillagerType vt = ModCultures.getVillagerType(typeId);
      if (vt == null) {
         return null;
      }

      MillVillager villager = (MillVillager)((EntityType)ModEntities.MILL_VILLAGER.get()).create(level);
      if (villager == null) {
         return null;
      }

      ItemStack mainHand = resolveTravelBookItem(vt.travelBookHeldItem());
      ItemStack offHand = resolveTravelBookItem(vt.travelBookHeldItemOffHand());
      float yaw = CatalogCameraFraming.villagerFacingYaw();
      villager.setVillagerTypeId(typeId);
      villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
      villager.setYBodyRot(yaw);
      villager.yBodyRotO = yaw;
      villager.setYHeadRot(yaw);
      villager.yHeadRotO = yaw;
      VillagerAppearanceFactory.randomizeAppearance(villager, vt);
      villager.updateClothTextures(vt);
      villager.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
      villager.setItemSlot(EquipmentSlot.OFFHAND, offHand);
      villager.setNoAi(true);
      villager.setNoGravity(true);
      villager.setGuiPreviewMode(true);
      villager.setPersistenceRequired();
      level.addFreshEntity(villager);
      lastSpawned = villager;
      return villager;
   }

   private static ItemStack resolveTravelBookItem(@Nullable String itemId) {
      if (itemId != null && !itemId.isBlank()) {
         Item item = ItemHelper.resolve(itemId);
         return item == null ? ItemStack.EMPTY : new ItemStack(item);
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static void discardPrevious() {
      if (lastSpawned != null) {
         lastSpawned.discard();
         lastSpawned = null;
      }
   }
}
