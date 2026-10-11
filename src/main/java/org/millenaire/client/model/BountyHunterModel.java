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
import org.millenaire.entity.BountyHunterEntity;
import org.millenaire.faction.crime.hunter.BountyHunterPhase;

/**
 * Custom bounty hunter — wide-brim hat, iron mask, layered trench coat, gear belt.
 *
 * <pre>
 * 256x256 UV atlas:
 *   0,0     mask / face              32x32
 *   32,0    hat crown / brim         96x48
 *   128,0   scarf / badge            48x32
 *   0,48    torso / armor / coat     96x48
 *   96,48   belt / pouches           64x32
 *   0,96    coat panels (4)          128x48
 *   0,144   arms / bracers           128x48
 *   128,144 legs / boots             96x48
 *   0,192   equipment (rope/cuffs)   96x48
 *   96,192  lantern / bola           64x48
 * Emissive islands:
 *   200,40  eyes                     16x8
 *   200,52  lantern glow             16x16
 *   220,52  badge glow               12x12
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class BountyHunterModel extends EntityModel<BountyHunterEntity> {
   private final ModelPart root;
   private final ModelPart pelvis;
   private final ModelPart torso;
   private final ModelPart headRoot;
   private final ModelPart hatBrim;
   private final ModelPart rightShoulder;
   private final ModelPart rightUpperArm;
   private final ModelPart rightForearm;
   private final ModelPart rightHand;
   private final ModelPart leftShoulder;
   private final ModelPart leftUpperArm;
   private final ModelPart leftForearm;
   private final ModelPart leftHand;
   private final ModelPart rightThigh;
   private final ModelPart rightLowerLeg;
   private final ModelPart leftThigh;
   private final ModelPart leftLowerLeg;
   private final ModelPart coatFrontLeft;
   private final ModelPart coatFrontRight;
   private final ModelPart coatRearLeft;
   private final ModelPart coatRearRight;
   private final ModelPart lantern;
   private final ModelPart bola;
   private final ModelPart handcuffs;
   private final ModelPart rope;

   public BountyHunterModel(ModelPart root) {
      this.root = root;
      this.pelvis = root.getChild("pelvis");
      this.torso = this.pelvis.getChild("torso");
      this.headRoot = this.torso.getChild("head_root");
      this.hatBrim = this.headRoot.getChild("hat_brim");

      this.rightShoulder = this.torso.getChild("right_shoulder");
      this.rightUpperArm = this.rightShoulder.getChild("right_upper_arm");
      this.rightForearm = this.rightUpperArm.getChild("right_forearm");
      this.rightHand = this.rightForearm.getChild("right_hand");
      this.leftShoulder = this.torso.getChild("left_shoulder");
      this.leftUpperArm = this.leftShoulder.getChild("left_upper_arm");
      this.leftForearm = this.leftUpperArm.getChild("left_forearm");
      this.leftHand = this.leftForearm.getChild("left_hand");

      this.rightThigh = this.pelvis.getChild("right_thigh");
      this.rightLowerLeg = this.rightThigh.getChild("right_lower_leg");
      this.leftThigh = this.pelvis.getChild("left_thigh");
      this.leftLowerLeg = this.leftThigh.getChild("left_lower_leg");

      ModelPart coat = this.pelvis.getChild("coat_root");
      this.coatFrontLeft = coat.getChild("coat_front_left");
      this.coatFrontRight = coat.getChild("coat_front_right");
      this.coatRearLeft = coat.getChild("coat_rear_left");
      this.coatRearRight = coat.getChild("coat_rear_right");

      ModelPart gear = this.pelvis.getChild("gear");
      this.lantern = gear.getChild("lantern");
      this.bola = this.rightHand.getChild("bola");
      this.handcuffs = gear.getChild("handcuffs");
      this.rope = gear.getChild("rope");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      PartDefinition pelvis = root.addOrReplaceChild(
         "pelvis",
         CubeListBuilder.create()
            .texOffs(96, 48)
            .addBox(-4.0F, -2.0F, -2.5F, 8.0F, 4.0F, 5.0F, none),
         PartPose.offset(0.0F, 24.0F, 0.0F)
      );

      buildCoat(pelvis);
      buildGear(pelvis);
      buildTorso(pelvis);
      buildLeg(pelvis, true);
      buildLeg(pelvis, false);

      return LayerDefinition.create(mesh, 256, 256);
   }

   private static void buildCoat(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition coat = pelvis.addOrReplaceChild("coat_root", CubeListBuilder.create(), PartPose.offset(0.0F, 0.5F, 0.0F));

      coat.addOrReplaceChild(
         "coat_front_left",
         CubeListBuilder.create().texOffs(0, 96).addBox(-0.5F, 0.0F, -0.4F, 3.8F, 14.0F, 0.8F, none),
         PartPose.offsetAndRotation(-2.2F, 0.0F, -2.6F, -0.04F, 0.0F, 0.05F)
      );
      coat.addOrReplaceChild(
         "coat_front_right",
         CubeListBuilder.create().texOffs(16, 96).addBox(-3.3F, 0.0F, -0.4F, 3.8F, 14.0F, 0.8F, none),
         PartPose.offsetAndRotation(2.2F, 0.0F, -2.6F, -0.04F, 0.0F, -0.05F)
      );
      coat.addOrReplaceChild(
         "coat_rear_left",
         CubeListBuilder.create()
            .texOffs(32, 96)
            .addBox(-0.5F, 0.0F, -0.4F, 4.2F, 15.0F, 0.9F, none)
            .texOffs(48, 96)
            .addBox(0.5F, 1.0F, -0.55F, 2.0F, 10.0F, 0.4F, none),
         PartPose.offsetAndRotation(-2.4F, 0.0F, 2.5F, 0.06F, 0.0F, 0.04F)
      );
      coat.addOrReplaceChild(
         "coat_rear_right",
         CubeListBuilder.create().texOffs(64, 96).addBox(-3.7F, 0.0F, -0.4F, 4.2F, 15.0F, 0.9F, none),
         PartPose.offsetAndRotation(2.4F, 0.0F, 2.5F, 0.06F, 0.0F, -0.04F)
      );
      coat.addOrReplaceChild(
         "coat_side_left",
         CubeListBuilder.create().texOffs(80, 96).addBox(-0.4F, 0.0F, -2.0F, 0.8F, 13.5F, 4.0F, none),
         PartPose.offsetAndRotation(-4.2F, 0.2F, 0.0F, 0.0F, 0.0F, 0.08F)
      );
      coat.addOrReplaceChild(
         "coat_side_right",
         CubeListBuilder.create().texOffs(90, 96).addBox(-0.4F, 0.0F, -2.0F, 0.8F, 13.5F, 4.0F, none),
         PartPose.offsetAndRotation(4.2F, 0.2F, 0.0F, 0.0F, 0.0F, -0.08F)
      );
   }

   private static void buildGear(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition gear = pelvis.addOrReplaceChild(
         "gear",
         CubeListBuilder.create()
            .texOffs(96, 56)
            .addBox(-4.5F, -1.0F, -3.0F, 9.0F, 2.0F, 6.0F, none)
            .texOffs(96, 64)
            .addBox(-4.0F, -0.5F, -3.4F, 2.2F, 2.2F, 1.2F, none)
            .texOffs(104, 64)
            .addBox(1.8F, -0.5F, -3.4F, 2.2F, 2.2F, 1.2F, none),
         PartPose.offset(0.0F, -1.0F, 0.0F)
      );

      gear.addOrReplaceChild(
         "rope",
         CubeListBuilder.create().texOffs(0, 192).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F, none),
         PartPose.offsetAndRotation(-4.2F, 0.5F, 0.5F, 0.15F, 0.0F, 0.2F)
      );
      gear.addOrReplaceChild(
         "handcuffs",
         CubeListBuilder.create()
            .texOffs(16, 192)
            .addBox(-2.0F, 0.0F, -0.8F, 4.0F, 3.5F, 1.6F, none)
            .texOffs(32, 192)
            .addBox(-1.8F, 3.2F, -0.5F, 1.2F, 2.5F, 1.0F, none)
            .texOffs(36, 192)
            .addBox(0.6F, 3.2F, -0.5F, 1.2F, 2.5F, 1.0F, none),
         PartPose.offset(0.0F, 0.8F, 3.2F)
      );
      gear.addOrReplaceChild(
         "lantern",
         CubeListBuilder.create()
            .texOffs(96, 192)
            .addBox(-1.4F, 0.0F, -1.4F, 2.8F, 1.2F, 2.8F, none)
            .texOffs(200, 52)
            .addBox(-1.1F, 1.1F, -1.1F, 2.2F, 2.6F, 2.2F, none)
            .texOffs(108, 192)
            .addBox(-1.3F, 3.6F, -1.3F, 2.6F, 0.8F, 2.6F, none),
         PartPose.offsetAndRotation(4.0F, 0.6F, 1.8F, 0.1F, 0.0F, -0.15F)
      );
   }

   private static void buildTorso(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition torso = pelvis.addOrReplaceChild(
         "torso",
         CubeListBuilder.create()
            .texOffs(0, 48)
            .addBox(-4.0F, -12.0F, -2.8F, 8.0F, 12.0F, 5.6F, none)
            .texOffs(28, 48)
            .addBox(-4.5F, -12.2F, -3.1F, 9.0F, 4.0F, 6.2F, none)
            .texOffs(56, 48)
            .addBox(-5.2F, -12.0F, -2.0F, 2.2F, 3.5F, 4.0F, none)
            .texOffs(68, 48)
            .addBox(3.0F, -12.0F, -2.0F, 2.2F, 3.5F, 4.0F, none),
         PartPose.offset(0.0F, -2.0F, 0.0F)
      );

      torso.addOrReplaceChild(
         "bounty_badge",
         CubeListBuilder.create()
            .texOffs(128, 0)
            .addBox(-1.4F, -1.4F, -0.4F, 2.8F, 2.8F, 0.8F, none)
            .texOffs(220, 52)
            .addBox(-0.9F, -0.9F, -0.55F, 1.8F, 1.8F, 0.5F, none),
         PartPose.offset(2.2F, -9.5F, -3.2F)
      );
      torso.addOrReplaceChild(
         "scarf",
         CubeListBuilder.create().texOffs(128, 8).addBox(-3.5F, -1.5F, -3.2F, 7.0F, 3.5F, 6.4F, none),
         PartPose.offset(0.0F, -12.2F, 0.0F)
      );

      buildHead(torso);
      buildArm(torso, true);
      buildArm(torso, false);
   }

   private static void buildHead(PartDefinition torso) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition head = torso.addOrReplaceChild(
         "head_root",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-3.5F, -7.5F, -3.5F, 7.0F, 7.5F, 7.0F, none)
            .texOffs(0, 16)
            .addBox(-3.2F, -6.8F, -3.9F, 6.4F, 6.5F, 1.2F, none)
            .texOffs(16, 16)
            .addBox(-1.0F, -5.5F, -4.2F, 2.0F, 4.5F, 0.8F, none),
         PartPose.offset(0.0F, -12.5F, 0.0F)
      );

      head.addOrReplaceChild(
         "eye_left",
         CubeListBuilder.create().texOffs(200, 40).addBox(-0.85F, -0.7F, -0.35F, 1.7F, 1.3F, 0.7F, none),
         PartPose.offset(1.55F, -4.6F, -3.95F)
      );
      head.addOrReplaceChild(
         "eye_right",
         CubeListBuilder.create().texOffs(208, 40).addBox(-0.85F, -0.7F, -0.35F, 1.7F, 1.3F, 0.7F, none),
         PartPose.offset(-1.55F, -4.6F, -3.95F)
      );

      head.addOrReplaceChild(
         "hat_crown",
         CubeListBuilder.create()
            .texOffs(32, 0)
            .addBox(-4.0F, -3.5F, -4.0F, 8.0F, 3.5F, 8.0F, none)
            .texOffs(32, 12)
            .addBox(-3.5F, -4.2F, -3.5F, 7.0F, 1.0F, 7.0F, none),
         PartPose.offset(0.0F, -7.2F, 0.0F)
      );
      head.addOrReplaceChild(
         "hat_brim",
         CubeListBuilder.create()
            .texOffs(32, 24)
            .addBox(-7.5F, -0.4F, -7.5F, 15.0F, 0.8F, 15.0F, none)
            .texOffs(32, 40)
            .addBox(-6.5F, -0.55F, -6.5F, 13.0F, 0.4F, 2.0F, none),
         PartPose.offset(0.0F, -7.0F, 0.0F)
      );
   }

   private static void buildArm(PartDefinition torso, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;
      int u = right ? 0 : 32;

      PartDefinition shoulder = torso.addOrReplaceChild(
         side + "_shoulder",
         CubeListBuilder.create().texOffs(u, 144).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, none),
         PartPose.offset(4.5F * s, -10.5F, 0.0F)
      );
      PartDefinition upper = shoulder.addOrReplaceChild(
         side + "_upper_arm",
         CubeListBuilder.create().texOffs(u, 152).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F, none),
         PartPose.offset(0.0F, 1.5F, 0.0F)
      );
      PartDefinition forearm = upper.addOrReplaceChild(
         side + "_forearm",
         CubeListBuilder.create()
            .texOffs(u, 164)
            .addBox(-1.4F, 0.0F, -1.4F, 2.8F, 6.5F, 2.8F, none)
            .texOffs(u + 12, 164)
            .addBox(-1.8F, 0.5F, -1.8F, 3.6F, 4.0F, 3.6F, none),
         PartPose.offset(0.0F, 6.5F, 0.0F)
      );
      PartDefinition hand = forearm.addOrReplaceChild(
         side + "_hand",
         CubeListBuilder.create().texOffs(u, 176).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.8F, 3.0F, none),
         PartPose.offset(0.0F, 6.2F, 0.0F)
      );

      if (right) {
         hand.addOrReplaceChild(
            "bola",
            CubeListBuilder.create()
               .texOffs(96, 208)
               .addBox(-0.6F, -0.6F, -6.0F, 1.2F, 1.2F, 6.5F, none)
               .texOffs(112, 208)
               .addBox(-1.2F, -1.2F, -7.2F, 2.4F, 2.4F, 1.8F, none)
               .texOffs(120, 208)
               .addBox(-2.5F, -0.5F, -8.5F, 1.4F, 1.4F, 1.4F, none)
               .texOffs(120, 212)
               .addBox(1.1F, -0.5F, -8.5F, 1.4F, 1.4F, 1.4F, none)
               .texOffs(120, 216)
               .addBox(-0.7F, 1.0F, -8.5F, 1.4F, 1.4F, 1.4F, none),
            PartPose.offsetAndRotation(0.0F, 1.2F, 0.0F, -1.2F, 0.0F, 0.0F)
         );
      }
   }

   private static void buildLeg(PartDefinition pelvis, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;
      int u = right ? 128 : 160;

      PartDefinition thigh = pelvis.addOrReplaceChild(
         side + "_thigh",
         CubeListBuilder.create().texOffs(u, 144).addBox(-1.8F, 0.0F, -1.8F, 3.6F, 7.5F, 3.6F, none),
         PartPose.offset(2.0F * s, 0.5F, 0.0F)
      );
      thigh.addOrReplaceChild(
         side + "_lower_leg",
         CubeListBuilder.create()
            .texOffs(u, 156)
            .addBox(-1.6F, 0.0F, -1.6F, 3.2F, 7.0F, 3.2F, none)
            .texOffs(u, 168)
            .addBox(-2.0F, 5.5F, -2.5F, 4.0F, 2.5F, 4.5F, none),
         PartPose.offset(0.0F, 7.2F, 0.0F)
      );
   }

   @Override
   public void setupAnim(
      BountyHunterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      this.root.getAllParts().forEach(ModelPart::resetPose);

      float walk = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount;
      float walkOpp = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * limbSwingAmount;
      float breath = Mth.sin(ageInTicks * 0.08F) * 0.02F;

      this.headRoot.yRot = netHeadYaw * ((float) Math.PI / 180F);
      this.headRoot.xRot = headPitch * ((float) Math.PI / 180F);
      this.hatBrim.zRot = Mth.sin(ageInTicks * 0.05F) * 0.02F;

      this.torso.xRot = breath;
      this.torso.yRot = walk * 0.08F;

      this.rightThigh.xRot = walk * 1.1F;
      this.leftThigh.xRot = walkOpp * 1.1F;
      this.rightLowerLeg.xRot = Math.max(0.0F, -walk) * 0.7F;
      this.leftLowerLeg.xRot = Math.max(0.0F, -walkOpp) * 0.7F;

      this.rightShoulder.xRot = walkOpp * 0.85F;
      this.leftShoulder.xRot = walk * 0.85F;
      this.rightForearm.xRot = -0.15F - Math.max(0.0F, walkOpp) * 0.3F;
      this.leftForearm.xRot = -0.15F - Math.max(0.0F, walk) * 0.3F;

      float coatSwing = walk * 0.25F + Mth.sin(ageInTicks * 0.12F) * 0.03F;
      this.coatFrontLeft.xRot = -0.04F + coatSwing * 0.4F;
      this.coatFrontRight.xRot = -0.04F - coatSwing * 0.4F;
      this.coatRearLeft.xRot = 0.06F - coatSwing * 0.5F;
      this.coatRearRight.xRot = 0.06F + coatSwing * 0.5F;

      this.lantern.zRot = Mth.sin(ageInTicks * 0.15F) * 0.08F + coatSwing * 0.2F;
      this.rope.zRot = 0.2F + Mth.sin(ageInTicks * 0.1F) * 0.05F;
      this.handcuffs.xRot = Mth.sin(ageInTicks * 0.08F) * 0.04F;

      int anim = entity.getAnimState();
      float progress = entity.getAnimProgress(0.0F);
      BountyHunterPhase phase = entity.getHunterPhase();

      if (phase == BountyHunterPhase.OBSERVING || anim == BountyHunterEntity.ANIM_OBSERVE) {
         this.headRoot.xRot += -0.15F;
         this.rightShoulder.xRot = -0.2F;
         this.leftShoulder.xRot = -0.15F;
      }
      if (anim == BountyHunterEntity.ANIM_WARNING || phase == BountyHunterPhase.APPROACHING) {
         this.rightShoulder.xRot = -0.9F - progress * 0.3F;
         this.rightForearm.xRot = -0.4F;
         this.headRoot.xRot += -0.1F;
      }
      if (anim == BountyHunterEntity.ANIM_BATON) {
         float swing = Mth.sin(progress * (float) Math.PI);
         this.rightShoulder.xRot = -1.2F * swing;
         this.rightForearm.xRot = -0.5F * swing;
      }
      if (anim == BountyHunterEntity.ANIM_BOLA) {
         float throwP = Mth.sin(progress * (float) Math.PI);
         this.rightShoulder.xRot = -1.6F * throwP;
         this.rightForearm.xRot = -0.2F;
         this.bola.xRot = -1.2F - throwP * 0.8F;
      }
      if (anim == BountyHunterEntity.ANIM_HANDCUFF || phase == BountyHunterPhase.RESTRAINING) {
         this.rightShoulder.xRot = -0.7F;
         this.leftShoulder.xRot = -0.7F;
         this.rightForearm.xRot = -0.6F;
         this.leftForearm.xRot = -0.6F;
         this.rightHand.yRot = -0.4F;
         this.leftHand.yRot = 0.4F;
      }

      float hurt = entity.hurtTime > 0 ? 0.15F : 0.0F;
      this.torso.zRot += hurt * (entity.tickCount % 2 == 0 ? 1 : -1);

      if (entity.isDeadOrDying()) {
         float death = Mth.clamp(entity.deathTime / 20.0F, 0.0F, 1.0F);
         this.root.zRot = death * 1.4F;
         this.root.y += death * 10.0F;
         this.lantern.xRot = death * 0.8F;
      }

      boolean running = limbSwingAmount > 0.55F || phase == BountyHunterPhase.PURSUING;
      if (running) {
         this.coatFrontLeft.xRot -= 0.15F;
         this.coatFrontRight.xRot -= 0.15F;
         this.coatRearLeft.xRot += 0.25F;
         this.coatRearRight.xRot += 0.25F;
      }
   }

   @Override
   public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
      this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
   }
}
