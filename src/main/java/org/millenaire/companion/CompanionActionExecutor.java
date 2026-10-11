package org.millenaire.companion;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.millenaire.companion.capability.CompanionCapabilityRegistry;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.living.actions.NpcActionContext;
import org.millenaire.living.actions.NpcActionExecutor;
import org.millenaire.living.actions.NpcActionProposal;
import org.millenaire.living.actions.NpcActionRegistry;
import org.millenaire.living.actions.NpcActionType;
import org.millenaire.living.actions.NpcActionValidator;
import org.millenaire.village.Village;

public final class CompanionActionExecutor {
   private CompanionActionExecutor() {
   }

   public static String execute(
      ServerLevel level,
      ServerPlayer player,
      MillVillager companion,
      CompanionBond bond,
      CompanionIntent intent,
      @Nullable Village village
   ) {
      if (!CompanionActionCatalog.isKnown(intent.type())) {
         return intent.type() + " rejected: unknown";
      }
      if (!allowedByConfig(intent.type())) {
         return intent.type() + " rejected: config deny";
      }
      String cap = CompanionCapabilityRegistry.tryExecute(level, player, companion, intent);
      if (cap != null) {
         return cap;
      }
      return switch (intent.type()) {
         case "FOLLOW_PLAYER", "COME_CLOSER", "RETURN_TO_PLAYER" -> {
            bond.setStance(CompanionStance.STICK_CLOSE);
            companion.getNavManager().navigateTo(companion, player.blockPosition(), 0.5);
            yield intent.type() + " ok: following";
         }
         case "WAIT_HERE", "HOLD_POSITION" -> {
            companion.getNavManager().stop(companion);
            yield intent.type() + " ok: waiting";
         }
         case "SET_STANCE" -> {
            CompanionStance stance = CompanionStance.parse(
               intent.arg() != null ? intent.arg() : intent.target(), CompanionStance.FREE_ROAM
            );
            bond.setStance(stance);
            yield "SET_STANCE ok=" + stance;
         }
         case "RETURN_TO_VILLAGE", "GO_HOME" -> {
            if (village != null) {
               companion.getNavManager().navigateTo(companion, village.getCenter(), 0.5);
               yield intent.type() + " ok: heading to village";
            }
            yield intent.type() + " failed: no village";
         }
         case "PICK_UP_NEARBY_ITEMS", "ACCEPT_GIFT_FROM_PLAYER" -> pickup(level, companion, player);
         case "EQUIP_BEST_GEAR" -> equipBest(companion);
         case "EQUIP_ITEM" -> equipNamed(companion, intent.target() != null ? intent.target() : intent.arg());
         case "UNEQUIP" -> {
            clearArmor(companion);
            yield "UNEQUIP ok";
         }
         case "DROP_ITEM" -> dropOne(level, companion);
         case "DEFEND_PLAYER", "ATTACK_HOSTILE" -> {
            Monster m = nearestMonster(level, companion);
            if (m == null) {
               yield intent.type() + " failed: no hostile";
            }
            companion.setAttackTarget(m);
            bond.setStance(CompanionStance.STICK_CLOSE);
            yield intent.type() + " ok target=" + m.getType().builtInRegistryHolder().key().location();
         }
         case "FLEE_TO_SAFETY" -> {
            companion.setAttackTarget(null);
            companion.getNavManager().navigateTo(companion, player.blockPosition(), 0.6);
            yield "FLEE_TO_SAFETY ok";
         }
         case "SAY_ALOUD" -> "SAY_ALOUD ok";
         case "USE_ITEM" -> CompanionWorldActor.useItem(level, companion);
         case "USE_BLOCK" -> CompanionWorldActor.useBlock(level, companion, null);
         case "ATTACK" -> CompanionWorldActor.attack(level, companion, null);
         case "PLACE_BLOCK" -> CompanionWorldActor.placeBlock(level, companion);
         case "GATHER_GOODS", "GET_TOOL", "IDLE_PLAY", "SOCIALISE", "CHAT_WITH_VILLAGER" -> {
            if (companion.getGoalScheduler() != null) {
               companion.getGoalScheduler().forceStop(null);
            }
            yield intent.type() + " ok: scheduler cleared for re-pick";
         }
         case "WORK_FOR_DENIERS",
            "HELP_BUILD",
            "DELIVER_TO_HOUSEHOLD",
            "STORE_IN_BUILDING",
            "VISIT_BUILDING",
            "OBSERVE_VILLAGER",
            "ATTEND_CEREMONY",
            "SELL_WARES",
            "BUY_SUPPLIES",
            "OPEN_TRADE_WITH_PLAYER" -> {
            bond.setStance(CompanionStance.VILLAGE_DAY_JOB);
            yield intent.type() + " ok: stance=VILLAGE_DAY_JOB";
         }
         case "VIEW_VILLAGE_NEEDS" -> living(player, companion, NpcActionType.VIEW_VILLAGE_NEEDS);
         case "OFFER_ASSISTANCE" -> living(player, companion, NpcActionType.OFFER_ASSISTANCE);
         case "VIEW_VILLAGE_STATUS" -> living(player, companion, NpcActionType.VIEW_VILLAGE_STATUS);
         default -> intent.type() + " not implemented";
      };
   }

   private static boolean allowedByConfig(String type) {
      try {
         String deny = MillenaireServerConfig.SERVER.companionActionDenyList.get();
         if (deny != null && !deny.isBlank()) {
            for (String part : deny.split(",")) {
               if (type.equalsIgnoreCase(part.trim())) {
                  return false;
               }
            }
         }
         String allow = MillenaireServerConfig.SERVER.companionActionAllowList.get();
         if (allow != null && !allow.isBlank()) {
            for (String part : allow.split(",")) {
               if (type.equalsIgnoreCase(part.trim())) {
                  return true;
               }
            }
            return false;
         }
      } catch (IllegalStateException ignored) {
      }
      return true;
   }

