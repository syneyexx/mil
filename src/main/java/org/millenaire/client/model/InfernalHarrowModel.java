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
import org.millenaire.entity.InfernalHarrowEntity;

/**
 * Production Infernal Harrow model — flying skeletal demonic predator.
 * No legs; segmented tail, huge torn bat wings, exposed ribcage with
 * infernal core, elongated skull with independent jaw and swept horns.
 * Texture 256x256; emissive parts use texOffs >= 200.
 */
@OnlyIn(Dist.CLIENT)
public class InfernalHarrowModel extends EntityModel<InfernalHarrowEntity> {
   private final ModelPart root;
   private final ModelPart bodyRoot;
   private final ModelPart spine;
   private final ModelPart sternum;
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
   private final ModelPart wingRootRight;
   private final ModelPart wingUpperRight;
   private final ModelPart wingMidRight;
   private final ModelPart wingOuterRight;
   private final ModelPart wingRootLeft;
   private final ModelPart wingUpperLeft;
   private final ModelPart wingMidLeft;
   private final ModelPart wingOuterLeft;
   private final ModelPart pelvis;
   private final ModelPart tail1;
   private final ModelPart tail2;
   private final ModelPart tail3;
   private final ModelPart tail4;
   private final ModelPart tail5;
   private final ModelPart tail6;
   private final ModelPart tailTip;

   public InfernalHarrowModel(ModelPart root) {
      this.root = root;
      this.bodyRoot = root.getChild("body_root");
      this.spine = this.bodyRoot.getChild("spine");
      this.sternum = this.spine.getChild("sternum");
      this.infernalCore = this.spine.getChild("infernal_core");
      this.headRoot = this.spine.getChild("head_root");
      this.jaw = this.headRoot.getChild("jaw");

      this.rightShoulder = this.spine.getChild("right_shoulder");
      this.rightUpperArm = this.rightShoulder.getChild("right_upper_arm");
      this.rightForearm = this.rightUpperArm.getChild("right_forearm");
      this.rightHand = this.rightForearm.getChild("right_hand");
      this.rightClaw1 = this.rightHand.getChild("right_claw1");
      this.rightClaw2 = this.rightHand.getChild("right_claw2");
      this.rightClaw3 = this.rightHand.getChild("right_claw3");

      this.leftShoulder = this.spine.getChild("left_shoulder");
      this.leftUpperArm = this.leftShoulder.getChild("left_upper_arm");
      this.leftForearm = this.leftUpperArm.getChild("left_forearm");
      this.leftHand = this.leftForearm.getChild("left_hand");
      this.leftClaw1 = this.leftHand.getChild("left_claw1");
      this.leftClaw2 = this.leftHand.getChild("left_claw2");
      this.leftClaw3 = this.leftHand.getChild("left_claw3");

      this.wingRootRight = this.spine.getChild("wing_root_right");
      this.wingUpperRight = this.wingRootRight.getChild("wing_upper_right");
      this.wingMidRight = this.wingUpperRight.getChild("wing_mid_right");
      this.wingOuterRight = this.wingMidRight.getChild("wing_outer_right");

      this.wingRootLeft = this.spine.getChild("wing_root_left");
      this.wingUpperLeft = this.wingRootLeft.getChild("wing_upper_left");
      this.wingMidLeft = this.wingUpperLeft.getChild("wing_mid_left");
      this.wingOuterLeft = this.wingMidLeft.getChild("wing_outer_left");

      this.pelvis = this.bodyRoot.getChild("pelvis");
      this.tail1 = this.pelvis.getChild("tail_1");
      this.tail2 = this.tail1.getChild("tail_2");
      this.tail3 = this.tail2.getChild("tail_3");
      this.tail4 = this.tail3.getChild("tail_4");
      this.tail5 = this.tail4.getChild("tail_5");
      this.tail6 = this.tail5.getChild("tail_6");
      this.tailTip = this.tail6.getChild("tail_tip");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      // Hovering body: head ~y0, hanging tail tip ~y42 → ~2.6 blocks tall.
      PartDefinition bodyRoot = root.addOrReplaceChild(
         "body_root",
         CubeListBuilder.create(),
         PartPose.offset(0.0F, 22.0F, 0.0F)
      );

      PartDefinition spine = bodyRoot.addOrReplaceChild(
         "spine",
         CubeListBuilder.create()
            .texOffs(0, 80)
            .addBox(-1.5F, -12.0F, -1.5F, 3.0F, 14.0F, 3.0F, none)
            .texOffs(12, 80)
            .addBox(-2.0F, -11.0F, -0.5F, 4.0F, 2.0F, 2.0F, none)
            .texOffs(12, 84)
            .addBox(-2.0F, -7.0F, -0.5F, 4.0F, 2.0F, 2.0F, none)
            .texOffs(12, 88)
            .addBox(-1.75F, -3.0F, -0.5F, 3.5F, 2.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.22F, 0.0F, 0.0F)
      );

      PartDefinition sternum = spine.addOrReplaceChild(
         "sternum",
         CubeListBuilder.create()
            .texOffs(28, 80)
            .addBox(-1.0F, -10.0F, -3.5F, 2.0F, 10.0F, 2.0F, none)
            .texOffs(36, 80)
            .addBox(-1.5F, -11.0F, -2.5F, 3.0F, 2.0F, 2.0F, none),
         PartPose.offset(0.0F, 0.0F, 0.0F)
      );
      sternum.addOrReplaceChild(
         "scapula_left",
         CubeListBuilder.create().texOffs(48, 80).addBox(0.0F, -1.5F, -1.0F, 5.0F, 3.0F, 2.0F, none),
         PartPose.offsetAndRotation(1.5F, -10.5F, 2.0F, -0.2F, 0.4F, -0.3F)
      );
      sternum.addOrReplaceChild(
         "scapula_right",
         CubeListBuilder.create().texOffs(62, 80).addBox(-5.0F, -1.5F, -1.0F, 5.0F, 3.0F, 2.0F, none),
         PartPose.offsetAndRotation(-1.5F, -10.5F, 2.0F, -0.2F, -0.4F, 0.3F)
      );

      // Hollow ribcage — open front so infernal core reads through gaps
      addRib(spine, "rib_l1", 2.0F, -10.0F, 0.0F, -0.1F, -0.55F, -0.4F, 0, 100);
      addRib(spine, "rib_l2", 2.2F, -8.0F, 0.2F, 0.0F, -0.5F, -0.3F, 16, 100);
      addRib(spine, "rib_l3", 2.0F, -6.0F, 0.3F, 0.08F, -0.45F, -0.22F, 32, 100);
      addRib(spine, "rib_l4", 1.7F, -4.0F, 0.2F, 0.15F, -0.4F, -0.15F, 48, 100);
      addRib(spine, "rib_l5", 1.4F, -2.2F, 0.1F, 0.22F, -0.35F, -0.1F, 64, 100);
      addRib(spine, "rib_r1", -2.0F, -10.0F, 0.0F, -0.1F, 0.55F, 0.4F, 0, 108);
      addRib(spine, "rib_r2", -2.2F, -8.0F, 0.2F, 0.0F, 0.5F, 0.3F, 16, 108);
      addRib(spine, "rib_r3", -2.0F, -6.0F, 0.3F, 0.08F, 0.45F, 0.22F, 32, 108);
      addRib(spine, "rib_r4", -1.7F, -4.0F, 0.2F, 0.15F, 0.4F, 0.15F, 48, 108);
      addRib(spine, "rib_r5", -1.4F, -2.2F, 0.1F, 0.22F, 0.35F, 0.1F, 64, 108);

      spine.addOrReplaceChild(
         "infernal_core",
         CubeListBuilder.create()
            .texOffs(200, 0)
            .addBox(-2.0F, -3.0F, -2.0F, 4.0F, 6.0F, 4.0F, none)
            .texOffs(216, 0)
            .addBox(-1.5F, -4.0F, -1.5F, 3.0F, 2.0F, 3.0F, none)
            .texOffs(228, 0)
            .addBox(-1.0F, 2.5F, -1.0F, 2.0F, 2.5F, 2.0F, none)
            .texOffs(236, 0)
            .addBox(-0.5F, -1.0F, -2.5F, 1.0F, 2.0F, 1.0F, none)
            .texOffs(240, 0)
            .addBox(-0.5F, -1.0F, 1.5F, 1.0F, 2.0F, 1.0F, none),
         PartPose.offset(0.0F, -6.5F, 0.0F)
      );

      buildHead(spine);
      buildArm(spine, true);
      buildArm(spine, false);
      buildWing(spine, true);
      buildWing(spine, false);
      buildTail(bodyRoot);

      return LayerDefinition.create(mesh, 256, 256);
   }

