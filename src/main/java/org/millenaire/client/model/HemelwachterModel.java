package org.millenaire.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.HemelwachterEntity;

/**
 * Hemelwachter production model — central eye core, dual gold rings + halo,
 * three layered wing pairs with blocky ivory feathers and gold leading edges.
 * Texture atlas 1254×1254. Target silhouette ~12 blocks tall, ~20 blocks span.
 * Visual authority: approved atlas materials + written size/structure requirements.
 */
@OnlyIn(Dist.CLIENT)
public class HemelwachterModel extends EntityModel<HemelwachterEntity> {
   private final ModelPart root;
   private final ModelPart body;
   private final ModelPart core;
   private final ModelPart eyeNeutral;
   private final ModelPart eyeProvoked;
   private final ModelPart ringOuter;
   private final ModelPart ringInner;
   private final ModelPart halo;
   private final ModelPart[] wingRoots = new ModelPart[6];
   private final ModelPart[] wingMids = new ModelPart[6];
   private final ModelPart[] wingTips = new ModelPart[6];

   public HemelwachterModel(ModelPart root) {
      this.root = root;
      this.body = root.getChild("body");
      this.core = this.body.getChild("core");
      this.eyeNeutral = this.core.getChild("eye_neutral");
      this.eyeProvoked = this.core.getChild("eye_provoked");
      this.ringOuter = this.body.getChild("ring_outer");
      this.ringInner = this.body.getChild("ring_inner");
      this.halo = this.body.getChild("halo");
      String[] names = {"wing_u_l", "wing_u_r", "wing_m_l", "wing_m_r", "wing_l_l", "wing_l_r"};
      for (int i = 0; i < 6; i++) {
         ModelPart wr = this.body.getChild(names[i] + "_root");
         this.wingRoots[i] = wr;
         ModelPart mid = wr.getChild(names[i] + "_mid");
         this.wingMids[i] = mid;
         this.wingTips[i] = mid.getChild(names[i] + "_tip");
      }
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;
      // Pivot near vertical centre; feet-equivalent at y≈0 for MobRenderer.
      PartDefinition body = root.addOrReplaceChild(
         "body",
         CubeListBuilder.create(),
         PartPose.offset(0.0F, 24.0F, 0.0F)
      );

      PartDefinition core = body.addOrReplaceChild(
         "core",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(-20.0F, -28.0F, -18.0F, 40.0F, 48.0F, 36.0F, none)
            .texOffs(HemelwachterAtlas.CORE_SIDE_U, HemelwachterAtlas.CORE_SIDE_V)
            .addBox(-24.0F, -20.0F, -14.0F, 8.0F, 32.0F, 28.0F, none)
            .texOffs(HemelwachterAtlas.CORE_SIDE_U, HemelwachterAtlas.CORE_SIDE_V)
            .addBox(16.0F, -20.0F, -14.0F, 8.0F, 32.0F, 28.0F, none)
            .texOffs(HemelwachterAtlas.CORE_REAR_U, HemelwachterAtlas.CORE_REAR_V)
            .addBox(-16.0F, -22.0F, 16.0F, 32.0F, 36.0F, 10.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-22.0F, -30.0F, -20.0F, 44.0F, 6.0F, 40.0F, new CubeDeformation(-0.1F))
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-22.0F, 18.0F, -20.0F, 44.0F, 6.0F, 40.0F, new CubeDeformation(-0.1F)),
         PartPose.offset(0.0F, -96.0F, 0.0F)
      );

