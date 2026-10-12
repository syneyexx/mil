package org.millenaire.worldgen.materialize;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Optional fast path for bulk worldgen placement via {@link LevelChunkSection#setBlockState}.
 * Falls back to {@link ServerLevel#setBlock} for block-entities, waterlogged, and when disabled.
 */
public final class FastSectionBlockWriter {
   private FastSectionBlockWriter() {
   }

   public static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenFastSectionWrites.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   /**
    * Place {@code state} at {@code pos}. Returns true when a change was applied.
    */
   public static boolean setBlock(ServerLevel level, BlockPos pos, BlockState state) {
      if (!enabled() || needsVanillaSetBlock(level, pos, state)) {
         return level.setBlock(pos, state, 2);
      }
      LevelChunk chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
      int sectionIndex = chunk.getSectionIndex(pos.getY());
      if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
         return level.setBlock(pos, state, 2);
      }
      LevelChunkSection section = chunk.getSection(sectionIndex);
      int lx = pos.getX() & 15;
      int ly = pos.getY() & 15;
      int lz = pos.getZ() & 15;
      BlockState previous = section.setBlockState(lx, ly, lz, state, false);
      if (previous == state) {
         return false;
      }
      chunk.setUnsaved(true);
      level.getChunkSource().blockChanged(pos);
      level.getLightEngine().checkBlock(pos);
      return true;
   }

   private static boolean needsVanillaSetBlock(ServerLevel level, BlockPos pos, BlockState state) {
      if (state.hasBlockEntity()) {
         return true;
      }
      if (state.getFluidState() != null && !state.getFluidState().isEmpty()) {
         return true;
      }
      BlockEntity be = level.getBlockEntity(pos);
      return be != null;
   }
}
