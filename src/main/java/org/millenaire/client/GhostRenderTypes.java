package org.millenaire.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.LineStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;

public final class GhostRenderTypes {
   public static final RenderType LINES_NO_DEPTH = RenderType.create(
      "millenaire_lines_no_depth",
      DefaultVertexFormat.POSITION_COLOR_NORMAL,
      Mode.LINES,
      1536,
      CompositeState.builder()
         .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
         .setLineState(new LineStateShard(OptionalDouble.empty()))
         .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
         .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
         .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
         .setWriteMaskState(RenderStateShard.COLOR_WRITE)
         .setCullState(RenderStateShard.NO_CULL)
         .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
         .createCompositeState(false)
   );

   private GhostRenderTypes() {
   }
}
