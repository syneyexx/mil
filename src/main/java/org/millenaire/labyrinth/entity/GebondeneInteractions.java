package org.millenaire.labyrinth.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import org.millenaire.item.ModItems;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Concrete frenemy services while {@link GebondeneAttitude#GASTVRIJ}.
 */
public final class GebondeneInteractions {
   private GebondeneInteractions() {
   }

   public static InteractionResult tryInteract(GebondeneEntity host, Player player) {
      if (!(player instanceof ServerPlayer serverPlayer) || !(host.level() instanceof ServerLevel level)) {
         return InteractionResult.SUCCESS;
      }
      if (host.getAttitude() != GebondeneAttitude.GASTVRIJ) {
         return InteractionResult.PASS;
      }

      LabyrinthOrdeData orde = LabyrinthOrdeData.get(level);
      if (orde.isHostile(serverPlayer.getUUID()) && canReconcile(host.getKind())) {
         return reconcile(host, serverPlayer, orde);
      }

      return switch (host.getKind()) {
         case SMID -> repair(host, serverPlayer);
         case GASTHEER -> hostRest(host, serverPlayer);
         case NOTARIS -> contractBuff(host, serverPlayer);
         case GIDS -> guideHint(host, serverPlayer);
         case SCHATTER -> appraise(host, serverPlayer);
         case SLEUTELDRAGER -> grantKeyVision(host, serverPlayer);
         case ARCHIVARIS -> loreHint(host, serverPlayer);
         case NAAISTER -> stitch(host, serverPlayer);
         case PELGRIM -> pilgrimLead(host, serverPlayer);
         case BLINDE -> blindGuide(host, serverPlayer);
         case POORTWACHTER -> gateBlessing(host, serverPlayer);
         case KETTINGZUSTER -> chainPass(host, serverPlayer);
         case FLUITENDE -> fluteCalm(host, serverPlayer);
         case DANSERES -> danceCalm(host, serverPlayer);
         case GETUIGE -> witnessMark(host, serverPlayer, orde);
         case ARCHITECT -> architectMeasure(host, serverPlayer);
         case SPIJKERMEESTER -> spikeBlessing(host, serverPlayer);
         case SPIEGELGAST -> mirrorShortcut(host, serverPlayer);
         case ECHO -> echoTip(host, serverPlayer);
         case TWEELING -> twinSwap(host, serverPlayer);
         default -> tipOnly(host, serverPlayer);
      };
   }

   private static boolean canReconcile(GebondeneKind kind) {
      return kind == GebondeneKind.GASTHEER || kind == GebondeneKind.NOTARIS || kind == GebondeneKind.ARCHIVARIS;
   }

   private static InteractionResult reconcile(GebondeneEntity host, ServerPlayer player, LabyrinthOrdeData orde) {
      ItemStack offhand = player.getOffhandItem();
      if (!offhand.is(Items.GOLD_INGOT) && !offhand.is(ModItems.DENIER_OR.get())) {
         player.displayClientMessage(Component.translatable("labyrinth.interact.reconcile_need"), true);
         return InteractionResult.CONSUME;
      }
      offhand.shrink(1);
      orde.setHostile(player.getUUID(), false);
      for (GebondeneEntity nearby : ((ServerLevel) host.level()).getEntitiesOfClass(
         GebondeneEntity.class, host.getBoundingBox().inflate(64.0)
      )) {
         if (nearby.getAttitude() != GebondeneAttitude.GASTVRIJ) {
            nearby.setAttitude(GebondeneAttitude.GASTVRIJ);
         }
      }
      play(host, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.reconcile_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult repair(GebondeneEntity host, ServerPlayer player) {
      ItemStack stack = player.getMainHandItem();
      if (stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) {
         player.displayClientMessage(Component.translatable("labyrinth.interact.smid_need"), true);
         return InteractionResult.CONSUME;
      }
      if (!consumeOffer(player, Items.IRON_INGOT, 1) && !consumeDenier(player, 8)) {
         player.displayClientMessage(Component.translatable("labyrinth.interact.smid_pay"), true);
         return InteractionResult.CONSUME;
      }
      stack.setDamageValue(Math.max(0, stack.getDamageValue() - stack.getMaxDamage() / 3));
      play(host, SoundEvents.ANVIL_USE, 0.9F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.smid_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult hostRest(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
      player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0));
      play(host, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.1F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.gastheer_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult contractBuff(GebondeneEntity host, ServerPlayer player) {
      if (!consumeDenier(player, 4) && !consumeOffer(player, Items.PAPER, 1)) {
         player.displayClientMessage(Component.translatable("labyrinth.interact.notaris_pay"), true);
         return InteractionResult.CONSUME;
      }
      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0));
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 0));
      play(host, SoundEvents.BOOK_PAGE_TURN, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.notaris_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult guideHint(GebondeneEntity host, ServerPlayer player) {
      int[] cell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(
         player.blockPosition().getX(), player.blockPosition().getZ()
      );
      int regionX = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      var maze = org.millenaire.labyrinth.gen.LabyrinthMaze.forRegion(
         ((ServerLevel) host.level()).getSeed(), regionX, regionZ
      );
      var room = maze.getRoomTypeGlobal(cell[0], cell[1]);

      // Near chase pressure: hint the safe corridor instead of Core bearing.
      if (room == org.millenaire.labyrinth.gen.LabyrinthMaze.RoomType.GATE
         || room == org.millenaire.labyrinth.gen.LabyrinthMaze.RoomType.ROTARY
         || player.getRandom().nextFloat() < 0.35F) {
         int safe = org.millenaire.labyrinth.chase.RammelaarChaseHandler.safeWallFlag(
            ((ServerLevel) host.level()).getSeed(), cell[0], cell[1]
         );
         String dirKey = switch (safe) {
            case org.millenaire.labyrinth.gen.LabyrinthMaze.WALL_N -> "north";
            case org.millenaire.labyrinth.gen.LabyrinthMaze.WALL_E -> "east";
            case org.millenaire.labyrinth.gen.LabyrinthMaze.WALL_S -> "south";
            default -> "west";
         };
         boolean honest = player.getRandom().nextFloat() < 0.8F;
         if (!honest) {
            dirKey = switch (dirKey) {
               case "north" -> "south";
               case "south" -> "north";
               case "east" -> "west";
               default -> "east";
            };
         }
         Component dir = Component.translatable("labyrinth.dir." + dirKey);
         play(host, SoundEvents.LANTERN_PLACE, 1.0F);
         player.displayClientMessage(Component.translatable("labyrinth.interact.gids_chase", dir), true);
         return InteractionResult.CONSUME;
      }

      int coreX = LabyrinthKeys.CORE_CELL_X * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      int coreZ = LabyrinthKeys.CORE_CELL_Z * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      double dx = coreX - player.getX();
      double dz = coreZ - player.getZ();
      // ~75% honest: sometimes invert.
      boolean honest = player.getRandom().nextFloat() < 0.75F;
      if (!honest) {
         dx = -dx;
         dz = -dz;
      }
      Component dir = Component.translatable("labyrinth.dir." + cardinal(dx, dz));
      play(host, SoundEvents.LANTERN_PLACE, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.gids_ok", dir), true);
      if (!honest && player.getRandom().nextBoolean()) {
         player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
      }
      return InteractionResult.CONSUME;
   }

   private static InteractionResult appraise(GebondeneEntity host, ServerPlayer player) {
      ItemStack stack = player.getMainHandItem();
      if (stack.isEmpty()) {
         player.displayClientMessage(Component.translatable("labyrinth.interact.schatter_need"), true);
         return InteractionResult.CONSUME;
      }
      int payout = payoutFor(stack);
      if (payout <= 0) {
         player.displayClientMessage(Component.translatable("labyrinth.interact.schatter_refuse"), true);
         return InteractionResult.CONSUME;
      }
      stack.shrink(1);
      player.getInventory().add(new ItemStack(ModItems.DENIER.get(), payout));
      play(host, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.schatter_ok", payout), true);
      return InteractionResult.CONSUME;
   }

   private static int payoutFor(ItemStack stack) {
      if (stack.is(ModItems.LABYRINTH_ZEGEL.get())
         || stack.is(ModItems.LABYRINTH_MASKER.get())
         || stack.is(ModItems.LABYRINTH_KOORD.get())) {
         // Tempting but costly — selling a gate key is a bad bargain.
         return 24;
      }
      if (stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_BLOCK)) {
         return stack.is(Items.GOLD_BLOCK) ? 36 : 6;
      }
      if (stack.is(Items.DIAMOND) || stack.is(Items.EMERALD)) {
         return 12;
      }
      if (stack.is(Items.IRON_INGOT)) {
         return 2;
      }
      if (stack.is(ModItems.DENIER_OR.get())) {
         return 16;
      }
      return 0;
   }

   private static InteractionResult grantKeyVision(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0));
      play(host, SoundEvents.CHAIN_PLACE, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.sleutel_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult loreHint(GebondeneEntity host, ServerPlayer player) {
      play(host, SoundEvents.BOOK_PAGE_TURN, 0.9F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.archivaris_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult stitch(GebondeneEntity host, ServerPlayer player) {
      player.heal(6.0F);
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
      play(host, SoundEvents.WOOL_PLACE, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.naaister_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult pilgrimLead(GebondeneEntity host, ServerPlayer player) {
      int coreX = LabyrinthKeys.CORE_CELL_X * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      int coreZ = LabyrinthKeys.CORE_CELL_Z * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      host.getNavigation().moveTo(coreX + 0.5, LabyrinthKeys.FLOOR_Y + 1.0, coreZ + 0.5, 1.0);
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
      play(host, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.pelgrim_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult blindGuide(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0));
      play(host, SoundEvents.SCULK_CLICKING, 0.8F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.blinde_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult gateBlessing(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
      play(host, SoundEvents.IRON_DOOR_CLOSE, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.poort_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult chainPass(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.JUMP, 400, 0));
      play(host, SoundEvents.CHAIN_BREAK, 0.9F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.ketting_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult fluteCalm(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
      // Calm nearby hunters for a short while.
      for (GebondeneEntity nearby : ((ServerLevel) host.level()).getEntitiesOfClass(
         GebondeneEntity.class, host.getBoundingBox().inflate(24.0)
      )) {
         if (nearby.getAttitude() == GebondeneAttitude.JACHT && nearby.getKind().tier() != GebondeneKind.Tier.LEADER) {
            nearby.setAttitude(GebondeneAttitude.GEALARMEERD);
            nearby.setTarget(null);
         }
      }
      play(host, SoundEvents.NOTE_BLOCK_FLUTE.value(), 1.2F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.fluit_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult danceCalm(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0));
      player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0));
      play(host, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.dans_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult witnessMark(GebondeneEntity host, ServerPlayer player, LabyrinthOrdeData orde) {
      int[] cell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
      orde.addReputation(player.getUUID(),
         org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[0]),
         org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[1]),
         5);
      play(host, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.getuige_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult architectMeasure(GebondeneEntity host, ServerPlayer player) {
      play(host, SoundEvents.ELDER_GUARDIAN_CURSE, 0.7F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.architect"), true);
      // Soft invite: resistance before optional ritual fight.
      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
      return InteractionResult.CONSUME;
   }

   private static InteractionResult spikeBlessing(GebondeneEntity host, ServerPlayer player) {
      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
      play(host, SoundEvents.ANVIL_PLACE, 0.8F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.spijker_ok"), true);
      return InteractionResult.CONSUME;
   }

   /**
    * Spiegelgast: fold the player one hub-spacing toward the Core (impossible shortcut feel).
    */
   private static InteractionResult mirrorShortcut(GebondeneEntity host, ServerPlayer player) {
      int[] cell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(
         player.blockPosition().getX(), player.blockPosition().getZ()
      );
      int step = LabyrinthKeys.HUB_SPACING;
      int dx = Integer.compare(LabyrinthKeys.CORE_CELL_X, cell[0]) * step;
      int dz = Integer.compare(LabyrinthKeys.CORE_CELL_Z, cell[1]) * step;
      if (dx == 0 && dz == 0) {
         dx = step;
      }
      int destCellX = cell[0] + (dx == 0 ? 0 : Integer.signum(dx) * step);
      int destCellZ = cell[1] + (dz == 0 ? 0 : Integer.signum(dz) * step);
      double destX = destCellX * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2 + 0.5;
      double destZ = destCellZ * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2 + 0.5;
      double destY = LabyrinthKeys.FLOOR_Y + 1.0;
      player.teleportTo(destX, destY, destZ);
      player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
      play(host, SoundEvents.ENDERMAN_TELEPORT, 0.8F);
      if (host.level() instanceof ServerLevel level) {
         level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
            destX, destY + 1.0, destZ, 24, 0.4, 0.6, 0.4, 0.02
         );
      }
      player.displayClientMessage(Component.translatable("labyrinth.interact.spiegelgast_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult echoTip(GebondeneEntity host, ServerPlayer player) {
      play(host, SoundEvents.SCULK_CLICKING, 0.7F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.echo_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult twinSwap(GebondeneEntity host, ServerPlayer player) {
      // Brief invis + swap place with the Tweeling (player ↔ host).
      double px = player.getX();
      double py = player.getY();
      double pz = player.getZ();
      player.teleportTo(host.getX(), host.getY(), host.getZ());
      host.teleportTo(px, py, pz);
      player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0));
      play(host, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F);
      player.displayClientMessage(Component.translatable("labyrinth.interact.tweeling_ok"), true);
      return InteractionResult.CONSUME;
   }

   private static InteractionResult tipOnly(GebondeneEntity host, ServerPlayer player) {
      play(host, SoundEvents.VILLAGER_AMBIENT, 0.9F);
      player.displayClientMessage(Component.translatable("labyrinth.interact." + host.getKind().displayKeySuffix()), true);
      return InteractionResult.CONSUME;
   }

   private static boolean consumeOffer(Player player, net.minecraft.world.item.Item item, int count) {
      if (player.getAbilities().instabuild) {
         return true;
      }
      ItemStack main = player.getMainHandItem();
      if (main.is(item) && main.getCount() >= count) {
         main.shrink(count);
         return true;
      }
      ItemStack off = player.getOffhandItem();
      if (off.is(item) && off.getCount() >= count) {
         off.shrink(count);
         return true;
      }
      return false;
   }

   private static boolean consumeDenier(Player player, int count) {
      if (player.getAbilities().instabuild) {
         return true;
      }
      int remaining = count;
      for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(ModItems.DENIER.get())) {
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
         }
      }
      return remaining <= 0;
   }

   private static String cardinal(double dx, double dz) {
      if (Math.abs(dx) > Math.abs(dz)) {
         return dx > 0 ? "east" : "west";
      }
      return dz > 0 ? "south" : "north";
   }

   private static void play(GebondeneEntity host, net.minecraft.sounds.SoundEvent sound, float pitch) {
      host.level().playSound(null, host.getX(), host.getY(), host.getZ(), sound, SoundSource.NEUTRAL, 0.95F, pitch);
   }
}