   private static void addRib(PartDefinition parent, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v) {
      PartDefinition rib = parent.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(0.0F, -1.0F, -1.0F, 5.5F, 2.0F, 2.0F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      rib.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(u, v + 4).addBox(0.0F, -0.75F, -0.75F, 4.0F, 1.5F, 1.5F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, 0.0F, name.contains("_l") ? 0.6F : -0.6F)
      );
   }

   private static void buildHead(PartDefinition spine) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition head = spine.addOrReplaceChild(
         "head_root",
         CubeListBuilder.create(),
         PartPose.offsetAndRotation(0.0F, -12.5F, -1.0F, -0.15F, 0.0F, 0.0F)
      );

      head.addOrReplaceChild(
         "cranium",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0F, -9.0F, -5.5F, 10.0F, 7.0F, 11.0F, none)
            .texOffs(42, 0)
            .addBox(-4.0F, -10.5F, -3.5F, 8.0F, 2.0F, 8.0F, none),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "forehead",
         CubeListBuilder.create()
            .texOffs(0, 18)
            .addBox(-4.5F, -2.0F, -2.0F, 9.0F, 3.0F, 4.0F, none)
            .texOffs(26, 18)
            .addBox(-3.0F, -3.0F, -1.5F, 6.0F, 1.5F, 3.0F, none),
         PartPose.offset(0.0F, -8.5F, -4.0F)
      );
      head.addOrReplaceChild(
         "brow_left",
         CubeListBuilder.create().texOffs(0, 26).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 2.0F, 4.0F, none),
         PartPose.offsetAndRotation(3.0F, -6.8F, -6.0F, -0.2F, -0.2F, 0.12F)
      );
      head.addOrReplaceChild(
         "brow_right",
         CubeListBuilder.create().texOffs(14, 26).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 2.0F, 4.0F, none),
         PartPose.offsetAndRotation(-3.0F, -6.8F, -6.0F, -0.2F, 0.2F, -0.12F)
      );
      head.addOrReplaceChild(
         "cheek_left",
         CubeListBuilder.create().texOffs(28, 26).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, none),
         PartPose.offset(4.8F, -3.5F, -1.5F)
      );
      head.addOrReplaceChild(
         "cheek_right",
         CubeListBuilder.create().texOffs(40, 26).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 4.0F, 4.0F, none),
         PartPose.offset(-4.8F, -3.5F, -1.5F)
      );
      head.addOrReplaceChild(
         "muzzle",
         CubeListBuilder.create()
            .texOffs(52, 18)
            .addBox(-3.5F, -2.0F, -8.0F, 7.0F, 3.0F, 8.0F, none)
            .texOffs(0, 34)
            .addBox(-2.5F, -0.5F, -9.0F, 5.0F, 2.0F, 2.0F, none),
         PartPose.offset(0.0F, -4.5F, -5.0F)
      );
      head.addOrReplaceChild(
         "teeth_upper",
         CubeListBuilder.create()
            .texOffs(80, 0)
            .addBox(-3.0F, 0.0F, -7.5F, 1.0F, 3.0F, 1.0F, none)
            .texOffs(84, 0)
            .addBox(-1.5F, 0.0F, -8.2F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(88, 0)
            .addBox(0.5F, 0.0F, -8.2F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(92, 0)
            .addBox(2.0F, 0.0F, -7.5F, 1.0F, 3.0F, 1.0F, none)
            .texOffs(96, 0)
            .addBox(-0.5F, 0.0F, -8.5F, 1.0F, 2.5F, 1.0F, none),
         PartPose.offset(0.0F, -2.5F, -5.5F)
      );
      head.addOrReplaceChild(
         "mouth_cavity",
         CubeListBuilder.create().texOffs(200, 16).addBox(-3.0F, -2.0F, -6.0F, 6.0F, 4.0F, 6.0F, none),
         PartPose.offset(0.0F, -1.5F, -5.5F)
      );
      head.addOrReplaceChild(
         "eye_left",
         CubeListBuilder.create().texOffs(200, 28).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.5F, 1.0F, none),
         PartPose.offset(2.6F, -6.0F, -6.2F)
      );
      head.addOrReplaceChild(
         "eye_right",
         CubeListBuilder.create().texOffs(206, 28).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.5F, 1.0F, none),
         PartPose.offset(-2.6F, -6.0F, -6.2F)
      );

      PartDefinition jaw = head.addOrReplaceChild(
         "jaw",
         CubeListBuilder.create()
            .texOffs(80, 16)
            .addBox(-3.5F, 0.0F, -9.0F, 7.0F, 2.5F, 10.0F, none)
            .texOffs(80, 30)
            .addBox(-3.0F, -1.5F, -8.5F, 1.0F, 2.0F, 1.0F, none)
            .texOffs(84, 30)
            .addBox(-1.5F, -2.0F, -9.0F, 1.0F, 2.5F, 1.0F, none)
            .texOffs(88, 30)
            .addBox(0.5F, -2.0F, -9.0F, 1.0F, 2.5F, 1.0F, none)
            .texOffs(92, 30)
            .addBox(2.0F, -1.5F, -8.5F, 1.0F, 2.0F, 1.0F, none)
            .texOffs(96, 30)
            .addBox(-0.5F, -1.8F, -9.2F, 1.0F, 2.0F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, -1.5F, -3.5F, 0.4F, 0.0F, 0.0F)
      );
      jaw.addOrReplaceChild(
         "jaw_glow",
         CubeListBuilder.create().texOffs(200, 32).addBox(-2.5F, -1.0F, -7.0F, 5.0F, 1.5F, 6.0F, none),
         PartPose.offset(0.0F, 0.5F, 0.0F)
      );

      buildHorn(head, true);
      buildHorn(head, false);
   }

   private static void buildHorn(PartDefinition head, boolean left) {
      CubeDeformation none = CubeDeformation.NONE;
      String prefix = left ? "horn_left" : "horn_right";
      float side = left ? 1.0F : -1.0F;
      PartDefinition h1 = head.addOrReplaceChild(
         prefix + "_1",
         CubeListBuilder.create().texOffs(100, 0).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F, none),
         PartPose.offsetAndRotation(3.2F * side, -9.0F, 0.5F, -0.5F, 0.12F * side, 0.4F * side)
      );
      PartDefinition h2 = h1.addOrReplaceChild(
         prefix + "_2",
         CubeListBuilder.create().texOffs(100, 8).addBox(-1.25F, -5.0F, -1.25F, 2.5F, 5.0F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.55F, 0.1F * side, 0.22F * side)
      );
      PartDefinition h3 = h2.addOrReplaceChild(
         prefix + "_3",
         CubeListBuilder.create().texOffs(100, 16).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.6F, 0.08F * side, 0.15F * side)
      );
      h3.addOrReplaceChild(
         prefix + "_4",
         CubeListBuilder.create().texOffs(100, 24).addBox(-0.5F, -4.5F, -0.5F, 1.0F, 5.0F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.4F, 0.05F * side, 0.1F * side)
      );
   }

   private static void buildArm(PartDefinition spine, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;

      PartDefinition shoulder = spine.addOrReplaceChild(
         side + "_shoulder",
         CubeListBuilder.create().texOffs(0, 120).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, none),
         PartPose.offsetAndRotation(5.5F * s, -10.0F, 0.0F, 0.25F, 0.0F, 0.2F * s)
      );
      PartDefinition upper = shoulder.addOrReplaceChild(
         side + "_upper_arm",
         CubeListBuilder.create()
            .texOffs(16, 120)
            .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 12.0F, 3.5F, none)
            .texOffs(30, 120)
            .addBox(-2.25F, -1.0F, -2.25F, 4.5F, 3.0F, 4.5F, none),
         PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.2F, 0.0F, 0.1F * s)
      );
      PartDefinition forearm = upper.addOrReplaceChild(
         side + "_forearm",
         CubeListBuilder.create()
            .texOffs(48, 120)
            .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F, none)
            .texOffs(60, 120)
            .addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 11.5F, 0.0F, -0.35F, 0.0F, 0.0F)
      );
      PartDefinition hand = forearm.addOrReplaceChild(
         side + "_hand",
         CubeListBuilder.create().texOffs(76, 120).addBox(-2.5F, 0.0F, -2.0F, 5.0F, 4.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 13.5F, 0.0F, -0.1F, 0.0F, 0.0F)
      );

      addTalon(hand, side + "_claw1", -1.8F, 3.5F, -0.5F, 0.4F, 0.2F * s, 0.25F * s, 0, 140);
      addTalon(hand, side + "_claw2", 0.0F, 4.0F, -1.0F, 0.5F, 0.0F, 0.0F, 16, 140);
      addTalon(hand, side + "_claw3", 1.8F, 3.5F, -0.5F, 0.4F, -0.2F * s, -0.25F * s, 32, 140);
   }

   private static void addTalon(PartDefinition hand, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition base = hand.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(-0.75F, 0.0F, -0.75F, 1.5F, 6.0F, 1.5F, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      PartDefinition mid = base.addOrReplaceChild(
         "mid",
         CubeListBuilder.create().texOffs(u, v + 8).addBox(-0.55F, 0.0F, -0.55F, 1.1F, 5.0F, 1.1F, none),
         PartPose.offsetAndRotation(0.0F, 5.5F, 0.0F, 0.4F, 0.0F, 0.0F)
      );
      mid.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(212, 16).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 4.5F, 0.8F, none),
         PartPose.offsetAndRotation(0.0F, 4.7F, 0.0F, 0.45F, 0.0F, 0.0F)
      );
   }

   private static void buildWing(PartDefinition spine, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;

      PartDefinition wingRoot = spine.addOrReplaceChild(
         "wing_root_" + side,
         CubeListBuilder.create()
            .texOffs(0, 160)
            .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, none),
         PartPose.offsetAndRotation(3.5F * s, -10.5F, 2.5F, 0.15F, -0.55F * s, -0.35F * s)
      );

      PartDefinition upper = wingRoot.addOrReplaceChild(
         "wing_upper_" + side,
         CubeListBuilder.create()
            .texOffs(16, 160)
            .addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 16.0F, none)
            .texOffs(50, 160)
            .addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 1.5F, -0.25F, 0.35F * s, -0.55F * s)
      );

      // Membrane panel between root and mid (tattered)
      addMembranePanel(upper, "membrane_upper_" + side, 0.0F, 1.5F, 2.0F, 10.0F, 12.0F, s, 120, 40);
      addMembraneHoles(upper, "membrane_upper_holes_" + side, 0.0F, 3.0F, 4.0F, s, 140, 40);
      addMembraneGlowEdge(upper, "membrane_upper_glow_" + side, 0.0F, 12.0F, 3.0F, 8.0F, s, 200, 48);

      PartDefinition mid = upper.addOrReplaceChild(
         "wing_mid_" + side,
         CubeListBuilder.create()
            .texOffs(16, 180)
            .addBox(-1.25F, -1.25F, 0.0F, 2.5F, 2.5F, 13.0F, none)
            .texOffs(46, 180)
            .addBox(-1.75F, -1.75F, -0.5F, 3.5F, 3.5F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 15.0F, 0.35F, 0.2F * s, -0.25F * s)
      );

      // Bony fingers fanning from mid joint
      addWingFinger(mid, "finger1_" + side, -0.5F * s, 0.5F, 2.0F, 0.15F, 0.35F * s, 0.4F * s, 10.0F, 64, 160);
      addWingFinger(mid, "finger2_" + side, 0.0F, 1.0F, 3.0F, 0.45F, 0.15F * s, 0.15F * s, 12.0F, 76, 160);
      addWingFinger(mid, "finger3_" + side, 0.5F * s, 1.5F, 2.5F, 0.7F, -0.1F * s, -0.1F * s, 11.0F, 88, 160);

      addMembranePanel(mid, "membrane_mid_" + side, 0.0F, 1.2F, 1.5F, 14.0F, 11.0F, s, 120, 60);
      addMembraneHoles(mid, "membrane_mid_holes_" + side, 0.0F, 4.0F, 3.0F, s, 150, 60);
      addMembraneGlowEdge(mid, "membrane_mid_glow_" + side, 0.0F, 13.5F, 2.0F, 9.0F, s, 210, 48);

      PartDefinition outer = mid.addOrReplaceChild(
         "wing_outer_" + side,
         CubeListBuilder.create()
            .texOffs(16, 196)
            .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 10.0F, none)
            .texOffs(40, 196)
            .addBox(-1.4F, -1.4F, -0.5F, 2.8F, 2.8F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.45F, 0.15F * s, -0.15F * s)
      );

      addWingFinger(outer, "finger_outer1_" + side, -0.3F * s, 0.8F, 1.0F, 0.35F, 0.25F * s, 0.2F * s, 8.0F, 64, 176);
      addWingFinger(outer, "finger_outer2_" + side, 0.3F * s, 1.2F, 1.5F, 0.65F, -0.15F * s, -0.15F * s, 9.0F, 76, 176);

      addMembranePanel(outer, "membrane_outer_" + side, 0.0F, 1.0F, 0.5F, 12.0F, 9.0F, s, 120, 80);
      addMembraneHoles(outer, "membrane_outer_holes_" + side, 0.0F, 3.5F, 2.0F, s, 160, 80);
      addMembraneGlowEdge(outer, "membrane_outer_glow_" + side, 0.0F, 11.0F, 1.0F, 8.0F, s, 220, 48);

      // Tip claw / bone tip
      outer.addOrReplaceChild(
         "wing_tip_" + side,
         CubeListBuilder.create()
            .texOffs(52, 196)
            .addBox(-0.6F, -0.6F, 0.0F, 1.2F, 1.2F, 5.0F, none)
            .texOffs(230, 16)
            .addBox(-0.4F, -0.4F, 4.5F, 0.8F, 0.8F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 9.5F, 0.25F, 0.1F * s, 0.0F)
      );
   }

   private static void addWingFinger(
      PartDefinition parent, String name, float x, float y, float z, float rx, float ry, float rz, float len, int u, int v
   ) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition finger = parent.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(-0.6F, -0.6F, 0.0F, 1.2F, 1.2F, len, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      finger.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(u, v + 12).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, len * 0.55F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, len - 0.3F, 0.2F, 0.0F, 0.0F)
      );
   }

   private static void addMembranePanel(
      PartDefinition parent, String name, float x, float y, float z, float width, float height, float side, int u, int v
   ) {
      // Thin membrane sheet hanging from bony chain (rotated to face outward)
      parent.addOrReplaceChild(
         name,
         CubeListBuilder.create()
            .texOffs(u, v)
            .addBox(-width * 0.5F, 0.0F, -0.4F, width, height, 0.8F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(x + 0.8F * side, y, z, 0.15F, -0.1F * side, 0.05F * side)
      );
   }

   private static void addMembraneHoles(PartDefinition parent, String name, float x, float y, float z, float side, int u, int v) {
      // Negative-space suggestion: small charcoal "hole" cubes punched through membrane plane
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition holes = parent.addOrReplaceChild(
         name,
         CubeListBuilder.create()
            .texOffs(u, v)
            .addBox(-1.0F, 0.0F, -0.55F, 2.0F, 2.0F, 1.1F, none)
            .texOffs(u + 4, v)
            .addBox(2.5F * side, 2.5F, -0.55F, 1.5F, 1.5F, 1.1F, none)
            .texOffs(u + 8, v)
            .addBox(-3.0F * side, 4.0F, -0.55F, 2.0F, 1.5F, 1.1F, none)
            .texOffs(u + 12, v)
            .addBox(0.5F * side, 6.0F, -0.55F, 1.5F, 2.0F, 1.1F, none)
            .texOffs(u, v + 4)
            .addBox(-2.5F * side, 7.5F, -0.55F, 1.5F, 1.5F, 1.1F, none),
         PartPose.offset(x, y, z)
      );
      holes.addOrReplaceChild(
         "tear_edge",
         CubeListBuilder.create()
            .texOffs(u + 16, v)
            .addBox(-4.0F, 0.0F, -0.45F, 3.0F, 1.0F, 0.9F, none)
            .texOffs(u + 20, v)
            .addBox(1.0F, 1.5F, -0.45F, 2.5F, 1.0F, 0.9F, none),
         PartPose.offset(0.0F, 8.5F, 0.0F)
      );
   }

   private static void addMembraneGlowEdge(
      PartDefinition parent, String name, float x, float y, float z, float width, float side, int u, int v
   ) {
      parent.addOrReplaceChild(
         name,
         CubeListBuilder.create()
            .texOffs(u, v)
            .addBox(-width * 0.5F, 0.0F, -0.35F, width, 1.2F, 0.7F, CubeDeformation.NONE)
            .texOffs(u, v + 4)
            .addBox(-width * 0.35F, 1.0F, -0.3F, width * 0.5F, 1.5F, 0.6F, CubeDeformation.NONE)
            .texOffs(u + 12, v)
            .addBox(width * 0.1F * side, 0.8F, -0.3F, 1.2F, 2.0F, 0.6F, CubeDeformation.NONE),
         PartPose.offset(x, y, z)
      );
   }

   private static void buildTail(PartDefinition bodyRoot) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition pelvis = bodyRoot.addOrReplaceChild(
         "pelvis",
         CubeListBuilder.create()
            .texOffs(0, 90)
            .addBox(-3.0F, -1.5F, -2.5F, 6.0F, 3.0F, 5.0F, none)
            .texOffs(22, 90)
            .addBox(-1.5F, 1.0F, -1.5F, 3.0F, 2.0F, 3.0F, none),
         PartPose.offset(0.0F, 2.0F, 0.5F)
      );

      PartDefinition t1 = pelvis.addOrReplaceChild(
         "tail_1",
         CubeListBuilder.create()
            .texOffs(40, 90)
            .addBox(-2.0F, -1.5F, -1.5F, 4.0F, 5.0F, 3.0F, none)
            .texOffs(54, 90)
            .addBox(-1.5F, 3.0F, -1.0F, 3.0F, 2.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, 2.5F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
      PartDefinition t2 = t1.addOrReplaceChild(
         "tail_2",
         CubeListBuilder.create().texOffs(40, 98).addBox(-1.75F, 0.0F, -1.4F, 3.5F, 5.0F, 2.8F, none),
         PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.28F, 0.0F, 0.0F)
      );
      PartDefinition t3 = t2.addOrReplaceChild(
         "tail_3",
         CubeListBuilder.create().texOffs(54, 98).addBox(-1.5F, 0.0F, -1.25F, 3.0F, 5.0F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.25F, 0.0F, 0.0F)
      );
      PartDefinition t4 = t3.addOrReplaceChild(
         "tail_4",
         CubeListBuilder.create().texOffs(66, 90).addBox(-1.25F, 0.0F, -1.1F, 2.5F, 4.5F, 2.2F, none),
         PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, 0.3F, 0.0F, 0.0F)
      );
      PartDefinition t5 = t4.addOrReplaceChild(
         "tail_5",
         CubeListBuilder.create().texOffs(76, 90).addBox(-1.0F, 0.0F, -0.95F, 2.0F, 4.0F, 1.9F, none),
         PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
      PartDefinition t6 = t5.addOrReplaceChild(
         "tail_6",
         CubeListBuilder.create()
            .texOffs(84, 90)
            .addBox(-0.75F, 0.0F, -0.8F, 1.5F, 3.5F, 1.6F, none)
            .texOffs(224, 24)
            .addBox(-0.4F, 1.0F, -0.4F, 0.8F, 1.5F, 0.8F, none),
         PartPose.offsetAndRotation(0.0F, 3.5F, 0.0F, 0.4F, 0.0F, 0.0F)
      );
      t6.addOrReplaceChild(
         "tail_tip",
         CubeListBuilder.create()
            .texOffs(200, 40)
            .addBox(-0.55F, 0.0F, -0.55F, 1.1F, 4.0F, 1.1F, none)
            .texOffs(206, 40)
            .addBox(-0.35F, 3.5F, -0.35F, 0.7F, 3.0F, 0.7F, none)
            .texOffs(212, 40)
            .addBox(-0.25F, 6.0F, -0.25F, 0.5F, 2.5F, 0.5F, none),
         PartPose.offsetAndRotation(0.0F, 3.2F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
   }

   @Override
   public void setupAnim(
      InfernalHarrowEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      float breathe = Mth.sin(ageInTicks * 0.08F);
      float breatheSlow = Mth.sin(ageInTicks * 0.05F);
      float bob = Mth.sin(ageInTicks * 0.1F) * 1.2F;
      boolean hardFly = entity.isFlyingHard();
      float flyBlend = hardFly ? 1.0F : 0.0F;

      // HOVER bob
      this.bodyRoot.y = 22.0F + bob;
      this.bodyRoot.xRot = 0.0F;
      this.bodyRoot.zRot = breatheSlow * 0.02F;
      this.bodyRoot.yRot = 0.0F;

      // Spine idle + FLY pitch
      float flyPitch = 0.55F * flyBlend;
      this.spine.xRot = 0.22F + breathe * 0.04F + flyPitch;
      this.spine.yRot = breatheSlow * 0.03F;
      this.spine.zRot = Mth.sin(ageInTicks * 0.07F) * 0.025F;
      this.sternum.xRot = breathe * 0.03F;

      // Infernal core pulse
      float corePulse = 1.0F + breathe * 0.1F + Mth.sin(ageInTicks * 0.22F) * 0.04F;
      this.infernalCore.xScale = corePulse;
      this.infernalCore.yScale = 1.0F + breathe * 0.14F;
      this.infernalCore.zScale = corePulse;

      // Head tracking + occasional twitch
      float twitch = 0.0F;
      if ((int)(ageInTicks / 35.0F) % 6 == 0) {
         twitch = Mth.sin(ageInTicks * 1.9F) * 0.07F;
      }
      this.headRoot.yRot = netHeadYaw * ((float)Math.PI / 180F) * 0.85F + twitch;
      this.headRoot.xRot = -0.15F + headPitch * ((float)Math.PI / 180F) * 0.7F + breathe * 0.02F;
      this.headRoot.zRot = twitch * 0.5F;

      // Jaw idle flutter
      float jawIdle = 0.4F + Mth.sin(ageInTicks * 0.12F) * 0.06F + Mth.sin(ageInTicks * 0.37F) * 0.03F;
      this.jaw.xRot = jawIdle;

      // Wing beat — slow hover vs strong fly
      float wingSpeed = hardFly ? 0.55F : 0.18F;
      float wingAmp = hardFly ? 0.75F : 0.35F;
      float flap = Mth.sin(ageInTicks * wingSpeed) * wingAmp;
      float flapOpp = Mth.cos(ageInTicks * wingSpeed) * wingAmp * 0.55F;

      this.wingRootRight.zRot = 0.35F + flap;
      this.wingRootLeft.zRot = -0.35F - flap;
      this.wingRootRight.xRot = 0.15F + flapOpp * 0.25F - 0.1F * flyBlend;
      this.wingRootLeft.xRot = 0.15F + flapOpp * 0.25F - 0.1F * flyBlend;
      this.wingRootRight.yRot = 0.55F - 0.1F * flyBlend;
      this.wingRootLeft.yRot = -0.55F + 0.1F * flyBlend;

      this.wingUpperRight.zRot = 0.55F + flap * 0.65F;
      this.wingUpperLeft.zRot = -0.55F - flap * 0.65F;
      this.wingUpperRight.xRot = -0.25F + flapOpp * 0.2F;
      this.wingUpperLeft.xRot = -0.25F + flapOpp * 0.2F;

      this.wingMidRight.zRot = 0.25F + flap * 0.45F;
      this.wingMidLeft.zRot = -0.25F - flap * 0.45F;
      this.wingMidRight.xRot = 0.35F + flapOpp * 0.3F;
      this.wingMidLeft.xRot = 0.35F + flapOpp * 0.3F;

      this.wingOuterRight.zRot = 0.15F + flap * 0.3F;
      this.wingOuterLeft.zRot = -0.15F - flap * 0.3F;
      this.wingOuterRight.xRot = 0.45F + flapOpp * 0.35F;
      this.wingOuterLeft.xRot = 0.45F + flapOpp * 0.35F;

      // Arms hang; slight sway
      float armSway = Mth.sin(ageInTicks * 0.11F) * 0.08F;
      this.rightShoulder.xRot = 0.25F + armSway + 0.15F * flyBlend;
      this.leftShoulder.xRot = 0.25F - armSway + 0.15F * flyBlend;
      this.rightShoulder.zRot = -0.2F + breathe * 0.02F;
      this.leftShoulder.zRot = 0.2F - breathe * 0.02F;
      this.rightShoulder.yRot = 0.0F;
      this.leftShoulder.yRot = 0.0F;
      this.rightUpperArm.xRot = -0.2F + armSway * 0.5F;
      this.leftUpperArm.xRot = -0.2F - armSway * 0.5F;
      this.rightForearm.xRot = -0.35F;
      this.leftForearm.xRot = -0.35F;

      float finger = Mth.sin(ageInTicks * 0.25F) * 0.08F;
      this.rightClaw1.xRot = 0.4F + finger;
      this.rightClaw2.xRot = 0.5F - finger * 0.7F;
      this.rightClaw3.xRot = 0.4F + finger * 0.5F;
      this.leftClaw1.xRot = 0.4F - finger;
      this.leftClaw2.xRot = 0.5F + finger * 0.7F;
      this.leftClaw3.xRot = 0.4F - finger * 0.5F;

      // Tail serpentine sway — trails more when flying hard
      float sway = Mth.sin(ageInTicks * 0.14F);
      float sway2 = Mth.cos(ageInTicks * 0.11F);
      float trail = hardFly ? 0.35F : 0.0F;
      this.pelvis.xRot = trail * 0.2F;
      this.tail1.xRot = 0.35F - trail * 0.45F + sway * 0.06F;
      this.tail1.zRot = sway2 * 0.08F;
      this.tail1.yRot = sway * 0.1F;
      this.tail2.xRot = 0.28F - trail * 0.2F + sway * 0.08F;
      this.tail2.zRot = sway2 * 0.1F;
      this.tail2.yRot = sway * 0.08F;
      this.tail3.xRot = 0.25F + sway * 0.1F;
      this.tail3.zRot = sway2 * 0.12F;
      this.tail3.yRot = sway * 0.1F;
      this.tail4.xRot = 0.3F + sway * 0.12F;
      this.tail4.zRot = sway2 * 0.14F;
      this.tail5.xRot = 0.35F + sway * 0.14F;
      this.tail5.zRot = sway2 * 0.12F;
      this.tail6.xRot = 0.4F + sway * 0.1F;
      this.tail6.zRot = sway2 * 0.1F;
      this.tailTip.xRot = 0.35F + sway * 0.08F;
      this.tailTip.zRot = sway2 * 0.08F;

      // FIRE / RANGED attack — jaw open, wings pull back, head lock
      float ranged = entity.getRangedAttackProgress(0.0F);
      if (ranged > 0.0F) {
         float wind = smoothStep(ranged, 0.0F, 0.45F);
         float hold = smoothStep(ranged, 0.45F, 0.75F);
         float release = smoothStep(ranged, 0.75F, 1.0F);
         float open = wind + hold * 0.85F - release * 0.7F;
         this.jaw.xRot = jawIdle + 0.95F * open;
         this.headRoot.xRot = -0.05F + 0.1F * wind;
         this.headRoot.yRot *= 1.0F - 0.7F * open;
         this.wingRootRight.zRot += -0.45F * open;
         this.wingRootLeft.zRot += 0.45F * open;
         this.wingUpperRight.zRot += -0.35F * open;
         this.wingUpperLeft.zRot += 0.35F * open;
         this.spine.xRot += 0.12F * open;
         this.infernalCore.xScale += 0.15F * open;
         this.infernalCore.yScale += 0.2F * open;
         this.infernalCore.zScale += 0.15F * open;
      }

      // MELEE claw slash
      float attack = entity.getMeleeAttackProgress(0.0F);
      int attackType = entity.getMeleeAttackType();
      if (attack > 0.0F) {
         float wind = smoothStep(attack, 0.0F, 0.3F);
         float strike = Mth.sin(smoothStep(attack, 0.3F, 0.7F) * (float)Math.PI);
         float recover = smoothStep(attack, 0.7F, 1.0F);
         if (attackType == 1) {
            // Opposite-arm slash
            this.leftShoulder.xRot = 0.25F - 1.2F * wind - 1.5F * strike + 0.25F * recover;
            this.leftShoulder.yRot = 0.65F * wind + 0.95F * strike;
            this.leftShoulder.zRot = 0.2F + 0.55F * wind;
            this.leftForearm.xRot = -0.35F - 0.45F * wind - 0.75F * strike;
            this.leftClaw1.xRot = 0.4F + 0.45F * wind;
            this.leftClaw2.xRot = 0.5F + 0.55F * wind;
            this.leftClaw3.xRot = 0.4F + 0.45F * wind;
            this.spine.yRot += 0.4F * wind + 0.5F * strike - 0.2F * recover;
         } else {
            this.rightShoulder.xRot = 0.25F - 1.2F * wind - 1.5F * strike + 0.25F * recover;
            this.rightShoulder.yRot = -0.65F * wind - 0.95F * strike;
            this.rightShoulder.zRot = -0.2F - 0.55F * wind;
            this.rightForearm.xRot = -0.35F - 0.45F * wind - 0.75F * strike;
            this.rightClaw1.xRot = 0.4F + 0.45F * wind;
            this.rightClaw2.xRot = 0.5F + 0.55F * wind;
            this.rightClaw3.xRot = 0.4F + 0.45F * wind;
            this.spine.yRot += -0.4F * wind - 0.5F * strike + 0.2F * recover;
         }
         this.spine.xRot += 0.18F * strike;
         this.jaw.xRot = jawIdle + 0.2F * strike;
         this.bodyRoot.z = -1.5F * strike;
      } else {
         this.bodyRoot.z = 0.0F;
      }

      // HURT — core flare + recoil
      float hurt = entity.getHurtReaction(0.0F);
      if (hurt > 0.0F) {
         float h = Mth.sin(hurt * (float)Math.PI);
         this.spine.xRot -= 0.2F * h;
         this.headRoot.zRot += 0.3F * h;
         this.jaw.xRot += 0.35F * h;
         this.wingRootRight.zRot += 0.35F * h;
         this.wingRootLeft.zRot -= 0.35F * h;
         this.infernalCore.xScale += 0.25F * h;
         this.infernalCore.yScale += 0.35F * h;
         this.infernalCore.zScale += 0.25F * h;
      }

      // DEATH — collapse, glow scales down
      float death = entity.getDeathReaction(0.0F);
      if (death > 0.0F) {
         float d = Mth.clamp(death, 0.0F, 1.0F);
         this.bodyRoot.y = 22.0F + bob * (1.0F - d) + 8.0F * d;
         this.spine.xRot = 0.22F + 0.85F * d;
         this.jaw.xRot = 0.4F + 0.65F * d;
         this.wingRootRight.zRot = 0.35F + 0.9F * d;
         this.wingRootLeft.zRot = -0.35F - 0.9F * d;
         this.wingUpperRight.zRot = 0.55F + 0.7F * d;
         this.wingUpperLeft.zRot = -0.55F - 0.7F * d;
         this.tail1.xRot = 0.35F + 0.5F * d;
         this.tail3.xRot = 0.25F + 0.55F * d;
         this.tail5.xRot = 0.35F + 0.5F * d;
         this.infernalCore.xScale = Math.max(0.05F, corePulse * (1.0F - 0.85F * d));
         this.infernalCore.yScale = Math.max(0.05F, (1.0F + breathe * 0.14F) * (1.0F - 0.85F * d));
         this.infernalCore.zScale = Math.max(0.05F, corePulse * (1.0F - 0.85F * d));
         this.rightShoulder.xRot = 0.25F + 0.6F * d;
         this.leftShoulder.xRot = 0.25F + 0.55F * d;
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
