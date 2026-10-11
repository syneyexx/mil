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
import org.millenaire.entity.NetherWraithEntity;

/**
 * Production Nether Wraith model — tall skeletal demonic predator.
 * Silhouette-driven geometry: segmented skull, open jaw, swept horns,
 * hollow ribcage with infernal core, long clawed arms, digitigrade legs,
 * and six independently animated back tendrils.
 */
@OnlyIn(Dist.CLIENT)
public class NetherWraithModel extends EntityModel<NetherWraithEntity> {
   private final ModelPart root;
   private final ModelPart pelvis;
   private final ModelPart torso;
   private final ModelPart chest;
   private final ModelPart infernalCore;
   private final ModelPart headRoot;
   private final ModelPart jaw;
   private final ModelPart rightShoulder;
   private final ModelPart rightUpperArm;
   private final ModelPart rightForearm;
   private final ModelPart rightHand;
   private final ModelPart rightClaw1;
   private final ModelPart rightClaw2;
   private final ModelPart rightClaw3;
   private final ModelPart leftShoulder;
   private final ModelPart leftUpperArm;
   private final ModelPart leftForearm;
   private final ModelPart leftHand;
   private final ModelPart leftClaw1;
   private final ModelPart leftClaw2;
   private final ModelPart leftClaw3;
   private final ModelPart rightThigh;
   private final ModelPart rightLowerLeg;
   private final ModelPart rightFoot;
   private final ModelPart leftThigh;
   private final ModelPart leftLowerLeg;
   private final ModelPart leftFoot;
   private final ModelPart[] tendrilRoots = new ModelPart[6];
   private final ModelPart[] tendrilMids = new ModelPart[6];
   private final ModelPart[] tendrilMids2 = new ModelPart[6];
   private final ModelPart[] tendrilTips = new ModelPart[6];