      core.addOrReplaceChild(
         "eye_neutral",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.EYE_NEUTRAL_U, HemelwachterAtlas.EYE_NEUTRAL_V)
            .addBox(-14.0F, -14.0F, -4.0F, 28.0F, 28.0F, 6.0F, none),
         PartPose.offset(0.0F, -2.0F, -20.0F)
      );
      core.addOrReplaceChild(
         "eye_provoked",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.EYE_PROVOKED_U, HemelwachterAtlas.EYE_PROVOKED_V)
            .addBox(-14.0F, -14.0F, -4.0F, 28.0F, 28.0F, 6.0F, none),
         PartPose.offset(0.0F, -2.0F, -20.0F)
      );
      core.addOrReplaceChild(
         "rune_front",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.RUNE_CYAN_U, HemelwachterAtlas.RUNE_CYAN_V)
            .addBox(-6.0F, -6.0F, -1.0F, 12.0F, 12.0F, 2.0F, none),
         PartPose.offset(0.0F, 16.0F, -19.0F)
      );

      // Outer / inner segmented rings (approximated as crossed plates for performance)
      PartDefinition ringOuter = body.addOrReplaceChild(
         "ring_outer", CubeListBuilder.create(), PartPose.offset(0.0F, -96.0F, 0.0F));
      for (int i = 0; i < 8; i++) {
         float yaw = i * ((float)Math.PI * 2.0F / 8.0F);
         ringOuter.addOrReplaceChild(
            "ro_" + i,
            CubeListBuilder.create()
               .texOffs(HemelwachterAtlas.RING_FRONT_U, HemelwachterAtlas.RING_FRONT_V)
               .addBox(-6.0F, -3.0F, 52.0F, 12.0F, 6.0F, 18.0F, none)
               .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
               .addBox(-8.0F, -2.0F, 50.0F, 16.0F, 4.0F, 4.0F, none),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, yaw, 0.0F)
         );
      }
      PartDefinition ringInner = body.addOrReplaceChild(
         "ring_inner", CubeListBuilder.create(), PartPose.offset(0.0F, -96.0F, 0.0F));
      for (int i = 0; i < 6; i++) {
         float yaw = i * ((float)Math.PI * 2.0F / 6.0F) + 0.3F;
         ringInner.addOrReplaceChild(
            "ri_" + i,
            CubeListBuilder.create()
               .texOffs(HemelwachterAtlas.RING_UNDER_U, HemelwachterAtlas.RING_UNDER_V)
               .addBox(-5.0F, -2.5F, 34.0F, 10.0F, 5.0F, 14.0F, none)
               .texOffs(HemelwachterAtlas.RUNE_AMBER_U, HemelwachterAtlas.RUNE_AMBER_V)
               .addBox(-4.0F, -4.0F, 40.0F, 8.0F, 8.0F, 2.0F, none),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.4F, yaw, 0.0F)
         );
      }

      PartDefinition halo = body.addOrReplaceChild(
         "halo", CubeListBuilder.create(), PartPose.offset(0.0F, -130.0F, 0.0F));
      for (int i = 0; i < 7; i++) {
         float yaw = -0.9F + i * 0.3F;
         halo.addOrReplaceChild(
            "h_" + i,
            CubeListBuilder.create()
               .texOffs(HemelwachterAtlas.HALO_CYAN_U, HemelwachterAtlas.HALO_CYAN_V)
               .addBox(-4.0F, -3.0F, 28.0F, 8.0F, 6.0F, 10.0F, none)
               .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
               .addBox(-5.0F, -2.0F, 26.0F, 10.0F, 4.0F, 3.0F, none),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.15F, yaw, 0.0F)
         );
      }

      PartDefinition wing_u_l_root = body.addOrReplaceChild(
         "wing_u_l_root",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.WING_ROOT_U, HemelwachterAtlas.WING_ROOT_V)
            .addBox(-8.0F, -6.0F, -8.0F, 20.0F, 12.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-2.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F, none),
         PartPose.offsetAndRotation(22.0F, -110.0F, -4.0F, -0.55F, 0.9F, 0.35F)
      );
      PartDefinition wing_u_l_mid = wing_u_l_root.addOrReplaceChild(
         "wing_u_l_mid",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(0.0F, -5.0F, -10.0F, 73.0F, 10.0F, 20.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -6.0F, -12.0F, 73.0F, 3.0F, 4.0F, none),
         PartPose.offset(10.0F, 0.0F, 0.0F)
      );
      wing_u_l_mid.addOrReplaceChild(
         "wing_u_l_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 42.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(40.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -18.0F, -9.0F, 0.0F, 0.0F, -0.150F)
      );
      wing_u_l_mid.addOrReplaceChild(
         "wing_u_l_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 38.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(36.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -10.0F, -7.5F, 0.0F, 0.0F, -0.070F)
      );
      wing_u_l_mid.addOrReplaceChild(
         "wing_u_l_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 35.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(33.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -2.0F, -6.0F, 0.0F, 0.0F, 0.010F)
      );
      wing_u_l_mid.addOrReplaceChild(
         "wing_u_l_feather_3",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 32.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(30.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 6.0F, -4.5F, 0.0F, 0.0F, 0.090F)
      );
      wing_u_l_mid.addOrReplaceChild(
         "wing_u_l_feather_4",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 29.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(27.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 14.0F, -3.0F, 0.0F, 0.0F, 0.170F)
      );
      PartDefinition wing_u_l_tip = wing_u_l_mid.addOrReplaceChild(
         "wing_u_l_tip",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(0.0F, -4.0F, -8.0F, 50.0F, 8.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -5.0F, -10.0F, 50.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(69.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F)
      );
      wing_u_l_tip.addOrReplaceChild(
         "wing_u_l_tip_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 28.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -8.0F, -2.0F, 0.0F, 0.0F, 0.1F)
      );
      wing_u_l_tip.addOrReplaceChild(
         "wing_u_l_tip_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 24.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.1F)
      );
      wing_u_l_tip.addOrReplaceChild(
         "wing_u_l_tip_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 20.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, 6.0F, 2.0F, 0.0F, 0.0F, 0.1F)
      );

      PartDefinition wing_u_r_root = body.addOrReplaceChild(
         "wing_u_r_root",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.WING_ROOT_U, HemelwachterAtlas.WING_ROOT_V)
            .addBox(-12.0F, -6.0F, -8.0F, 20.0F, 12.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-6.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F, none),
         PartPose.offsetAndRotation(-22.0F, -110.0F, -4.0F, -0.55F, -0.9F, -0.35F)
      );
      PartDefinition wing_u_r_mid = wing_u_r_root.addOrReplaceChild(
         "wing_u_r_mid",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(0.0F, -5.0F, -10.0F, 73.0F, 10.0F, 20.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -6.0F, -12.0F, 73.0F, 3.0F, 4.0F, none),
         PartPose.offset(-10.0F, 0.0F, 0.0F)
      );
      wing_u_r_mid.addOrReplaceChild(
         "wing_u_r_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 42.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(40.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -18.0F, -9.0F, 0.0F, 0.0F, 0.150F)
      );
      wing_u_r_mid.addOrReplaceChild(
         "wing_u_r_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 38.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(36.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -10.0F, -7.5F, 0.0F, 0.0F, 0.070F)
      );
      wing_u_r_mid.addOrReplaceChild(
         "wing_u_r_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 35.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(33.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -2.0F, -6.0F, 0.0F, 0.0F, -0.010F)
      );
      wing_u_r_mid.addOrReplaceChild(
         "wing_u_r_feather_3",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 32.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(30.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 6.0F, -4.5F, 0.0F, 0.0F, -0.090F)
      );
      wing_u_r_mid.addOrReplaceChild(
         "wing_u_r_feather_4",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 29.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(27.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 14.0F, -3.0F, 0.0F, 0.0F, -0.170F)
      );
      PartDefinition wing_u_r_tip = wing_u_r_mid.addOrReplaceChild(
         "wing_u_r_tip",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(0.0F, -4.0F, -8.0F, 50.0F, 8.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -5.0F, -10.0F, 50.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(69.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F)
      );
      wing_u_r_tip.addOrReplaceChild(
         "wing_u_r_tip_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 28.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -8.0F, -2.0F, 0.0F, 0.0F, -0.1F)
      );
      wing_u_r_tip.addOrReplaceChild(
         "wing_u_r_tip_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 24.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1F)
      );
      wing_u_r_tip.addOrReplaceChild(
         "wing_u_r_tip_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 20.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, 6.0F, 2.0F, 0.0F, 0.0F, -0.1F)
      );

      PartDefinition wing_m_l_root = body.addOrReplaceChild(
         "wing_m_l_root",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.WING_ROOT_U, HemelwachterAtlas.WING_ROOT_V)
            .addBox(-8.0F, -6.0F, -8.0F, 20.0F, 12.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-2.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F, none),
         PartPose.offsetAndRotation(22.0F, -96.0F, 2.0F, -0.15F, 0.9F, 0.55F)
      );
      PartDefinition wing_m_l_mid = wing_m_l_root.addOrReplaceChild(
         "wing_m_l_mid",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(0.0F, -5.0F, -10.0F, 80.0F, 10.0F, 20.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -6.0F, -12.0F, 80.0F, 3.0F, 4.0F, none),
         PartPose.offset(10.0F, 0.0F, 0.0F)
      );
      wing_m_l_mid.addOrReplaceChild(
         "wing_m_l_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 46.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(44.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -18.0F, -9.0F, 0.0F, 0.0F, -0.150F)
      );
      wing_m_l_mid.addOrReplaceChild(
         "wing_m_l_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 42.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(40.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -10.0F, -7.5F, 0.0F, 0.0F, -0.070F)
      );
      wing_m_l_mid.addOrReplaceChild(
         "wing_m_l_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 39.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(37.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -2.0F, -6.0F, 0.0F, 0.0F, 0.010F)
      );
      wing_m_l_mid.addOrReplaceChild(
         "wing_m_l_feather_3",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 35.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(33.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 6.0F, -4.5F, 0.0F, 0.0F, 0.090F)
      );
      wing_m_l_mid.addOrReplaceChild(
         "wing_m_l_feather_4",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 32.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(30.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 14.0F, -3.0F, 0.0F, 0.0F, 0.170F)
      );
      PartDefinition wing_m_l_tip = wing_m_l_mid.addOrReplaceChild(
         "wing_m_l_tip",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(0.0F, -4.0F, -8.0F, 55.0F, 8.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -5.0F, -10.0F, 55.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(76.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F)
      );
      wing_m_l_tip.addOrReplaceChild(
         "wing_m_l_tip_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 28.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -8.0F, -2.0F, 0.0F, 0.0F, 0.1F)
      );
      wing_m_l_tip.addOrReplaceChild(
         "wing_m_l_tip_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 24.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.1F)
      );
      wing_m_l_tip.addOrReplaceChild(
         "wing_m_l_tip_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 20.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, 6.0F, 2.0F, 0.0F, 0.0F, 0.1F)
      );

      PartDefinition wing_m_r_root = body.addOrReplaceChild(
         "wing_m_r_root",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.WING_ROOT_U, HemelwachterAtlas.WING_ROOT_V)
            .addBox(-12.0F, -6.0F, -8.0F, 20.0F, 12.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-6.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F, none),
         PartPose.offsetAndRotation(-22.0F, -96.0F, 2.0F, -0.15F, -0.9F, -0.55F)
      );
      PartDefinition wing_m_r_mid = wing_m_r_root.addOrReplaceChild(
         "wing_m_r_mid",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(0.0F, -5.0F, -10.0F, 80.0F, 10.0F, 20.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -6.0F, -12.0F, 80.0F, 3.0F, 4.0F, none),
         PartPose.offset(-10.0F, 0.0F, 0.0F)
      );
      wing_m_r_mid.addOrReplaceChild(
         "wing_m_r_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 46.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(44.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -18.0F, -9.0F, 0.0F, 0.0F, 0.150F)
      );
      wing_m_r_mid.addOrReplaceChild(
         "wing_m_r_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 42.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(40.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -10.0F, -7.5F, 0.0F, 0.0F, 0.070F)
      );
      wing_m_r_mid.addOrReplaceChild(
         "wing_m_r_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 39.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(37.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -2.0F, -6.0F, 0.0F, 0.0F, -0.010F)
      );
      wing_m_r_mid.addOrReplaceChild(
         "wing_m_r_feather_3",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 35.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(33.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 6.0F, -4.5F, 0.0F, 0.0F, -0.090F)
      );
      wing_m_r_mid.addOrReplaceChild(
         "wing_m_r_feather_4",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 32.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(30.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 14.0F, -3.0F, 0.0F, 0.0F, -0.170F)
      );
      PartDefinition wing_m_r_tip = wing_m_r_mid.addOrReplaceChild(
         "wing_m_r_tip",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(0.0F, -4.0F, -8.0F, 55.0F, 8.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -5.0F, -10.0F, 55.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(76.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F)
      );
      wing_m_r_tip.addOrReplaceChild(
         "wing_m_r_tip_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 28.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -8.0F, -2.0F, 0.0F, 0.0F, -0.1F)
      );
      wing_m_r_tip.addOrReplaceChild(
         "wing_m_r_tip_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 24.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1F)
      );
      wing_m_r_tip.addOrReplaceChild(
         "wing_m_r_tip_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 20.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, 6.0F, 2.0F, 0.0F, 0.0F, -0.1F)
      );

      PartDefinition wing_l_l_root = body.addOrReplaceChild(
         "wing_l_l_root",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.WING_ROOT_U, HemelwachterAtlas.WING_ROOT_V)
            .addBox(-8.0F, -6.0F, -8.0F, 20.0F, 12.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-2.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F, none),
         PartPose.offsetAndRotation(22.0F, -78.0F, 6.0F, 0.45F, 0.9F, 0.45F)
      );
      PartDefinition wing_l_l_mid = wing_l_l_root.addOrReplaceChild(
         "wing_l_l_mid",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(0.0F, -5.0F, -10.0F, 66.0F, 10.0F, 20.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -6.0F, -12.0F, 66.0F, 3.0F, 4.0F, none),
         PartPose.offset(10.0F, 0.0F, 0.0F)
      );
      wing_l_l_mid.addOrReplaceChild(
         "wing_l_l_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 38.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(36.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -18.0F, -9.0F, 0.0F, 0.0F, -0.150F)
      );
      wing_l_l_mid.addOrReplaceChild(
         "wing_l_l_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 35.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(33.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -10.0F, -7.5F, 0.0F, 0.0F, -0.070F)
      );
      wing_l_l_mid.addOrReplaceChild(
         "wing_l_l_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 32.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(30.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -2.0F, -6.0F, 0.0F, 0.0F, 0.010F)
      );
      wing_l_l_mid.addOrReplaceChild(
         "wing_l_l_feather_3",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 29.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(27.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 6.0F, -4.5F, 0.0F, 0.0F, 0.090F)
      );
      wing_l_l_mid.addOrReplaceChild(
         "wing_l_l_feather_4",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 26.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(24.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 14.0F, -3.0F, 0.0F, 0.0F, 0.170F)
      );
      PartDefinition wing_l_l_tip = wing_l_l_mid.addOrReplaceChild(
         "wing_l_l_tip",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(0.0F, -4.0F, -8.0F, 45.0F, 8.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -5.0F, -10.0F, 45.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(62.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2F)
      );
      wing_l_l_tip.addOrReplaceChild(
         "wing_l_l_tip_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 28.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -8.0F, -2.0F, 0.0F, 0.0F, 0.1F)
      );
      wing_l_l_tip.addOrReplaceChild(
         "wing_l_l_tip_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 24.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.1F)
      );
      wing_l_l_tip.addOrReplaceChild(
         "wing_l_l_tip_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 20.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, 6.0F, 2.0F, 0.0F, 0.0F, 0.1F)
      );

      PartDefinition wing_l_r_root = body.addOrReplaceChild(
         "wing_l_r_root",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.WING_ROOT_U, HemelwachterAtlas.WING_ROOT_V)
            .addBox(-12.0F, -6.0F, -8.0F, 20.0F, 12.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.GOLD_U, HemelwachterAtlas.GOLD_V)
            .addBox(-6.0F, -7.0F, -9.0F, 12.0F, 3.0F, 18.0F, none),
         PartPose.offsetAndRotation(-22.0F, -78.0F, 6.0F, 0.45F, -0.9F, -0.45F)
      );
      PartDefinition wing_l_r_mid = wing_l_r_root.addOrReplaceChild(
         "wing_l_r_mid",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(0.0F, -5.0F, -10.0F, 66.0F, 10.0F, 20.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -6.0F, -12.0F, 66.0F, 3.0F, 4.0F, none),
         PartPose.offset(-10.0F, 0.0F, 0.0F)
      );
      wing_l_r_mid.addOrReplaceChild(
         "wing_l_r_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 38.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(36.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -18.0F, -9.0F, 0.0F, 0.0F, 0.150F)
      );
      wing_l_r_mid.addOrReplaceChild(
         "wing_l_r_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 35.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(33.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -10.0F, -7.5F, 0.0F, 0.0F, 0.070F)
      );
      wing_l_r_mid.addOrReplaceChild(
         "wing_l_r_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 32.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(30.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, -2.0F, -6.0F, 0.0F, 0.0F, -0.010F)
      );
      wing_l_r_mid.addOrReplaceChild(
         "wing_l_r_feather_3",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_BACK_U, HemelwachterAtlas.FEATHER_BACK_V)
            .addBox(4.0F, -3.0F, -3.0F, 29.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(27.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 6.0F, -4.5F, 0.0F, 0.0F, -0.090F)
      );
      wing_l_r_mid.addOrReplaceChild(
         "wing_l_r_feather_4",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_FRONT_U, HemelwachterAtlas.FEATHER_FRONT_V)
            .addBox(4.0F, -3.0F, -3.0F, 26.0F, 6.0F, 8.0F, none)
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(24.0F, -2.0F, -2.0F, 10.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(8.0F, 14.0F, -3.0F, 0.0F, 0.0F, -0.170F)
      );
      PartDefinition wing_l_r_tip = wing_l_r_mid.addOrReplaceChild(
         "wing_l_r_tip",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.FEATHER_EDGE_U, HemelwachterAtlas.FEATHER_EDGE_V)
            .addBox(0.0F, -4.0F, -8.0F, 45.0F, 8.0F, 16.0F, none)
            .texOffs(HemelwachterAtlas.WING_TRIM_U, HemelwachterAtlas.WING_TRIM_V)
            .addBox(0.0F, -5.0F, -10.0F, 45.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(62.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F)
      );
      wing_l_r_tip.addOrReplaceChild(
         "wing_l_r_tip_feather_0",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 28.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -8.0F, -2.0F, 0.0F, 0.0F, -0.1F)
      );
      wing_l_r_tip.addOrReplaceChild(
         "wing_l_r_tip_feather_1",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 24.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.1F)
      );
      wing_l_r_tip.addOrReplaceChild(
         "wing_l_r_tip_feather_2",
         CubeListBuilder.create()
            .texOffs(HemelwachterAtlas.IVORY_U, HemelwachterAtlas.IVORY_V)
            .addBox(0.0F, -2.0F, -2.0F, 20.0F, 4.0F, 6.0F, none),
         PartPose.offsetAndRotation(10.0F, 6.0F, 2.0F, 0.0F, 0.0F, -0.1F)
      );

      return LayerDefinition.create(mesh, HemelwachterAtlas.TEX_W, HemelwachterAtlas.TEX_H);
   }

   @Override
   public void setupAnim(HemelwachterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      boolean provoked = entity.isProvoked();
      this.eyeNeutral.visible = !provoked;
      this.eyeProvoked.visible = provoked;

      float partial = ageInTicks - entity.tickCount;
      float wing = entity.getWingPhase(partial);
      float ring = entity.getRingPhase(partial);
      int anim = entity.getAnimState();
      float ap = entity.getAnimProgress(partial);

      this.ringOuter.yRot = ring;
      this.ringInner.yRot = -ring * 1.35F;
      this.ringInner.xRot = 0.4F + Mth.sin(ring * 0.5F) * 0.05F;
      this.halo.yRot = Mth.sin(ring * 0.7F) * 0.1F;

      // Eye tracks look target
      float yaw = netHeadYaw * ((float)Math.PI / 180F) * 0.35F;
      float pitch = headPitch * ((float)Math.PI / 180F) * 0.35F;
      this.eyeNeutral.yRot = yaw;
      this.eyeNeutral.xRot = pitch;
      this.eyeProvoked.yRot = yaw;
      this.eyeProvoked.xRot = pitch;

      float flap = Mth.sin(wing) * 0.18F;
      float flap2 = Mth.sin(wing + 0.8F) * 0.14F;
      float flap3 = Mth.sin(wing + 1.6F) * 0.12F;
      if (anim == HemelwachterEntity.ANIM_BUFFET) {
         flap += Mth.sin(ap * (float)Math.PI) * 0.55F;
      } else if (anim == HemelwachterEntity.ANIM_VOLLEY) {
         flap2 += Mth.sin(ap * (float)Math.PI * 2.0F) * 0.25F;
      } else if (anim == HemelwachterEntity.ANIM_BEAM) {
         this.core.xRot = Mth.sin(ap * (float)Math.PI) * -0.12F;
      } else {
         this.core.xRot = Mth.sin(wing * 0.5F) * 0.03F;
      }
      if (anim == HemelwachterEntity.ANIM_ARRIVE) {
         this.body.y = 24.0F + (1.0F - ap) * 40.0F;
      } else if (anim == HemelwachterEntity.ANIM_DEPART) {
         this.body.y = 24.0F - ap * 80.0F;
         flap += ap * 0.4F;
      } else if (anim == HemelwachterEntity.ANIM_DEATH) {
         this.body.xRot = ap * 0.6F;
         flap *= (1.0F - ap);
      } else {
         this.body.y = 24.0F + Mth.sin(wing * 0.35F) * 2.0F;
         this.body.xRot = 0.0F;
      }

      // 0 UL 1 UR 2 ML 3 MR 4 LL 5 LR
      poseWing(0, -0.55F, 0.9F, 0.35F, flap);
      poseWing(1, -0.55F, -0.9F, -0.35F, flap);
      poseWing(2, -0.15F, 0.9F, 0.55F, flap2);
      poseWing(3, -0.15F, -0.9F, -0.55F, flap2);
      poseWing(4, 0.45F, 0.9F, 0.45F, flap3);
      poseWing(5, 0.45F, -0.9F, -0.45F, flap3);

      if (anim == HemelwachterEntity.ANIM_PROVOKE) {
         float p = Mth.sin(ap * (float)Math.PI);
         this.ringOuter.xRot = p * 0.25F;
         this.core.yScale = 1.0F + p * 0.08F;
      } else {
         this.ringOuter.xRot = 0.0F;
         this.core.yScale = 1.0F;
         this.core.xScale = 1.0F;
         this.core.zScale = 1.0F;
      }
   }

   private void poseWing(int idx, float basePitch, float baseYaw, float baseRoll, float flap) {
      ModelPart root = this.wingRoots[idx];
      boolean left = idx % 2 == 0;
      float sign = left ? 1.0F : -1.0F;
      root.xRot = basePitch + flap * 0.35F;
      root.yRot = baseYaw;
      root.zRot = baseRoll + flap * 0.55F * sign;
      this.wingMids[idx].zRot = flap * 0.4F * sign;
      this.wingTips[idx].zRot = flap * 0.55F * sign;
      this.wingTips[idx].xRot = -flap * 0.15F;
   }

   @Override
   public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
      this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
   }
}
