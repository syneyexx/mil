package org.millenaire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.millenaire.network.PlanPreviewPayload;

public final class GhostBuildingRenderer {
   private static final float ALPHA = 0.75F;
   private static final int FULL_BRIGHT = 15728880;
   private static PlanPreviewPayload cachedPreview;
   private static Rotation cachedRotation;
   private static List<GhostBuildingRenderer.GhostBlock> cachedBlocks = List.of();
   private static Set<BlockPos> cachedOccluders = Set.of();

   private GhostBuildingRenderer() {
   }

   public static void clear() {
      cachedPreview = null;
      cachedRotation = null;
      cachedBlocks = List.of();
      cachedOccluders = Set.of();
   }

   public static void render(PoseStack pose, MultiBufferSource buffers, PlanPreviewPayload preview, BlockPos pivot, Rotation rotation, boolean valid) {
      rebuildIfNeeded(preview, rotation);
      if (!cachedBlocks.isEmpty()) {
         VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
         float tintR = 1.0F;
         float tintG = valid ? 1.0F : 0.55F;
         float tintB = valid ? 1.0F : 0.55F;

         for (GhostBuildingRenderer.GhostBlock block : cachedBlocks) {
            pose.pushPose();
            pose.translate(pivot.getX() + block.rel().getX(), pivot.getY() + block.rel().getY(), pivot.getZ() + block.rel().getZ());

            for (int i = 0; i < Direction.values().length; i++) {
               Direction cullDir = Direction.values()[i];
               if (!cachedOccluders.contains(block.rel().relative(cullDir))) {
                  emitQuads(vc, pose, block.state(), block.faceQuads()[i], tintR, tintG, tintB);
               }
            }

            emitQuads(vc, pose, block.state(), block.generalQuads(), tintR, tintG, tintB);
            pose.popPose();
         }
      }
   }

   private static void emitQuads(VertexConsumer vc, PoseStack pose, BlockState state, List<BakedQuad> quads, float tintR, float tintG, float tintB) {
      for (BakedQuad quad : quads) {
         float r = tintR;
         float g = tintG;
         float b = tintB;
         if (quad.isTinted()) {
            int color = Minecraft.getInstance().getBlockColors().getColor(state, null, null, quad.getTintIndex());
            r *= (color >> 16 & 0xFF) / 255.0F;
            g *= (color >> 8 & 0xFF) / 255.0F;
            b *= (color & 0xFF) / 255.0F;
         }

         vc.putBulkData(pose.last(), quad, r, g, b, ALPHA, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
      }
   }

   private static void rebuildIfNeeded(PlanPreviewPayload preview, Rotation rotation) {
      if (preview != cachedPreview || rotation != cachedRotation) {
         Minecraft mc = Minecraft.getInstance();
         RandomSource random = RandomSource.create(42L);
         List<GhostBuildingRenderer.GhostBlock> blocks = new ArrayList<>(preview.blocks().size());
         Set<BlockPos> occluders = new HashSet<>();

         for (PlanPreviewPayload.Entry entry : preview.blocks()) {
            BlockState state = preview.palette().get(entry.paletteIndex());
            BlockPos rel = StructureTemplate.transform(entry.relativePos(), Mirror.NONE, rotation, BlockPos.ZERO);
            BlockState rotated = state.rotate(rotation);
            if (rotated.getRenderShape() == RenderShape.MODEL) {
               BakedModel model = mc.getBlockRenderer().getBlockModel(rotated);
               @SuppressWarnings("unchecked")
               List<BakedQuad>[] faceQuads = (List<BakedQuad>[]) new List[Direction.values().length];
               for (int i = 0; i < Direction.values().length; i++) {
                  faceQuads[i] = List.copyOf(model.getQuads(rotated, Direction.values()[i], random, ModelData.EMPTY, null));
               }
               List<BakedQuad> generalQuads = List.copyOf(model.getQuads(rotated, null, random, ModelData.EMPTY, null));
               blocks.add(new GhostBuildingRenderer.GhostBlock(rel, rotated, faceQuads, generalQuads));
               if (rotated.canOcclude()) {
                  occluders.add(rel);
               }
            }
         }

         cachedPreview = preview;
         cachedRotation = rotation;
         cachedBlocks = blocks;
         cachedOccluders = occluders;
      }
   }

   private record GhostBlock(BlockPos rel, BlockState state, List<BakedQuad>[] faceQuads, List<BakedQuad> generalQuads) {
   }
}