   public NetherWraithModel(ModelPart root) {
      this.root = root;
      this.pelvis = root.getChild("pelvis");
      this.torso = this.pelvis.getChild("torso");
      this.chest = this.torso.getChild("chest");
      this.infernalCore = this.chest.getChild("infernal_core");
      this.headRoot = this.torso.getChild("head_root");
      this.jaw = this.headRoot.getChild("jaw");

      this.rightShoulder = this.torso.getChild("right_shoulder");
      this.rightUpperArm = this.rightShoulder.getChild("right_upper_arm");
      this.rightForearm = this.rightUpperArm.getChild("right_forearm");
      this.rightHand = this.rightForearm.getChild("right_hand");
      this.rightClaw1 = this.rightHand.getChild("right_claw1");
      this.rightClaw2 = this.rightHand.getChild("right_claw2");
      this.rightClaw3 = this.rightHand.getChild("right_claw3");

      this.leftShoulder = this.torso.getChild("left_shoulder");
      this.leftUpperArm = this.leftShoulder.getChild("left_upper_arm");
      this.leftForearm = this.leftUpperArm.getChild("left_forearm");
      this.leftHand = this.leftForearm.getChild("left_hand");
      this.leftClaw1 = this.leftHand.getChild("left_claw1");
      this.leftClaw2 = this.leftHand.getChild("left_claw2");
      this.leftClaw3 = this.leftHand.getChild("left_claw3");

      this.rightThigh = this.pelvis.getChild("right_thigh");
      this.rightLowerLeg = this.rightThigh.getChild("right_lower_leg");
      this.rightFoot = this.rightLowerLeg.getChild("right_foot");
      this.leftThigh = this.pelvis.getChild("left_thigh");
      this.leftLowerLeg = this.leftThigh.getChild("left_lower_leg");
      this.leftFoot = this.leftLowerLeg.getChild("left_foot");

      for (int i = 0; i < 6; i++) {
         String side = i < 3 ? "l" : "r";
         int idx = i < 3 ? i + 1 : i - 2;
         ModelPart rootTendril = this.torso.getChild("tendril_" + side + idx + "_root");
         this.tendrilRoots[i] = rootTendril;
         ModelPart mid = rootTendril.getChild("tendril_" + side + idx + "_mid");
         this.tendrilMids[i] = mid;
         ModelPart mid2 = mid.getChild("tendril_" + side + idx + "_mid2");
         this.tendrilMids2[i] = mid2;
         this.tendrilTips[i] = mid2.getChild("tendril_" + side + idx + "_tip");
      }
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      // Feet at y≈52 → ~3.25 blocks tall. y increases downward in ModelPart space.
      PartDefinition pelvis = root.addOrReplaceChild(
         "pelvis",
         CubeListBuilder.create()
            .texOffs(48, 30)
            .addBox(-3.5F, -2.0F, -3.0F, 7.0F, 4.0F, 6.0F, none)
            .texOffs(54, 30)
            .addBox(-1.5F, -3.0F, -1.5F, 3.0F, 2.0F, 3.0F, none),
         PartPose.offset(0.0F, 38.0F, 0.0F)
      );

      PartDefinition torso = pelvis.addOrReplaceChild(
         "torso",
         CubeListBuilder.create()
            .texOffs(16, 16)
            .addBox(-1.5F, -17.0F, -1.5F, 3.0F, 15.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.28F, 0.0F, 0.0F)
      );

      PartDefinition chest = torso.addOrReplaceChild(
         "chest",
         CubeListBuilder.create()
            .texOffs(24, 16)
            .addBox(-2.0F, -16.0F, -2.0F, 4.0F, 3.0F, 4.0F, none)
            .texOffs(32, 16)
            .addBox(-5.5F, -15.5F, -1.5F, 3.0F, 3.0F, 3.0F, none)
            .texOffs(40, 16)
            .addBox(2.5F, -15.5F, -1.5F, 3.0F, 3.0F, 3.0F, none)
            .texOffs(16, 24)
            .addBox(-1.0F, -14.0F, -3.5F, 2.0F, 10.0F, 2.0F, none),
         PartPose.offset(0.0F, 0.0F, 0.0F)
      );

      // Segmented vertebrae along the spine (silhouette read at distance)
      for (int v = 0; v < 5; v++) {
         chest.addOrReplaceChild(
            "vertebra_" + v,
            CubeListBuilder.create().texOffs(48, 32).addBox(-1.75F, -1.0F, -1.75F, 3.5F, 2.0F, 3.5F, none),
            PartPose.offset(0.0F, -14.5F + v * 2.4F, 0.8F)
         );
      }
      chest.addOrReplaceChild(
         "scapula_left",
         CubeListBuilder.create().texOffs(32, 24).addBox(0.0F, -1.5F, -1.0F, 5.0F, 3.0F, 2.0F, none),
         PartPose.offsetAndRotation(1.5F, -15.0F, 2.0F, -0.2F, 0.35F, -0.25F)
      );
      chest.addOrReplaceChild(
         "scapula_right",
         CubeListBuilder.create().texOffs(40, 24).addBox(-5.0F, -1.5F, -1.0F, 5.0F, 3.0F, 2.0F, none),
         PartPose.offsetAndRotation(-1.5F, -15.0F, 2.0F, -0.2F, -0.35F, 0.25F)
      );

      // Hollow ribcage — individual ribs with visible gaps
      addRib(chest, "rib_l1", 2.0F, -13.5F, 0.0F, -0.15F, -0.55F, -0.35F, 30, 12);
      addRib(chest, "rib_l2", 2.2F, -11.5F, 0.2F, 0.0F, -0.5F, -0.25F, 34, 12);
      addRib(chest, "rib_l3", 2.0F, -9.5F, 0.3F, 0.1F, -0.45F, -0.2F, 30, 12);
      addRib(chest, "rib_l4", 1.6F, -7.5F, 0.2F, 0.2F, -0.4F, -0.15F, 34, 12);
      addRib(chest, "rib_r1", -2.0F, -13.5F, 0.0F, -0.15F, 0.55F, 0.35F, 30, 16);
      addRib(chest, "rib_r2", -2.2F, -11.5F, 0.2F, 0.0F, 0.5F, 0.25F, 34, 16);
      addRib(chest, "rib_r3", -2.0F, -9.5F, 0.3F, 0.1F, 0.45F, 0.2F, 32, 16);
      addRib(chest, "rib_r4", -1.6F, -7.5F, 0.2F, 0.2F, 0.4F, 0.15F, 34, 16);

      chest.addOrReplaceChild(
         "infernal_core",
         CubeListBuilder.create()
            .texOffs(8, 22)
            .addBox(-1.5F, -2.5F, -1.5F, 3.0F, 5.0F, 3.0F, none)
            .texOffs(12, 22)
            .addBox(-1.0F, -3.5F, -1.0F, 2.0F, 2.0F, 2.0F, none)
            .texOffs(16, 22)
            .addBox(-0.5F, 2.0F, -0.5F, 1.0F, 2.0F, 1.0F, none),
         PartPose.offset(0.0F, -11.0F, 0.0F)
      );

      torso.addOrReplaceChild(
         "neck",
         CubeListBuilder.create()
            .texOffs(16, 30)
            .addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F, none)
            .texOffs(24, 30)
            .addBox(-1.0F, -5.0F, -1.0F, 2.0F, 2.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, -17.0F, -0.5F, 0.35F, 0.0F, 0.0F)
      );

