package org.millenaire.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;

/**
 * Full-set bonus: Hunter's Resolve — +0.10 knockback resistance when all four pieces are worn.
 * Applied as a named transient attribute modifier; never stacks; cleared when incomplete.
 */
@EventBusSubscriber(modid = "millenaire")
public final class BountyHunterArmorSetBonus {
   public static final ResourceLocation HUNTERS_RESOLVE_ID =
      ResourceLocation.fromNamespaceAndPath("millenaire", "hunters_resolve");
   public static final double KNOCKBACK_BONUS = 0.10;

   private BountyHunterArmorSetBonus() {
   }

   public static void refresh(LivingEntity entity) {
      if (entity == null || entity.level().isClientSide()) {
         return;
      }
      AttributeInstance knockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
      if (knockback == null) {
         return;
      }
      knockback.removeModifier(HUNTERS_RESOLVE_ID);
      if (BountyHunterArmorItem.isFullSet(entity)) {
         knockback.addTransientModifier(
            new AttributeModifier(HUNTERS_RESOLVE_ID, KNOCKBACK_BONUS, AttributeModifier.Operation.ADD_VALUE)
         );
      }
   }

   public static boolean hasHuntersResolve(LivingEntity entity) {
      if (entity == null) {
         return false;
      }
      AttributeInstance knockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
      return knockback != null && knockback.hasModifier(HUNTERS_RESOLVE_ID);
   }

   @SubscribeEvent
   public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
      EquipmentSlot slot = event.getSlot();
      if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
         return;
      }
      if (!(event.getEntity() instanceof Player)) {
         // Still refresh for armor stands / other living if they somehow wear it.
      }
      refresh(event.getEntity());
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      refresh(event.getEntity());
   }

   @SubscribeEvent
   public static void onRespawn(PlayerRespawnEvent event) {
      refresh(event.getEntity());
   }
}