   private static String pickup(ServerLevel level, MillVillager companion, ServerPlayer player) {
      AABB box = companion.getBoundingBox().inflate(6.0, 3.0, 6.0);
      int taken = 0;
      for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
         ItemStack stack = entity.getItem();
         if (stack.isEmpty() || !isPickupAllowed(stack)) {
            continue;
         }
         companion.getNavManager().navigateTo(companion, entity.blockPosition(), 0.5);
         companion.getInventory().add(stack.getItem(), stack.getCount());
         maybeEquipStack(companion, stack.copy());
         CompanionSharedEventLog.onGift(player, BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
         entity.discard();
         taken++;
         if (taken >= 8) {
            break;
         }
      }
      return taken == 0 ? "PICK_UP failed: no eligible items" : "PICK_UP ok count=" + taken;
   }

   private static boolean isPickupAllowed(ItemStack stack) {
      if (stack.getItem() instanceof ArmorItem) {
         return true;
      }
      var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (id == null) {
         return false;
      }
      String path = id.getPath().toLowerCase(Locale.ROOT);
      return path.contains("sword")
         || path.contains("axe")
         || path.contains("pickaxe")
         || path.contains("bow")
         || path.contains("gun")
         || path.contains("bread")
         || stack.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD)
         || id.getNamespace().contains("armor")
         || id.getNamespace().contains("magistu")
         || id.getNamespace().contains("immersive")
         || id.getNamespace().contains("tacz")
         || "minecraft".equals(id.getNamespace());
   }

   private static String equipBest(MillVillager companion) {
      int equipped = 0;
      for (var entry : companion.getInventory().getAll().entrySet()) {
         if (entry.getValue() <= 0) {
            continue;
         }
         if (maybeEquipStack(companion, new ItemStack(entry.getKey()))) {
            equipped++;
         }
      }
      return equipped == 0 ? "EQUIP_BEST_GEAR failed: nothing wearable" : "EQUIP_BEST_GEAR ok count=" + equipped;
   }

   private static String equipNamed(MillVillager companion, @Nullable String name) {
      if (name == null || name.isBlank()) {
         return equipBest(companion);
      }
      ResourceLocation id = ResourceLocation.tryParse(name);
      for (var entry : companion.getInventory().getAll().entrySet()) {
         if (entry.getValue() <= 0) {
            continue;
         }
         ResourceLocation key = BuiltInRegistries.ITEM.getKey(entry.getKey());
         if (key != null
            && (key.toString().equalsIgnoreCase(name)
               || key.getPath().contains(name.toLowerCase(Locale.ROOT))
               || (id != null && key.equals(id)))) {
            if (maybeEquipStack(companion, new ItemStack(entry.getKey()))) {
               return "EQUIP_ITEM ok=" + key;
            }
         }
      }
      return "EQUIP_ITEM failed: not found " + name;
   }

   private static boolean maybeEquipStack(MillVillager companion, ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }
      if (stack.getItem() instanceof ArmorItem armor) {
         EquipmentSlot slot = armor.getType().getSlot();
         companion.setItemSlot(slot, stack.copyWithCount(1));
         return true;
      }
      String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
      if (path.contains("helmet")) {
         companion.setItemSlot(EquipmentSlot.HEAD, stack.copyWithCount(1));
         return true;
      }
      if (path.contains("chest") || path.contains("tunic") || path.contains("plate")) {
         companion.setItemSlot(EquipmentSlot.CHEST, stack.copyWithCount(1));
         return true;
      }
      if (path.contains("leg")) {
         companion.setItemSlot(EquipmentSlot.LEGS, stack.copyWithCount(1));
         return true;
      }
      if (path.contains("boot")) {
         companion.setItemSlot(EquipmentSlot.FEET, stack.copyWithCount(1));
         return true;
      }
      if (path.contains("sword") || path.contains("axe") || path.contains("gun") || path.contains("bow")) {
         companion.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
         return true;
      }
      return false;
   }

   private static void clearArmor(MillVillager companion) {
      companion.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      companion.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
      companion.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
      companion.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
   }

   private static String dropOne(ServerLevel level, MillVillager companion) {
      ItemStack hand = companion.getMainHandItem();
      if (hand.isEmpty()) {
         return "DROP_ITEM failed: empty hand";
      }
      ItemEntity dropped = new ItemEntity(level, companion.getX(), companion.getY() + 0.5, companion.getZ(), hand.copy());
      level.addFreshEntity(dropped);
      companion.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      return "DROP_ITEM ok";
   }

   private static Monster nearestMonster(ServerLevel level, MillVillager companion) {
      Monster best = null;
      double bestD = Double.MAX_VALUE;
      for (Monster m : level.getEntitiesOfClass(Monster.class, companion.getBoundingBox().inflate(24.0))) {
         double d = companion.distanceToSqr(m);
         if (d < bestD) {
            bestD = d;
            best = m;
         }
      }
      return best;
   }

   private static String living(ServerPlayer player, MillVillager companion, NpcActionType type) {
      try {
         NpcActionContext ctx = NpcActionValidator.validateSession(player.serverLevel(), player, companion.getId(), null);
         if (ctx == null) {
            return type.name() + " failed: invalid session";
         }
         NpcActionProposal proposal = NpcActionRegistry.createProposal(ctx, type, null);
         var result = NpcActionExecutor.execute(ctx, proposal);
         return type.name() + " result=" + (result != null ? result.messageKey() : "null");
      } catch (Exception e) {
         return type.name() + " failed: " + e.getClass().getSimpleName();
      }
   }
}
