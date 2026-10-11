package org.millenaire.companion.capability;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import org.millenaire.companion.CompanionIntent;
import org.millenaire.companion.CompanionWorldActor;
import org.millenaire.compat.tacz.TaczWeaponCompat;
import org.millenaire.entity.MillVillager;

public final class TaczCompanionCapability implements CompanionCapability {
   @Override
   public String id() {
      return "tacz";
   }

   @Override
   public boolean isAvailable() {
      return TaczWeaponCompat.isLoaded();
   }

   @Override
   public List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion) {
      List<String> lines = new ArrayList<>();
      lines.add("cap.tacz=loaded");
      if (TaczWeaponCompat.isGun(companion.getMainHandItem())) {
         lines.add("cap.tacz.mainHandGun=true");
      }
      return lines;
   }

   @Override
   @Nullable
   public String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent) {
      if (!"TACZ_FIRE".equals(intent.type()) && !"ATTACK".equals(intent.type())) {
         return null;
      }
      if (!"TACZ_FIRE".equals(intent.type())) {
         return null;
      }
      ItemStack hand = companion.getMainHandItem();
      if (!TaczWeaponCompat.isGun(hand)) {
         // Try equip a gun from inventory
         for (var entry : companion.getInventory().getAll().entrySet()) {
            if (entry.getValue() > 0 && TaczWeaponCompat.isGunItem(entry.getKey())) {
               companion.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(entry.getKey()));
               hand = companion.getMainHandItem();
               break;
            }
         }
      }
      if (!TaczWeaponCompat.isGun(hand)) {
         return "TACZ_FIRE failed: no gun in hand/inventory";
      }
      LivingEntity target = null;
      for (Monster m : level.getEntitiesOfClass(Monster.class, companion.getBoundingBox().inflate(24.0))) {
         target = m;
         break;
      }
      if (target == null) {
         return "TACZ_FIRE failed: no hostile target";
      }
      companion.setAttackTarget(target);
      if (TaczWeaponCompat.tryFire(companion, target)) {
         return "TACZ_FIRE ok";
      }
      return CompanionWorldActor.attack(level, companion, target) + " (tacz fallback)";
   }
}
