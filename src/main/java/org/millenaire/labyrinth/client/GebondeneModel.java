package org.millenaire.labyrinth.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneEntity;

/**
 * Humanoid Gebondene with binding straps across the torso.
 */
@OnlyIn(Dist.CLIENT)
public class GebondeneModel<T extends GebondeneEntity> extends HumanoidModel<T> {
   public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
      ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, "gebondene"),
      "main"
   );

   public GebondeneModel(ModelPart root) {
      super(root);
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition body = root.getChild("body");

      // Binding straps — ritual bindings that mark the Gebondene as claim of the Orde.
      body.addOrReplaceChild(
         "strap_left",
         CubeListBuilder.create().texOffs(0, 32).addBox(-3.6F, 0.5F, -2.55F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.05F)),
         PartPose.ZERO
      );
      body.addOrReplaceChild(
         "strap_right",
         CubeListBuilder.create().texOffs(4, 32).addBox(2.6F, 0.5F, -2.55F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.05F)),
         PartPose.ZERO
      );
      body.addOrReplaceChild(
         "strap_cross",
         CubeListBuilder.create().texOffs(8, 32).addBox(-3.0F, 4.0F, -2.7F, 6.0F, 1.5F, 1.0F, new CubeDeformation(0.02F)),
         PartPose.ZERO
      );
      body.addOrReplaceChild(
         "strap_shoulder",
         CubeListBuilder.create().texOffs(8, 35).addBox(-4.0F, -0.2F, -2.4F, 8.0F, 1.0F, 4.5F, new CubeDeformation(0.15F)),
         PartPose.ZERO
      );

      return LayerDefinition.create(mesh, 64, 64);
   }
}