      buildHead(torso);
      buildArm(torso, true);
      buildArm(torso, false);
      buildTendrils(torso);
      buildLeg(pelvis, true);
      buildLeg(pelvis, false);

      return LayerDefinition.create(mesh, 64, 64);
   }

   private static void addRib(PartDefinition chest, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v) {
      PartDefinition rib = chest.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      rib.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(u, v + 4).addBox(0.0F, -0.75F, -0.75F, 4.0F, 1.5F, 1.5F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(4.5F, 0.0F, 0.0F, 0.0F, 0.0F, name.contains("_l") ? 0.55F : -0.55F)
      );
   }

   private static void buildHead(PartDefinition torso) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition head = torso.addOrReplaceChild(
         "head_root",
         CubeListBuilder.create(),
         PartPose.offsetAndRotation(0.0F, -17.5F, -1.5F, -0.2F, 0.0F, 0.0F)
      );

      // Cranium / skull mass
      head.addOrReplaceChild(
         "cranium",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.5F, -10.0F, -6.0F, 11.0F, 8.0F, 12.0F, none)
            .texOffs(8, 0)
            .addBox(-4.5F, -11.5F, -4.0F, 9.0F, 2.0F, 9.0F, none),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "brow_left",
         CubeListBuilder.create().texOffs(0, 12).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 2.0F, 4.0F, none),
         PartPose.offsetAndRotation(3.2F, -7.5F, -6.5F, -0.15F, -0.2F, 0.1F)
      );
      head.addOrReplaceChild(
         "brow_right",
         CubeListBuilder.create().texOffs(8, 12).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 2.0F, 4.0F, none),
         PartPose.offsetAndRotation(-3.2F, -7.5F, -6.5F, -0.15F, 0.2F, -0.1F)
      );
      head.addOrReplaceChild(
         "muzzle_top",
         CubeListBuilder.create()
            .texOffs(16, 0)
            .addBox(-3.5F, -2.0F, -8.0F, 7.0F, 3.0F, 8.0F, none),
         PartPose.offset(0.0F, -5.0F, -5.5F)
      );
      head.addOrReplaceChild(
         "muzzle_left",
         CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.5F, -5.0F, 2.0F, 3.0F, 6.0F, none),
         PartPose.offsetAndRotation(3.8F, -3.5F, -7.0F, 0.1F, -0.25F, 0.0F)
      );
      head.addOrReplaceChild(
         "muzzle_right",
         CubeListBuilder.create().texOffs(8, 20).addBox(-1.0F, -1.5F, -5.0F, 2.0F, 3.0F, 6.0F, none),
         PartPose.offsetAndRotation(-3.8F, -3.5F, -7.0F, 0.1F, 0.25F, 0.0F)
      );
      head.addOrReplaceChild(
         "cheek_left",
         CubeListBuilder.create().texOffs(16, 12).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, none),
         PartPose.offset(5.0F, -4.0F, -2.0F)
      );
      head.addOrReplaceChild(
         "cheek_right",
         CubeListBuilder.create().texOffs(24, 12).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, none),
         PartPose.offset(-5.0F, -4.0F, -2.0F)
      );
      head.addOrReplaceChild(
         "teeth_upper",
         CubeListBuilder.create()
            .texOffs(32, 0)
            .addBox(-3.0F, 0.0F, -7.5F, 1.0F, 3.0F, 1.0F, none)
            .texOffs(34, 0)
            .addBox(-1.0F, 0.0F, -8.0F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(36, 0)
            .addBox(1.0F, 0.0F, -8.0F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(38, 0)
            .addBox(2.5F, 0.0F, -7.5F, 1.0F, 3.0F, 1.0F, none),
         PartPose.offset(0.0F, -3.0F, -6.0F)
      );
      head.addOrReplaceChild(
         "mouth_cavity",
         CubeListBuilder.create().texOffs(8, 6).addBox(-3.0F, -2.0F, -6.0F, 6.0F, 4.0F, 6.0F, none),
         PartPose.offset(0.0F, -2.0F, -6.0F)
      );
      head.addOrReplaceChild(
         "eye_left",
         CubeListBuilder.create().texOffs(24, 4).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.5F, 1.0F, none),
         PartPose.offset(2.8F, -6.8F, -6.6F)
      );
      head.addOrReplaceChild(
         "eye_right",
         CubeListBuilder.create().texOffs(26, 4).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.5F, 1.0F, none),
         PartPose.offset(-2.8F, -6.8F, -6.6F)
      );

      // Articulated jaw — default slightly open
      PartDefinition jaw = head.addOrReplaceChild(
         "jaw",
         CubeListBuilder.create()
            .texOffs(0, 28)
            .addBox(-3.5F, 0.0F, -9.0F, 7.0F, 2.5F, 10.0F, none)
            .texOffs(32, 4)
            .addBox(-3.0F, -1.5F, -8.5F, 1.0F, 2.0F, 1.0F, none)
            .texOffs(34, 4)
            .addBox(-1.0F, -2.0F, -9.0F, 1.0F, 2.5F, 1.0F, none)
            .texOffs(36, 4)
            .addBox(1.0F, -2.0F, -9.0F, 1.0F, 2.5F, 1.0F, none)
            .texOffs(38, 4)
            .addBox(2.5F, -1.5F, -8.5F, 1.0F, 2.0F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, -2.0F, -4.0F, 0.45F, 0.0F, 0.0F)
      );
      jaw.addOrReplaceChild(
         "jaw_glow",
         CubeListBuilder.create().texOffs(8, 20).addBox(-2.5F, -1.0F, -7.0F, 5.0F, 1.5F, 6.0F, none),
         PartPose.offset(0.0F, 0.5F, 0.0F)
      );

      buildHorn(head, true);
      buildHorn(head, false);

      // Secondary skull spikes
      head.addOrReplaceChild(
         "spike_left",
         CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, none),
         PartPose.offsetAndRotation(4.5F, -10.0F, 2.0F, -0.4F, 0.0F, 0.35F)
      );
      head.addOrReplaceChild(
         "spike_right",
         CubeListBuilder.create().texOffs(42, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, none),
         PartPose.offsetAndRotation(-4.5F, -10.0F, 2.0F, -0.4F, 0.0F, -0.35F)
      );
   }

   private static void buildHorn(PartDefinition head, boolean left) {
      CubeDeformation none = CubeDeformation.NONE;
      String prefix = left ? "horn_left" : "horn_right";
      float side = left ? 1.0F : -1.0F;
      PartDefinition h1 = head.addOrReplaceChild(
         prefix + "_1",
         CubeListBuilder.create().texOffs(48, 0).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F, none),
         PartPose.offsetAndRotation(3.5F * side, -10.0F, 1.0F, -0.55F, 0.15F * side, 0.35F * side)
      );
      PartDefinition h2 = h1.addOrReplaceChild(
         prefix + "_2",
         CubeListBuilder.create().texOffs(48, 8).addBox(-1.25F, -5.0F, -1.25F, 2.5F, 5.0F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.55F, 0.1F * side, 0.2F * side)
      );
      PartDefinition h3 = h2.addOrReplaceChild(
         prefix + "_3",
         CubeListBuilder.create().texOffs(48, 16).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.65F, 0.1F * side, 0.15F * side)
      );
      h3.addOrReplaceChild(
         prefix + "_4",
         CubeListBuilder.create().texOffs(52, 0).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.45F, 0.05F * side, 0.1F * side)
      );
   }

   private static void buildArm(PartDefinition torso, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;

      PartDefinition shoulder = torso.addOrReplaceChild(
         side + "_shoulder",
         CubeListBuilder.create().texOffs(0, 40).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, none),
         PartPose.offsetAndRotation(6.5F * s, -15.0F, 0.0F, 0.15F, 0.0F, 0.12F * s)
      );
      PartDefinition upper = shoulder.addOrReplaceChild(
         side + "_upper_arm",
         CubeListBuilder.create().texOffs(8, 40).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 14.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.15F, 0.0F, 0.08F * s)
      );
      PartDefinition forearm = upper.addOrReplaceChild(
         side + "_forearm",
         CubeListBuilder.create()
            .texOffs(16, 40)
            .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 17.0F, 3.0F, none)
            .texOffs(24, 40)
            .addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 13.5F, 0.0F, -0.25F, 0.0F, 0.0F)
      );
      PartDefinition hand = forearm.addOrReplaceChild(
         side + "_hand",
         CubeListBuilder.create().texOffs(32, 40).addBox(-2.5F, 0.0F, -2.0F, 5.0F, 5.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 16.5F, 0.0F, -0.1F, 0.0F, 0.0F)
      );

      addClaw(hand, side + "_claw1", -1.8F, 4.5F, -0.5F, 0.35F, 0.15F * s, 0.2F * s, 48, 48, true);
      addClaw(hand, side + "_claw2", 0.0F, 5.0F, -1.0F, 0.45F, 0.0F, 0.0F, 48, 50, true);
      addClaw(hand, side + "_claw3", 1.8F, 4.5F, -0.5F, 0.35F, -0.15F * s, -0.2F * s, 50, 48, true);
      // Inner thumb-like claw
      addClaw(hand, side + "_thumb", -2.2F * s, 3.0F, 1.0F, 0.2F, 0.6F * s, 0.5F * s, 50, 50, false);
   }

   private static void addClaw(
      PartDefinition hand, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v, boolean longClaw
   ) {
      CubeDeformation none = CubeDeformation.NONE;
      float len1 = longClaw ? 6.0F : 4.0F;
      float len2 = longClaw ? 5.0F : 3.0F;
      PartDefinition base = hand.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(-0.75F, 0.0F, -0.75F, 1.5F, len1, 1.5F, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      PartDefinition mid = base.addOrReplaceChild(
         "mid",
         CubeListBuilder.create().texOffs(u, v + 8).addBox(-0.5F, 0.0F, -0.5F, 1.0F, len2, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, len1 - 0.5F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
      mid.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(50, 15).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 4.0F, 0.8F, none),
         PartPose.offsetAndRotation(0.0F, len2 - 0.3F, 0.0F, 0.4F, 0.0F, 0.0F)
      );
   }

   private static void buildLeg(PartDefinition pelvis, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;

      PartDefinition thigh = pelvis.addOrReplaceChild(
         side + "_thigh",
         CubeListBuilder.create().texOffs(40, 40).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 14.0F, 5.0F, none),
         PartPose.offsetAndRotation(3.2F * s, 1.0F, 0.5F, -0.55F, 0.0F, 0.05F * s)
      );
      PartDefinition lower = thigh.addOrReplaceChild(
         side + "_lower_leg",
         CubeListBuilder.create()
            .texOffs(48, 40)
            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 16.0F, 4.0F, none)
            .texOffs(56, 40)
            .addBox(-2.5F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F, none),
         PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 1.05F, 0.0F, 0.0F)
      );
      PartDefinition foot = lower.addOrReplaceChild(
         side + "_foot",
         CubeListBuilder.create()
            .texOffs(0, 48)
            .addBox(-2.0F, -1.0F, -8.0F, 4.0F, 3.0F, 10.0F, none),
         PartPose.offsetAndRotation(0.0F, 15.0F, 0.5F, -0.5F, 0.0F, 0.0F)
      );
      foot.addOrReplaceChild(
         side + "_toe1",
         CubeListBuilder.create().texOffs(50, 20).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F, none),
         PartPose.offsetAndRotation(-1.4F, 0.5F, -7.5F, 0.15F, 0.2F, 0.0F)
      );
      foot.addOrReplaceChild(
         side + "_toe2",
         CubeListBuilder.create().texOffs(52, 20).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F, none),
         PartPose.offsetAndRotation(0.0F, 0.5F, -7.5F, 0.2F, 0.0F, 0.0F)
      );
      foot.addOrReplaceChild(
         side + "_toe3",
         CubeListBuilder.create().texOffs(54, 20).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F, none),
         PartPose.offsetAndRotation(1.4F, 0.5F, -7.5F, 0.15F, -0.2F, 0.0F)
      );
   }

   private static void buildTendrils(PartDefinition torso) {
      // Left outer / mid / inner, then right outer / mid / inner — varied lengths & angles
      addTendril(torso, "tendril_l1", -3.5F, -14.0F, 2.5F, -0.9F, -0.55F, -0.35F, 11.0F, 10.0F, 9.0F, 0);
      addTendril(torso, "tendril_l2", -2.0F, -13.0F, 3.0F, -1.05F, -0.25F, -0.2F, 10.0F, 9.0F, 8.0F, 1);
      addTendril(torso, "tendril_l3", -0.8F, -12.0F, 3.2F, -0.85F, -0.05F, -0.1F, 8.0F, 7.5F, 7.0F, 2);
      addTendril(torso, "tendril_r1", 3.5F, -14.0F, 2.5F, -0.95F, 0.6F, 0.4F, 11.5F, 10.5F, 9.5F, 3);
      addTendril(torso, "tendril_r2", 2.0F, -13.0F, 3.0F, -1.1F, 0.3F, 0.25F, 10.0F, 9.0F, 8.5F, 4);
      addTendril(torso, "tendril_r3", 0.8F, -12.0F, 3.2F, -0.8F, 0.1F, 0.12F, 8.5F, 8.0F, 7.0F, 5);
   }

   private static void addTendril(
      PartDefinition torso,
      String name,
      float x,
      float y,
      float z,
      float rx,
      float ry,
      float rz,
      float len1,
      float len2,
      float len3,
      int variant
   ) {
      CubeDeformation none = CubeDeformation.NONE;
      // 64x64 atlas: long tendril strips along the right side
      int u = 50 + variant % 3;
      int v = variant % 4;
      float lenMid2 = Math.max(6.0F, len3 - 1.0F);
      PartDefinition rootSeg = torso.addOrReplaceChild(
         name + "_root",
         CubeListBuilder.create().texOffs(u, v).addBox(-1.25F, -1.25F, 0.0F, 2.5F, 2.5F, len1, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      PartDefinition mid = rootSeg.addOrReplaceChild(
         name + "_mid",
         CubeListBuilder.create().texOffs(u, v + 4).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, len2, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, len1 - 0.5F, 0.45F, 0.08F * (variant % 2 == 0 ? 1 : -1), 0.0F)
      );
      PartDefinition mid2 = mid.addOrReplaceChild(
         name + "_mid2",
         CubeListBuilder.create().texOffs(u, v + 8).addBox(-0.85F, -0.85F, 0.0F, 1.7F, 1.7F, lenMid2, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, len2 - 0.4F, 0.55F, 0.06F * (variant % 2 == 0 ? -1 : 1), 0.0F)
      );
      PartDefinition tip = mid2.addOrReplaceChild(
         name + "_tip",
         CubeListBuilder.create()
            .texOffs(50, 0)
            .addBox(-0.6F, -0.6F, 0.0F, 1.2F, 1.2F, len3, none)
            .texOffs(52, 0)
            .addBox(-0.4F, -0.4F, len3 - 0.5F, 0.8F, 0.8F, 4.5F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, lenMid2 - 0.3F, 0.65F, 0.04F * (variant % 2 == 0 ? 1 : -1), 0.0F)
      );
      tip.addOrReplaceChild(
         "glow",
         CubeListBuilder.create().texOffs(54, 0).addBox(-0.35F, -0.35F, 0.0F, 0.7F, 0.7F, 3.5F, none),
         PartPose.offset(0.0F, 0.0F, len3 + 3.5F)
      );
   }

   @Override
   public void setupAnim(NetherWraithEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      // Reset to bind pose defaults each frame via stored rest angles baked into PartPose —
      // we overwrite animated channels every tick below.

      float walk = limbSwingAmount;
      float walkCycle = limbSwing * 0.55F;
      float breathe = Mth.sin(ageInTicks * 0.08F);
      float breatheSlow = Mth.sin(ageInTicks * 0.05F);

      // Idle torso / breathing
      this.pelvis.y = 38.0F + Mth.sin(ageInTicks * 0.1F) * 0.35F;
      this.torso.xRot = 0.28F + breathe * 0.04F;
      this.torso.yRot = breatheSlow * 0.03F;
      this.torso.zRot = Mth.sin(ageInTicks * 0.07F) * 0.02F;
      this.chest.xRot = breathe * 0.03F;
      this.infernalCore.xScale = 1.0F + breathe * 0.08F;
      this.infernalCore.yScale = 1.0F + breathe * 0.12F;
      this.infernalCore.zScale = 1.0F + breathe * 0.08F;

      // Head tracking + occasional twitch
      float twitch = 0.0F;
      if ((int)(ageInTicks / 40.0F) % 7 == 0) {
         twitch = Mth.sin(ageInTicks * 1.8F) * 0.08F;
      }
      this.headRoot.yRot = netHeadYaw * ((float)Math.PI / 180F) * 0.85F + twitch;
      this.headRoot.xRot = -0.2F + headPitch * ((float)Math.PI / 180F) * 0.7F + breathe * 0.02F;
      this.headRoot.zRot = twitch * 0.5F;

      // Jaw idle flutter
      float jawIdle = 0.45F + Mth.sin(ageInTicks * 0.12F) * 0.06F + Mth.sin(ageInTicks * 0.37F) * 0.03F;
      this.jaw.xRot = jawIdle;

      // Predatory walk — long stalking strides, arms hang low with lag
      float stride = Mth.cos(walkCycle) * walk;
      float strideOpp = Mth.cos(walkCycle + (float)Math.PI) * walk;
      float bodyBob = Mth.sin(walkCycle * 2.0F) * walk * 0.6F;

      this.pelvis.y += bodyBob * 0.4F;
      this.torso.xRot += walk * 0.12F;
      this.torso.yRot += stride * 0.08F;
      this.torso.zRot += stride * 0.05F;

      this.rightThigh.xRot = -0.55F + stride * 0.9F;
      this.leftThigh.xRot = -0.55F + strideOpp * 0.9F;
      this.rightLowerLeg.xRot = 1.05F + Mth.clamp(-stride, 0.0F, 1.0F) * 0.55F;
      this.leftLowerLeg.xRot = 1.05F + Mth.clamp(-strideOpp, 0.0F, 1.0F) * 0.55F;
      this.rightFoot.xRot = -0.5F - stride * 0.35F;
      this.leftFoot.xRot = -0.5F - strideOpp * 0.35F;

      // Arms hang low; claws lag behind stride
      float armHang = -0.15F - walk * 0.25F;
      float clawLag = Mth.cos(walkCycle - 0.4F) * walk;
      this.rightShoulder.xRot = 0.15F + armHang + clawLag * 0.35F;
      this.leftShoulder.xRot = 0.15F + armHang - clawLag * 0.35F;
      this.rightShoulder.zRot = -0.12F + breathe * 0.02F;
      this.leftShoulder.zRot = 0.12F - breathe * 0.02F;
      this.rightUpperArm.xRot = -0.15F + clawLag * 0.25F;
      this.leftUpperArm.xRot = -0.15F - clawLag * 0.25F;
      this.rightForearm.xRot = -0.25F - walk * 0.1F;
      this.leftForearm.xRot = -0.25F - walk * 0.1F;

      // Finger twitch
      float finger = Mth.sin(ageInTicks * 0.25F) * 0.08F;
      this.rightClaw1.xRot = 0.35F + finger;
      this.rightClaw2.xRot = 0.45F - finger * 0.7F;
      this.rightClaw3.xRot = 0.35F + finger * 0.5F;
      this.leftClaw1.xRot = 0.35F - finger;
      this.leftClaw2.xRot = 0.45F + finger * 0.7F;
      this.leftClaw3.xRot = 0.35F - finger * 0.5F;

      // Six tendrils — phase-offset independent sway
      float[] baseX = {-0.9F, -1.05F, -0.85F, -0.95F, -1.1F, -0.8F};
      float[] baseY = {-0.55F, -0.25F, -0.05F, 0.6F, 0.3F, 0.1F};
      float[] baseZ = {-0.35F, -0.2F, -0.1F, 0.4F, 0.25F, 0.12F};
      float[] speed = {0.09F, 0.13F, 0.17F, 0.08F, 0.14F, 0.16F};
      float[] amp = {0.18F, 0.14F, 0.1F, 0.2F, 0.15F, 0.11F};
      float[] phase = {0.0F, 1.7F, 3.1F, 0.9F, 2.4F, 4.0F};
      for (int i = 0; i < 6; i++) {
         float t = ageInTicks * speed[i] + phase[i];
         float sway = Mth.sin(t) * amp[i];
         float sway2 = Mth.cos(t * 1.3F + 0.5F) * amp[i] * 0.7F;
         this.tendrilRoots[i].xRot = baseX[i] + sway + walk * 0.08F;
         this.tendrilRoots[i].yRot = baseY[i] + sway2 * 0.5F;
         this.tendrilRoots[i].zRot = baseZ[i] + sway2 * 0.35F;
         this.tendrilMids[i].xRot = 0.45F + Mth.sin(t + 0.8F) * amp[i] * 0.9F;
         this.tendrilMids[i].yRot = 0.08F * (i % 2 == 0 ? 1 : -1) + sway2 * 0.3F;
         this.tendrilMids2[i].xRot = 0.55F + Mth.sin(t + 1.2F) * amp[i] * 1.0F;
         this.tendrilMids2[i].yRot = 0.06F * (i % 2 == 0 ? -1 : 1) + sway2 * 0.25F;
         this.tendrilTips[i].xRot = 0.65F + Mth.sin(t + 1.6F) * amp[i] * 1.1F;
         this.tendrilTips[i].zRot = Mth.cos(t + 1.1F) * amp[i] * 0.4F;
      }

      // Attack animations
      float attack = entity.getMeleeAttackProgress(0.0F);
      int attackType = entity.getMeleeAttackType();
      if (attack > 0.0F) {
         if (attackType == 1) {
            // Bite / lunge
            float wind = smoothStep(attack, 0.0F, 0.35F);
            float strike = smoothStep(attack, 0.35F, 0.65F);
            float recover = smoothStep(attack, 0.65F, 1.0F);
            this.headRoot.xRot += -0.35F * wind + 0.55F * strike - 0.15F * recover;
            this.jaw.xRot = jawIdle + 0.7F * wind + 0.15F * strike - 0.4F * recover;
            this.torso.xRot += 0.35F * strike;
            this.pelvis.z = -2.0F * strike;
         } else {
            // Claw swipe
            float wind = smoothStep(attack, 0.0F, 0.3F);
            float strike = Mth.sin(smoothStep(attack, 0.3F, 0.7F) * (float)Math.PI);
            float recover = smoothStep(attack, 0.7F, 1.0F);
            this.rightShoulder.xRot = 0.15F - 1.1F * wind - 1.6F * strike + 0.3F * recover;
            this.rightShoulder.yRot = -0.6F * wind - 0.9F * strike;
            this.rightShoulder.zRot = -0.12F - 0.5F * wind;
            this.rightForearm.xRot = -0.25F - 0.4F * wind - 0.8F * strike;
            this.rightClaw1.xRot = 0.35F + 0.4F * wind;
            this.rightClaw2.xRot = 0.45F + 0.5F * wind;
            this.rightClaw3.xRot = 0.35F + 0.4F * wind;
            this.torso.yRot += -0.35F * wind - 0.55F * strike + 0.2F * recover;
            this.torso.xRot += 0.2F * strike;
            this.jaw.xRot = jawIdle + 0.25F * strike;
         }
      } else {
         this.rightShoulder.yRot = 0.0F;
         this.pelvis.z = 0.0F;
      }

      // Hurt recoil
      float hurt = entity.getHurtReaction(0.0F);
      if (hurt > 0.0F) {
         float h = Mth.sin(hurt * (float)Math.PI);
         this.torso.xRot -= 0.25F * h;
         this.headRoot.zRot += 0.35F * h;
         this.jaw.xRot += 0.4F * h;
         for (int i = 0; i < 6; i++) {
            this.tendrilRoots[i].zRot += (i < 3 ? -0.45F : 0.45F) * h;
            this.tendrilRoots[i].xRot -= 0.25F * h;
         }
      }

      // Death collapse
      float death = entity.getDeathReaction(0.0F);
      if (death > 0.0F) {
         float d = Mth.clamp(death, 0.0F, 1.0F);
         this.torso.xRot = 0.28F + 0.9F * d;
         this.pelvis.y = 38.0F + 6.0F * d;
         this.jaw.xRot = 0.45F + 0.7F * d;
         this.rightThigh.xRot = -0.55F - 0.4F * d;
         this.leftThigh.xRot = -0.55F - 0.3F * d;
         this.rightLowerLeg.xRot = 1.05F - 0.5F * d;
         this.leftLowerLeg.xRot = 1.05F - 0.45F * d;
         this.infernalCore.xScale = 1.0F - 0.7F * d;
         this.infernalCore.yScale = 1.0F - 0.7F * d;
         this.infernalCore.zScale = 1.0F - 0.7F * d;
         for (int i = 0; i < 6; i++) {
            this.tendrilRoots[i].xRot = baseX[i] + 0.8F * d;
            this.tendrilMids[i].xRot = 0.45F + 0.6F * d;
            this.tendrilMids2[i].xRot = 0.55F + 0.55F * d;
            this.tendrilTips[i].xRot = 0.65F + 0.5F * d;
         }
      }
   }

   private static float smoothStep(float t, float a, float b) {
      if (t <= a) {
         return 0.0F;
      }
      if (t >= b) {
         return 1.0F;
      }
      float x = (t - a) / (b - a);
      return x * x * (3.0F - 2.0F * x);
   }

   @Override
   public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
      this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
   }
}
