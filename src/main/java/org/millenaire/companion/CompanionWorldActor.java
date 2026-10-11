package org.millenaire.companion;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.millenaire.entity.MillVillager;

/**
 * FakePlayer-backed actor for player-like block/item interactions.
 */
public final class CompanionWorldActor {
   private CompanionWorldActor() {
   }

   public static FakePlayer actorFor(ServerLevel level, MillVillager companion) {
      GameProfile profile = new GameProfile(
         UUID.nameUUIDFromBytes(("millenaire-companion-" + companion.getUUID()).getBytes()),
         "MillCompanion"
      );
      FakePlayer fp = FakePlayerFactory.get(level, profile);
      fp.moveTo(companion.getX(), companion.getY(), companion.getZ(), companion.getYRot(), companion.getXRot());
      syncHand(fp, companion);
      return fp;
   }

   private static void syncHand(FakePlayer fp, MillVillager companion) {
      fp.setItemInHand(InteractionHand.MAIN_HAND, companion.getMainHandItem().copy());
      fp.setItemInHand(InteractionHand.OFF_HAND, companion.getOffhandItem().copy());
   }

   public static String useItem(ServerLevel level, MillVillager companion) {
      FakePlayer fp = actorFor(level, companion);
      ItemStack stack = fp.getMainHandItem();
      if (stack.isEmpty()) {
         return "USE_ITEM failed: empty hand";
      }
      var result = stack.use(level, fp, InteractionHand.MAIN_HAND);
      companion.setItemInHand(InteractionHand.MAIN_HAND, fp.getMainHandItem());
      return "USE_ITEM result=" + result.getResult();
   }

   public static String useBlock(ServerLevel level, MillVillager companion, @Nullable BlockPos pos) {
      BlockPos target = pos != null ? pos : companion.blockPosition().relative(companion.getDirection());
      FakePlayer fp = actorFor(level, companion);
      BlockState state = level.getBlockState(target);
      if (state.isAir()) {
         return "USE_BLOCK failed: air at " + target.toShortString();
      }
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
      InteractionResult blockResult = state.useWithoutItem(level, fp, hit);
      if (blockResult.consumesAction()) {
         return "USE_BLOCK ok block=" + state.getBlock();
      }
      ItemStack stack = fp.getMainHandItem();
      if (!stack.isEmpty()) {
         UseOnContext ctx = new UseOnContext(fp, InteractionHand.MAIN_HAND, hit);
         InteractionResult itemResult = stack.useOn(ctx);
         companion.setItemInHand(InteractionHand.MAIN_HAND, fp.getMainHandItem());
         return "USE_BLOCK itemResult=" + itemResult + " block=" + state.getBlock();
      }
      return "USE_BLOCK no-op block=" + state.getBlock();
   }

   public static String attack(ServerLevel level, MillVillager companion, @Nullable LivingEntity target) {
      LivingEntity t = target;
      if (t == null) {
         double best = Double.MAX_VALUE;
         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, companion.getBoundingBox().inflate(12.0))) {
            if (e == companion || e instanceof ServerPlayer || e instanceof MillVillager || !e.isAlive()) {
               continue;
            }
            double d = companion.distanceToSqr(e);
            if (d < best) {
               best = d;
               t = e;
            }
         }
      }
      if (t == null) {
         return "ATTACK failed: no valid target";
      }
      companion.setAttackTarget(t);
      boolean ok = companion.performAttack(t);
      return ok ? "ATTACK ok target=" + t.getType().builtInRegistryHolder().key().location() : "ATTACK missed";
   }

   public static String placeBlock(ServerLevel level, MillVillager companion) {
      return useBlock(level, companion, companion.blockPosition().relative(companion.getDirection()));
   }
}
