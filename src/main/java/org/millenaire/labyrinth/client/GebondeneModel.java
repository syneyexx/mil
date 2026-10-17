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
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;

/**
 * Humanoid Gebondene with ritual straps, hooks, mask plate and lantern attachment.
 */
@OnlyIn(Dist.CLIENT)
public class GebondeneModel<T extends GebondeneEntity> extends HumanoidModel<T> {
   public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
      ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, "gebondene"),
      "main"
   );

   private final ModelPart mask;
   private final ModelPart hookLeft;
   private final ModelPart hookRight;
   private final ModelPart lantern;
   private final ModelPart keys;
   private final ModelPart halo;

   public GebondeneModel(ModelPart root) {
      super(root);
      this.mask = this.head.getChild("mask");
      this.hookLeft = this.body.getChild("hook_left");
      this.hookRight = this.body.getChild("hook_right");
      this.lantern = this.leftArm.getChild("lantern");
      this.keys = this.body.getChild("keys");
      this.halo = this.head.getChild("halo");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      PartDefinition body = root.getChild("body");
      PartDefinition head = root.getChild("head");
      PartDefinition leftArm = root.getChild("left_arm");

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
      body.addOrReplaceChild(
         "hook_left",
         CubeListBuilder.create().texOffs(40, 32).addBox(-5.2F, 2.0F, -1.0F, 1.2F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
         PartPose.ZERO
      );
      body.addOrReplaceChild(
         "hook_right",
         CubeListBuilder.create().texOffs(44, 32).addBox(4.0F, 2.0F, -1.0F, 1.2F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
         PartPose.ZERO
      );
      body.addOrReplaceChild(
         "keys",
         CubeListBuilder.create().texOffs(48, 32).addBox(-2.0F, 8.0F, -3.2F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.05F)),
         PartPose.ZERO
      );

      head.addOrReplaceChild(
         "mask",
         CubeListBuilder.create().texOffs(32, 0).addBox(-4.2F, -8.2F, -4.4F, 8.4F, 8.4F, 1.2F, new CubeDeformation(0.05F)),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "halo",
         CubeListBuilder.create().texOffs(0, 48).addBox(-5.0F, -12.0F, -1.0F, 10.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
         PartPose.ZERO
      );

      leftArm.addOrReplaceChild(
         "lantern",
         CubeListBuilder.create().texOffs(24, 48).addBox(-1.5F, 8.0F, -2.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.05F)),
         PartPose.ZERO
      );

      return LayerDefinition.create(mesh, 64, 64);
   }

   @Override
   public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
      GebondeneKind kind = entity.getKind();

      boolean showMask = kind == GebondeneKind.ARCHITECT
         || kind == GebondeneKind.SPIJKERMEESTER
         || kind == GebondeneKind.GASTHEER
         || kind == GebondeneKind.SPIEGELGAST
         || kind == GebondeneKind.STILLE;
      boolean showHooks = kind == GebondeneKind.KETTINGZUSTER
         || kind == GebondeneKind.RAMMELAAR
         || kind == GebondeneKind.NAAISTER
         || kind == GebondeneKind.SPIJKERMEESTER;
      boolean showLantern = kind == GebondeneKind.GIDS || kind == GebondeneKind.PELGRIM;
      boolean showKeys = kind == GebondeneKind.SLEUTELDRAGER || kind == GebondeneKind.POORTWACHTER;
      boolean showHalo = kind == GebondeneKind.GETUIGE || kind == GebondeneKind.ARCHITECT;

      this.mask.visible = showMask;
      this.hookLeft.visible = showHooks;
      this.hookRight.visible = showHooks;
      this.lantern.visible = showLantern;
      this.keys.visible = showKeys;
      this.halo.visible = showHalo;

      if (showLantern) {
         this.lantern.xRot = -0.4F + Mth.sin(ageInTicks * 0.08F) * 0.05F;
      }
      if (showKeys) {
         this.keys.zRot = Mth.sin(ageInTicks * 0.1F) * 0.05F;
      }
      if (showHalo) {
         this.halo.yRot = ageInTicks * 0.03F;
      }
   }
}
