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
import org.millenaire.entity.InfernalCultistEntity;

/**
 * Production Infernal Cultist model — smaller servant (~1.95 blocks), not a mini-priest.
 * Pointed hood, skull face, torn layered robe, bone ornaments, chest ritual mark,
 * skeletal claws. Variant equipment: ritualist book / zealot dagger.
 *
 * <pre>
 * 256x256 UV atlas (albedo):
 *   0,0     skull / face             32x24
 *   32,0    pointed hood             48x32
 *   80,0    forehead rune base       16x16
 *   96,0    jaw                      24x12
 *   0,32    torso / chest robe       64x40
 *   64,32   pelvis / ornaments       48x24
 *   0,72    robe panels (6)          128x40
 *   0,112   rune / bone strips       64x24
 *   0,136   arms / claws             112x40
 *   112,136 legs                     64x40
 *   176,0   ritual book              32x32
 *   208,0   dagger (albedo)          16x32  (shares column with emissive marks)
 * Emissive islands (u &gt;= 200):
 *   200,40  eyes                     12x8
 *   200,52  forehead rune glow       12x12
 *   212,52  chest ritual symbol      16x16
 *   228,52  claw tip glow            12x12
 *   200,72  book runes               16x16
 *   216,72  dagger glow edge         12x16
 *   228,72  robe rune accents        16x12
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class InfernalCultistModel extends EntityModel<InfernalCultistEntity> {
   private final ModelPart root;
   private final ModelPart pelvis;
   private final ModelPart robeRoot;
   private final ModelPart[] robePanels = new ModelPart[6];
   private final ModelPart torso;
   private final ModelPart chestMark;
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
   private final ModelPart ritualBook;
   private final ModelPart zealotDagger;

   public InfernalCultistModel(ModelPart root) {
      this.root = root;
      this.pelvis = root.getChild("pelvis");
      this.robeRoot = this.pelvis.getChild("robe_root");
      for (int i = 0; i < 6; i++) {
         this.robePanels[i] = this.robeRoot.getChild("robe_panel_" + i);
      }

      this.torso = this.pelvis.getChild("torso");
      this.chestMark = this.torso.getChild("chest_mark");
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

      this.ritualBook = this.leftHand.getChild("ritual_book");
      this.zealotDagger = this.rightHand.getChild("zealot_dagger");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      // ~1.95 blocks: pelvis y=24, legs ~10 → feet ≈34; head/hood top ≈ y=2.
      PartDefinition pelvis = root.addOrReplaceChild(
         "pelvis",
         CubeListBuilder.create()
            .texOffs(64, 32)
            .addBox(-3.5F, -1.5F, -2.5F, 7.0F, 3.0F, 5.0F, none)
            .texOffs(64, 40)
            .addBox(-2.0F, -2.5F, -1.5F, 4.0F, 1.5F, 3.0F, none),
         PartPose.offset(0.0F, 24.0F, 0.0F)
      );

      buildRobe(pelvis);
      buildTorso(pelvis);
      buildLeg(pelvis, true);
      buildLeg(pelvis, false);

      return LayerDefinition.create(mesh, 256, 256);
   }

   private static void buildRobe(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition robeRoot = pelvis.addOrReplaceChild(
         "robe_root",
         CubeListBuilder.create(),
         PartPose.offset(0.0F, 0.5F, 0.0F)
      );

      float[][] panelData = {
         {-3.5F, 0.0F, -2.4F, 0.06F, 0.0F, 0.1F},
         {-1.2F, 0.0F, -2.8F, 0.03F, 0.0F, 0.04F},
         {1.2F, 0.0F, -2.8F, 0.03F, 0.0F, -0.04F},
         {3.5F, 0.0F, -2.4F, 0.06F, 0.0F, -0.1F},
         {-2.5F, 0.0F, 2.2F, 0.1F, 0.0F, -0.08F},
         {2.5F, 0.0F, 2.2F, 0.1F, 0.0F, 0.08F}
      };
      for (int i = 0; i < 6; i++) {
         float[] d = panelData[i];
         float w = i % 2 == 0 ? 2.2F : 1.8F;
         float h = 10.0F + (i % 3) * 1.2F;
         int u = (i % 4) * 14;
         int v = 72 + (i / 4) * 18;
         robeRoot.addOrReplaceChild(
            "robe_panel_" + i,
            CubeListBuilder.create()
               .texOffs(u, v)
               .addBox(-w * 0.5F, 0.0F, -0.35F, w, h, 0.7F, none)
               .texOffs(u, v + 14)
               .addBox(-w * 0.3F, h - 1.5F, -0.3F, w * 0.6F, 2.5F, 0.6F, none)
               .texOffs(228, 72)
               .addBox(-0.4F, 2.0F + i, -0.4F, 0.8F, 2.0F, 0.8F, none),
            PartPose.offsetAndRotation(d[0], d[1], d[2], d[3], d[4], d[5])
         );
      }

      // Front bone strip ornaments
      robeRoot.addOrReplaceChild(
         "bone_strip_l",
         CubeListBuilder.create().texOffs(0, 112).addBox(-0.5F, 0.0F, -0.3F, 1.0F, 11.0F, 0.6F, none),
         PartPose.offsetAndRotation(-1.8F, 0.2F, -3.0F, 0.05F, 0.0F, 0.04F)
      );
      robeRoot.addOrReplaceChild(
         "bone_strip_r",
         CubeListBuilder.create().texOffs(8, 112).addBox(-0.5F, 0.0F, -0.3F, 1.0F, 11.0F, 0.6F, none),
         PartPose.offsetAndRotation(1.8F, 0.2F, -3.0F, 0.05F, 0.0F, -0.04F)
      );
   }

   private static void buildTorso(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      // Aggressive forward lean baked into bind pose
      PartDefinition torso = pelvis.addOrReplaceChild(
         "torso",
         CubeListBuilder.create()
            .texOffs(0, 32)
            .addBox(-3.5F, -10.0F, -2.5F, 7.0F, 10.0F, 5.0F, none)
            .texOffs(24, 32)
            .addBox(-4.0F, -10.5F, -2.0F, 8.0F, 2.5F, 4.0F, none)
            .texOffs(0, 48)
            .addBox(-1.25F, -9.5F, -1.25F, 2.5F, 9.0F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, -1.5F, 0.0F, 0.22F, 0.0F, 0.0F)
      );

      torso.addOrReplaceChild(
         "chest_mark",
         CubeListBuilder.create()
            .texOffs(212, 52)
            .addBox(-1.75F, -1.75F, -0.5F, 3.5F, 3.5F, 1.0F, none)
            .texOffs(220, 52)
            .addBox(-0.6F, -2.4F, -0.6F, 1.2F, 4.8F, 1.0F, none)
            .texOffs(224, 52)
            .addBox(-2.4F, -0.6F, -0.6F, 4.8F, 1.2F, 1.0F, none),
         PartPose.offset(0.0F, -6.5F, -2.7F)
      );

      torso.addOrReplaceChild(
         "chain_l",
         CubeListBuilder.create().texOffs(88, 32).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 6.0F, 0.8F, none),
         PartPose.offsetAndRotation(3.2F, -9.5F, -1.2F, 0.2F, 0.0F, -0.25F)
      );
      torso.addOrReplaceChild(
         "chain_r",
         CubeListBuilder.create().texOffs(92, 32).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 6.0F, 0.8F, none),
         PartPose.offsetAndRotation(-3.2F, -9.5F, -1.2F, 0.2F, 0.0F, 0.25F)
      );
      torso.addOrReplaceChild(
         "bone_ornament",
         CubeListBuilder.create().texOffs(96, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, none),
         PartPose.offset(0.0F, -0.5F, -2.8F)
      );

      torso.addOrReplaceChild(
         "neck",
         CubeListBuilder.create().texOffs(48, 32).addBox(-1.5F, -2.5F, -1.5F, 3.0F, 3.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, -10.0F, -0.3F, 0.15F, 0.0F, 0.0F)
      );

      buildHead(torso);
      buildArm(torso, true);
      buildArm(torso, false);
   }

   private static void buildHead(PartDefinition torso) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition head = torso.addOrReplaceChild(
         "head_root",
         CubeListBuilder.create(),
         PartPose.offsetAndRotation(0.0F, -11.0F, -0.8F, -0.12F, 0.0F, 0.0F)
      );

      head.addOrReplaceChild(
         "skull",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-3.5F, -7.0F, -3.5F, 7.0F, 6.0F, 7.0F, none)
            .texOffs(0, 13)
            .addBox(-2.5F, -3.5F, -4.5F, 5.0F, 2.5F, 2.0F, none),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "eye_left",
         CubeListBuilder.create().texOffs(200, 40).addBox(-0.9F, -0.9F, -0.4F, 1.8F, 1.4F, 0.8F, none),
         PartPose.offset(1.7F, -4.8F, -3.7F)
      );
      head.addOrReplaceChild(
         "eye_right",
         CubeListBuilder.create().texOffs(206, 40).addBox(-0.9F, -0.9F, -0.4F, 1.8F, 1.4F, 0.8F, none),
         PartPose.offset(-1.7F, -4.8F, -3.7F)
      );

      // Pointed hood — tall peak, no giant horns
      head.addOrReplaceChild(
         "hood",
         CubeListBuilder.create()
            .texOffs(32, 0)
            .addBox(-4.5F, -8.5F, -4.5F, 9.0F, 9.0F, 9.0F, none)
            .texOffs(32, 18)
            .addBox(-3.0F, -12.0F, -3.0F, 6.0F, 4.0F, 6.0F, none)
            .texOffs(68, 0)
            .addBox(-1.5F, -14.5F, -1.5F, 3.0F, 3.0F, 3.0F, none)
            .texOffs(68, 6)
            .addBox(-0.75F, -16.5F, -0.75F, 1.5F, 2.5F, 1.5F, none),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "forehead_rune",
         CubeListBuilder.create()
            .texOffs(80, 0)
            .addBox(-1.25F, -2.5F, -0.4F, 2.5F, 4.0F, 0.8F, none)
            .texOffs(200, 52)
            .addBox(-1.0F, -2.2F, -0.55F, 2.0F, 3.5F, 0.9F, none),
         PartPose.offset(0.0F, -6.5F, -4.6F)
      );

      head.addOrReplaceChild(
         "jaw",
         CubeListBuilder.create()
            .texOffs(96, 0)
            .addBox(-2.5F, 0.0F, -4.0F, 5.0F, 1.8F, 5.0F, none)
            .texOffs(96, 8)
            .addBox(-2.0F, -1.0F, -3.5F, 1.0F, 1.2F, 1.0F, none)
            .texOffs(100, 8)
            .addBox(1.0F, -1.0F, -3.5F, 1.0F, 1.2F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, -1.5F, -1.0F, 0.2F, 0.0F, 0.0F)
      );
   }

   private static void buildArm(PartDefinition torso, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;
      int uBase = right ? 0 : 36;

      PartDefinition shoulder = torso.addOrReplaceChild(
         side + "_shoulder",
         CubeListBuilder.create().texOffs(uBase, 136).addBox(-1.75F, -1.75F, -1.75F, 3.5F, 3.5F, 3.5F, none),
         PartPose.offsetAndRotation(4.2F * s, -9.0F, 0.0F, 0.2F, 0.0F, 0.2F * s)
      );
      PartDefinition upper = shoulder.addOrReplaceChild(
         side + "_upper_arm",
         CubeListBuilder.create().texOffs(uBase, 144).addBox(-1.25F, 0.0F, -1.25F, 2.5F, 7.0F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, 1.25F, 0.0F, -0.15F, 0.0F, 0.1F * s)
      );
      PartDefinition forearm = upper.addOrReplaceChild(
         side + "_forearm",
         CubeListBuilder.create()
            .texOffs(uBase, 154)
            .addBox(-1.1F, 0.0F, -1.1F, 2.2F, 7.0F, 2.2F, none)
            .texOffs(uBase + 10, 154)
            .addBox(-1.5F, -0.8F, -1.5F, 3.0F, 2.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, -0.25F, 0.0F, 0.0F)
      );
      PartDefinition hand = forearm.addOrReplaceChild(
         side + "_hand",
         CubeListBuilder.create().texOffs(72 + uBase, 136).addBox(-1.6F, 0.0F, -1.4F, 3.2F, 2.8F, 2.8F, none),
         PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, -0.1F, 0.0F, 0.0F)
      );

      addClaw(hand, side + "_claw1", -1.2F, 2.5F, -0.3F, 0.4F, 0.2F * s, 0.25F * s, 72, 144);
      addClaw(hand, side + "_claw2", 0.0F, 2.8F, -0.6F, 0.5F, 0.0F, 0.0F, 78, 144);
      addClaw(hand, side + "_claw3", 1.2F, 2.5F, -0.3F, 0.4F, -0.2F * s, -0.25F * s, 84, 144);

      if (right) {
         hand.addOrReplaceChild(
            "zealot_dagger",
            CubeListBuilder.create()
               .texOffs(208, 0)
               .addBox(-0.4F, -1.0F, -0.4F, 0.8F, 8.0F, 0.8F, none)
               .texOffs(212, 0)
               .addBox(-0.7F, -1.5F, -0.7F, 1.4F, 2.0F, 1.4F, none)
               .texOffs(216, 72)
               .addBox(-0.25F, 2.0F, -0.5F, 0.5F, 5.0F, 1.0F, none)
               .texOffs(216, 0)
               .addBox(-1.2F, 6.5F, -0.3F, 2.4F, 1.2F, 0.6F, none),
            PartPose.offsetAndRotation(0.3F, 1.5F, -0.5F, 0.85F, 0.0F, 0.35F)
         );
      } else {
         hand.addOrReplaceChild(
            "ritual_book",
            CubeListBuilder.create()
               .texOffs(176, 0)
               .addBox(-2.0F, -0.5F, -3.0F, 4.0F, 1.2F, 6.0F, none)
               .texOffs(176, 8)
               .addBox(-1.8F, -0.8F, -2.8F, 3.6F, 0.5F, 5.6F, none)
               .texOffs(200, 72)
               .addBox(-1.2F, -0.9F, -2.0F, 2.4F, 0.4F, 4.0F, none)
               .texOffs(176, 16)
               .addBox(-2.1F, 0.5F, -3.1F, 4.2F, 0.4F, 6.2F, none),
            PartPose.offsetAndRotation(0.2F, 1.2F, 0.0F, 0.15F, 0.4F, 0.1F)
         );
      }
   }

   private static void addClaw(
      PartDefinition hand, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v
   ) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition base = hand.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(-0.45F, 0.0F, -0.45F, 0.9F, 3.5F, 0.9F, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      base.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(228, 52).addBox(-0.3F, 0.0F, -0.3F, 0.6F, 2.5F, 0.6F, none),
         PartPose.offsetAndRotation(0.0F, 3.2F, 0.0F, 0.4F, 0.0F, 0.0F)
      );
   }

   private static void buildLeg(PartDefinition pelvis, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;
      int u = right ? 112 : 144;

      PartDefinition thigh = pelvis.addOrReplaceChild(
         side + "_thigh",
         CubeListBuilder.create().texOffs(u, 136).addBox(-1.75F, 0.0F, -1.75F, 3.5F, 6.0F, 3.5F, none),
         PartPose.offsetAndRotation(2.0F * s, 1.0F, 0.0F, -0.05F, 0.0F, 0.04F * s)
      );
      PartDefinition lower = thigh.addOrReplaceChild(
         side + "_lower_leg",
         CubeListBuilder.create().texOffs(u, 146).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, 5.5F, 0.0F, 0.1F, 0.0F, 0.0F)
      );
      lower.addOrReplaceChild(
         side + "_foot",
         CubeListBuilder.create().texOffs(u, 156).addBox(-1.6F, -0.4F, -3.5F, 3.2F, 1.6F, 5.0F, none),
         PartPose.offsetAndRotation(0.0F, 5.5F, 0.2F, -0.05F, 0.0F, 0.0F)
      );
   }

   @Override
   public void setupAnim(
      InfernalCultistEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      float walk = limbSwingAmount;
      float running = entity.isRunning() ? 1.0F : 0.0F;
      float walkCycle = limbSwing * (0.65F + running * 0.35F);
      float breathe = Mth.sin(ageInTicks * 0.1F);
      float breatheSlow = Mth.sin(ageInTicks * 0.055F);

      // Appear: rise + scale in from summon
      float appear = entity.getAppearProgress(0.0F);
      float appearScale = 0.35F + 0.65F * appear;
      this.root.xScale = appearScale;
      this.root.yScale = appearScale;
      this.root.zScale = appearScale;
      float rise = (1.0F - appear) * 10.0F;

      this.pelvis.y = 24.0F + rise + Mth.sin(ageInTicks * 0.11F) * 0.2F;
      this.pelvis.z = 0.0F;
      this.pelvis.xRot = 0.0F;
      this.pelvis.yRot = 0.0F;
      this.pelvis.zRot = 0.0F;

      // Idle lean + breathing
      this.torso.xRot = 0.22F + breathe * 0.03F + running * 0.12F;
      this.torso.yRot = breatheSlow * 0.03F;
      this.torso.zRot = Mth.sin(ageInTicks * 0.07F) * 0.02F;
      float markPulse = 1.0F + breathe * 0.08F;
      this.chestMark.xScale = markPulse;
      this.chestMark.yScale = markPulse;
      this.chestMark.zScale = 1.0F;

      this.headRoot.yRot = netHeadYaw * ((float)Math.PI / 180F) * 0.85F;
      this.headRoot.xRot = -0.12F + headPitch * ((float)Math.PI / 180F) * 0.7F + breathe * 0.02F;
      this.headRoot.zRot = 0.0F;
      float jawIdle = 0.2F + Mth.sin(ageInTicks * 0.14F) * 0.035F;
      this.jaw.xRot = jawIdle;

      // Robe sway
      for (int i = 0; i < 6; i++) {
         float phase = ageInTicks * (0.12F + running * 0.08F) + i * 0.85F;
         this.robePanels[i].xRot = 0.05F + Mth.sin(phase) * 0.07F + walk * 0.1F;
         this.robePanels[i].zRot = Mth.cos(phase * 0.85F) * 0.05F * (i % 2 == 0 ? 1 : -1);
      }

      // Walk / run gait
      float amp = 0.7F + running * 0.45F;
      float stride = Mth.cos(walkCycle) * walk * amp;
      float strideOpp = Mth.cos(walkCycle + (float)Math.PI) * walk * amp;
      this.pelvis.y += Mth.sin(walkCycle * 2.0F) * walk * (0.4F + running * 0.3F);
      this.torso.yRot += stride * (0.08F + running * 0.06F);

      this.rightThigh.xRot = -0.05F + stride;
      this.leftThigh.xRot = -0.05F + strideOpp;
      this.rightLowerLeg.xRot = 0.1F + Mth.clamp(-stride, 0.0F, 1.0F) * 0.5F;
      this.leftLowerLeg.xRot = 0.1F + Mth.clamp(-strideOpp, 0.0F, 1.0F) * 0.5F;
      this.rightFoot.xRot = -0.05F - stride * 0.3F;
      this.leftFoot.xRot = -0.05F - strideOpp * 0.3F;

      float armSwing = Mth.cos(walkCycle - 0.25F) * walk * (0.55F + running * 0.4F);
      this.rightShoulder.xRot = 0.2F + armSwing;
      this.leftShoulder.xRot = 0.2F - armSwing;
      this.rightShoulder.yRot = 0.0F;
      this.leftShoulder.yRot = 0.0F;
      this.rightShoulder.zRot = -0.2F;
      this.leftShoulder.zRot = 0.2F;
      this.rightUpperArm.xRot = -0.15F;
      this.leftUpperArm.xRot = -0.15F;
      this.rightForearm.xRot = -0.25F;
      this.leftForearm.xRot = -0.25F;

      float finger = Mth.sin(ageInTicks * 0.28F) * 0.08F;
      this.rightClaw1.xRot = 0.4F + finger;
      this.rightClaw2.xRot = 0.5F - finger * 0.6F;
      this.rightClaw3.xRot = 0.4F + finger * 0.4F;
      this.leftClaw1.xRot = 0.4F - finger;
      this.leftClaw2.xRot = 0.5F + finger * 0.6F;
      this.leftClaw3.xRot = 0.4F - finger * 0.4F;

      // Variant equipment visibility
      boolean ritualist = entity.isRitualist();
      this.ritualBook.visible = ritualist;
      this.zealotDagger.visible = !ritualist;
      if (ritualist) {
         this.leftShoulder.xRot = 0.35F + breathe * 0.05F - armSwing * 0.15F;
         this.leftForearm.xRot = -0.45F;
         this.ritualBook.xRot = 0.15F + breatheSlow * 0.04F;
         this.ritualBook.yRot = 0.4F;
      } else {
         this.rightShoulder.zRot = -0.25F;
         this.zealotDagger.xRot = 0.85F + Mth.sin(ageInTicks * 0.15F) * 0.03F;
      }

      // Attack lean + strike
      float attack = entity.getMeleeAttackProgress(0.0F);
      if (attack > 0.0F) {
         float wind = smoothStep(attack, 0.0F, 0.28F);
         float strike = Mth.sin(smoothStep(attack, 0.28F, 0.68F) * (float)Math.PI);
         float recover = smoothStep(attack, 0.68F, 1.0F);
         this.torso.xRot = 0.22F + 0.35F * wind + 0.45F * strike - 0.15F * recover;
         this.pelvis.z = -1.5F * strike;
         if (ritualist) {
            // Book bash / claw with free arm
            this.rightShoulder.xRot = 0.2F - 1.2F * wind - 1.5F * strike + 0.25F * recover;
            this.rightShoulder.yRot = -0.5F * wind - 0.7F * strike;
            this.rightForearm.xRot = -0.25F - 0.5F * wind - 0.6F * strike;
            this.leftShoulder.xRot = 0.35F - 0.4F * wind;
         } else {
            // Dagger stab / slash
            this.rightShoulder.xRot = 0.2F - 1.4F * wind - 1.7F * strike + 0.3F * recover;
            this.rightShoulder.yRot = -0.35F * wind - 0.55F * strike;
            this.rightShoulder.zRot = -0.2F - 0.45F * wind;
            this.rightForearm.xRot = -0.25F - 0.55F * wind - 0.8F * strike;
            this.zealotDagger.xRot = 0.85F + 0.4F * wind + 0.5F * strike;
            this.leftShoulder.xRot = 0.2F + 0.3F * strike;
         }
         this.jaw.xRot = jawIdle + 0.25F * strike;
         this.headRoot.xRot += 0.15F * strike;
      }

      // Hurt
      float hurt = entity.getHurtReaction(0.0F);
      if (hurt > 0.0F) {
         float h = Mth.sin(hurt * (float)Math.PI);
         this.torso.xRot -= 0.28F * h;
         this.headRoot.zRot += 0.35F * h;
         this.jaw.xRot += 0.3F * h;
         this.chestMark.xScale += 0.2F * h;
         this.chestMark.yScale += 0.2F * h;
      }

      // Death
      float death = entity.getDeathReaction(0.0F);
      if (death > 0.0F) {
         float d = Mth.clamp(death, 0.0F, 1.0F);
         this.torso.xRot = 0.22F + 1.0F * d;
         this.pelvis.y = 24.0F + rise + 4.0F * d;
         this.headRoot.xRot = -0.12F + 0.55F * d;
         this.jaw.xRot = 0.2F + 0.5F * d;
         this.rightThigh.xRot = -0.05F - 0.4F * d;
         this.leftThigh.xRot = -0.05F - 0.35F * d;
         this.rightShoulder.xRot = 0.2F + 0.75F * d;
         this.leftShoulder.xRot = 0.2F + 0.7F * d;
         this.chestMark.xScale = Math.max(0.05F, markPulse * (1.0F - 0.8F * d));
         this.chestMark.yScale = Math.max(0.05F, markPulse * (1.0F - 0.8F * d));
         for (int i = 0; i < 6; i++) {
            this.robePanels[i].xRot += 0.55F * d;
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
