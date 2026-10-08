package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.PlacementStep;

public record PlanPreviewPayload(
   String planSetId, String variant, int variantIndex, int width, int depth, int groundLevel, List<BlockState> palette, List<PlanPreviewPayload.Entry> blocks
) implements CustomPacketPayload {
   static final int MAX_BLOCKS = 16384;
   private static final int MAX_PALETTE = 4096;
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<PlanPreviewPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "plan_preview"));
   public static final StreamCodec<ByteBuf, PlanPreviewPayload> STREAM_CODEC = StreamCodec.of(PlanPreviewPayload::encode, PlanPreviewPayload::decode);
   private static final StreamCodec<ByteBuf, BlockState> BLOCK_STATE_CODEC = ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY);

   public static PlanPreviewPayload fromSteps(
      String planSetId, String variant, int variantIndex, int width, int depth, int groundLevel, List<PlacementStep> steps
   ) {
      Map<BlockState, Integer> indices = new LinkedHashMap<>();
      List<PlanPreviewPayload.Entry> blocks = new ArrayList<>(Math.min(steps.size(), 16384));

      for (PlacementStep step : steps) {
         if (blocks.size() >= 16384) {
            break;
         }

         Integer index = indices.computeIfAbsent(step.blockState(), s -> indices.size() < 4096 ? indices.size() : null);
         if (index != null) {
            blocks.add(new PlanPreviewPayload.Entry(step.relativePos(), index));
         }
      }

      return new PlanPreviewPayload(planSetId, variant, variantIndex, width, depth, groundLevel, List.copyOf(indices.keySet()), blocks);
   }

   private static void encode(ByteBuf buf, PlanPreviewPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.variant);
      ByteBufCodecs.VAR_INT.encode(buf, payload.variantIndex);
      ByteBufCodecs.VAR_INT.encode(buf, payload.width);
      ByteBufCodecs.VAR_INT.encode(buf, payload.depth);
      ByteBufCodecs.VAR_INT.encode(buf, payload.groundLevel);
      int paletteCount = Math.min(payload.palette.size(), 4096);
      ByteBufCodecs.VAR_INT.encode(buf, paletteCount);

      for (int i = 0; i < paletteCount; i++) {
         BLOCK_STATE_CODEC.encode(buf, payload.palette.get(i));
      }

      int blockCount = Math.min(payload.blocks.size(), 16384);
      ByteBufCodecs.VAR_INT.encode(buf, blockCount);

      for (int i = 0; i < blockCount; i++) {
         PlanPreviewPayload.Entry entry = payload.blocks.get(i);
         ByteBufCodecs.VAR_INT.encode(buf, entry.relativePos().getX());
         ByteBufCodecs.VAR_INT.encode(buf, entry.relativePos().getY());
         ByteBufCodecs.VAR_INT.encode(buf, entry.relativePos().getZ());
         ByteBufCodecs.VAR_INT.encode(buf, entry.paletteIndex());
      }
   }

   private static PlanPreviewPayload decode(ByteBuf buf) {
      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 256) {
         planSetId = planSetId.substring(0, 256);
      }

      String variant = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (variant.length() > 256) {
         variant = variant.substring(0, 256);
      }

      int variantIndex = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int width = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int depth = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int groundLevel = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int paletteCount = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 4096);
      List<BlockState> palette = new ArrayList<>(paletteCount);

      for (int i = 0; i < paletteCount; i++) {
         palette.add((BlockState)BLOCK_STATE_CODEC.decode(buf));
      }

      int blockCount = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 16384);
      List<PlanPreviewPayload.Entry> blocks = new ArrayList<>(blockCount);

      for (int i = 0; i < blockCount; i++) {
         int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int index = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         if (index >= 0 && index < palette.size()) {
            blocks.add(new PlanPreviewPayload.Entry(new BlockPos(x, y, z), index));
         }
      }

      return new PlanPreviewPayload(planSetId, variant, variantIndex, width, depth, groundLevel, palette, blocks);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record Entry(BlockPos relativePos, int paletteIndex) {
   }
}
