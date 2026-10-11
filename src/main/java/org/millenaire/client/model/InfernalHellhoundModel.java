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
import org.millenaire.entity.InfernalHellhoundEntity;

/**
 * Production Infernal Hellhound — giant demonic quadruped wolf.
 * Dedicated geometry (not a vanilla wolf reskin): oversized skeletal skull with
 * independent jaw, bone armor plating over charred flesh, exposed rib/core,
 * varied dorsal spikes, fully articulated legs, and a long segmented spiked tail.
 *
 * Scale: shoulder ~1.3–1.5 blocks, body length ~2.3–2.8 blocks. UV 256×256;
 * emissive islands use texOffs ≥ 200.
 */
@OnlyIn(Dist.CLIENT)
public class InfernalHellhoundModel extends EntityModel<InfernalHellhoundEntity> {
   private final ModelPart root;
   private final ModelPart pelvis;
   private final ModelPart spineLower;
   private final ModelPart spineMid;
   private final ModelPart chest;
   private final ModelPart infernalCore;
   private final ModelPart neck;
   private final ModelPart head;
   private final ModelPart jaw;

   private final ModelPart flUpper;
   private final ModelPart flJoint;
   private final ModelPart flLower;
   private final ModelPart flPaw;
   private final ModelPart frUpper;
   private final ModelPart frJoint;
   private final ModelPart frLower;
   private final ModelPart frPaw;
   private final ModelPart blUpper;
   private final ModelPart blJoint;
   private final ModelPart blLower;
   private final ModelPart blPaw;
   private final ModelPart brUpper;
   private final ModelPart brJoint;
   private final ModelPart brLower;
   private final ModelPart brPaw;

   private final ModelPart tailBase;
   private final ModelPart tailMid;
   private final ModelPart tailMid2;
   private final ModelPart tailTip;

   private static final float PELVIS_Y = 17.0F;
   private static final float PELVIS_Z = 6.0F;

   public InfernalHellhoundModel(ModelPart root) {
      this.root = root;
      this.pelvis = root.getChild("pelvis");
      this.spineLower = this.pelvis.getChild("spine_lower");
      this.spineMid = this.spineLower.getChild("spine_mid");
      this.chest = this.spineMid.getChild("chest");
      this.infernalCore = this.chest.getChild("infernal_core");
      this.neck = this.chest.getChild("neck");
      this.head = this.neck.getChild("head");
      this.jaw = this.head.getChild("jaw");

      this.flUpper = this.chest.getChild("fl_upper");
      this.flJoint = this.flUpper.getChild("fl_joint");
      this.flLower = this.flJoint.getChild("fl_lower");
      this.flPaw = this.flLower.getChild("fl_paw");
      this.frUpper = this.chest.getChild("fr_upper");
      this.frJoint = this.frUpper.getChild("fr_joint");
      this.frLower = this.frJoint.getChild("fr_lower");
      this.frPaw = this.frLower.getChild("fr_paw");

      this.blUpper = this.pelvis.getChild("bl_upper");
      this.blJoint = this.blUpper.getChild("bl_joint");
      this.blLower = this.blJoint.getChild("bl_lower");
      this.blPaw = this.blLower.getChild("bl_paw");
      this.brUpper = this.pelvis.getChild("br_upper");
      this.brJoint = this.brUpper.getChild("br_joint");
      this.brLower = this.brJoint.getChild("br_lower");
      this.brPaw = this.brLower.getChild("br_paw");

      this.tailBase = this.pelvis.getChild("tail_base");
      this.tailMid = this.tailBase.getChild("tail_mid");
      this.tailMid2 = this.tailMid.getChild("tail_mid2");
      this.tailTip = this.tailMid2.getChild("tail_tip");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      // Ground ≈ y24. Shoulder ≈ 1.4 blocks; body along −Z (front) / +Z (rear).
      PartDefinition pelvis = root.addOrReplaceChild(
         "pelvis",
         CubeListBuilder.create()
            .texOffs(0, 80)
            .addBox(-4.0F, -3.5F, -3.5F, 8.0F, 6.0F, 7.0F, none)
            .texOffs(30, 80)
            .addBox(-3.0F, -4.5F, -2.0F, 6.0F, 2.0F, 5.0F, none)
            .texOffs(52, 80)
            .addBox(-4.5F, -2.0F, 0.5F, 2.0F, 4.0F, 3.0F, none)
            .texOffs(62, 80)
            .addBox(2.5F, -2.0F, 0.5F, 2.0F, 4.0F, 3.0F, none),
         PartPose.offset(0.0F, PELVIS_Y, PELVIS_Z)
      );

      PartDefinition spineLower = pelvis.addOrReplaceChild(
         "spine_lower",
         CubeListBuilder.create()
            .texOffs(0, 94)
            .addBox(-3.0F, -3.0F, -6.0F, 6.0F, 5.0F, 7.0F, none)
            .texOffs(26, 94)
            .addBox(-1.5F, -4.0F, -5.0F, 3.0F, 2.0F, 5.0F, none),
         PartPose.offsetAndRotation(0.0F, -1.0F, -3.0F, -0.08F, 0.0F, 0.0F)
      );

      PartDefinition spineMid = spineLower.addOrReplaceChild(
         "spine_mid",
         CubeListBuilder.create()
            .texOffs(0, 108)
            .addBox(-3.5F, -3.5F, -7.0F, 7.0F, 6.0F, 8.0F, none)
            .texOffs(30, 108)
            .addBox(-2.0F, -4.5F, -6.0F, 4.0F, 2.0F, 6.0F, none)
            .texOffs(50, 108)
            .addBox(-4.0F, -1.5F, -5.5F, 1.5F, 3.5F, 5.0F, none)
            .texOffs(62, 108)
            .addBox(2.5F, -1.5F, -5.5F, 1.5F, 3.5F, 5.0F, none),
         PartPose.offsetAndRotation(0.0F, -0.5F, -5.5F, -0.1F, 0.0F, 0.0F)
      );

      PartDefinition chest = spineMid.addOrReplaceChild(
         "chest",
         CubeListBuilder.create()
            .texOffs(0, 124)
            .addBox(-5.0F, -4.5F, -7.0F, 10.0F, 8.0F, 8.0F, none)
            .texOffs(36, 124)
            .addBox(-4.0F, -5.5F, -6.0F, 8.0F, 2.0F, 6.0F, none)
            .texOffs(64, 124)
            .addBox(-5.5F, -3.0F, -5.5F, 1.5F, 5.0F, 5.0F, none)
            .texOffs(78, 124)
            .addBox(4.0F, -3.0F, -5.5F, 1.5F, 5.0F, 5.0F, none),
         PartPose.offsetAndRotation(0.0F, -1.0F, -6.5F, -0.06F, 0.0F, 0.0F)
      );

      // Bone rib plates — leave center open for glowing core
      addRibPlate(chest, "rib_l1", 1.8F, -2.5F, -5.5F, -0.1F, -0.55F, -0.25F, 0, 142);
      addRibPlate(chest, "rib_l2", 2.0F, -0.5F, -5.0F, 0.05F, -0.5F, -0.2F, 16, 142);
      addRibPlate(chest, "rib_l3", 1.7F, 1.5F, -4.5F, 0.2F, -0.45F, -0.15F, 32, 142);
      addRibPlate(chest, "rib_r1", -1.8F, -2.5F, -5.5F, -0.1F, 0.55F, 0.25F, 0, 150);
      addRibPlate(chest, "rib_r2", -2.0F, -0.5F, -5.0F, 0.05F, 0.5F, 0.2F, 16, 150);
      addRibPlate(chest, "rib_r3", -1.7F, 1.5F, -4.5F, 0.2F, 0.45F, 0.15F, 32, 150);

      chest.addOrReplaceChild(
         "sternum_gap",
         CubeListBuilder.create().texOffs(200, 0).addBox(-1.5F, -3.0F, -1.0F, 3.0F, 6.0F, 2.0F, none),
         PartPose.offset(0.0F, 0.0F, -6.2F)
      );
      chest.addOrReplaceChild(
         "infernal_core",
         CubeListBuilder.create()
            .texOffs(208, 0)
            .addBox(-2.0F, -2.5F, -2.0F, 4.0F, 5.0F, 4.0F, none)
            .texOffs(224, 0)
            .addBox(-1.25F, -3.5F, -1.25F, 2.5F, 2.0F, 2.5F, none)
            .texOffs(236, 0)
            .addBox(-0.75F, 2.0F, -0.75F, 1.5F, 2.0F, 1.5F, none),
         PartPose.offset(0.0F, 0.0F, -3.5F)
      );

      // Shoulder armor plates
      chest.addOrReplaceChild(
         "scapula_l",
         CubeListBuilder.create().texOffs(92, 80).addBox(0.0F, -2.0F, -2.5F, 5.0F, 4.0F, 5.0F, none),
         PartPose.offsetAndRotation(3.5F, -2.5F, -2.0F, 0.1F, 0.25F, -0.35F)
      );
      chest.addOrReplaceChild(
         "scapula_r",
         CubeListBuilder.create().texOffs(112, 80).addBox(-5.0F, -2.0F, -2.5F, 5.0F, 4.0F, 5.0F, none),
         PartPose.offsetAndRotation(-3.5F, -2.5F, -2.0F, 0.1F, -0.25F, 0.35F)
      );

      PartDefinition neck = chest.addOrReplaceChild(
         "neck",
         CubeListBuilder.create()
            .texOffs(0, 160)
            .addBox(-2.5F, -3.0F, -5.0F, 5.0F, 5.0F, 6.0F, none)
            .texOffs(22, 160)
            .addBox(-1.5F, -4.0F, -4.0F, 3.0F, 2.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, -1.5F, -6.5F, 0.35F, 0.0F, 0.0F)
      );

      buildHead(neck);
      buildFrontLeg(chest, true);
      buildFrontLeg(chest, false);
      buildHindLeg(pelvis, true);
      buildHindLeg(pelvis, false);
      buildTail(pelvis);
      buildNeckSpikes(neck);
      buildBackSpikes(chest, spineMid, spineLower);

      return LayerDefinition.create(mesh, 256, 256);
   }

   private static void addRibPlate(PartDefinition chest, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v) {
      PartDefinition rib = chest.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(0.0F, -1.0F, -1.0F, 4.5F, 2.0F, 2.0F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      rib.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(u, v + 4).addBox(0.0F, -0.7F, -0.7F, 3.0F, 1.4F, 1.4F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(4.2F, 0.0F, 0.0F, 0.0F, 0.0F, name.contains("_l") ? 0.5F : -0.5F)
      );
      rib.addOrReplaceChild(
         "glow",
         CubeListBuilder.create().texOffs(200, 10).addBox(-0.5F, -0.4F, -0.4F, 2.5F, 0.8F, 0.8F, CubeDeformation.NONE),
         PartPose.offset(1.5F, 0.0F, 0.0F)
      );
   }

   private static void buildHead(PartDefinition neck) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition head = neck.addOrReplaceChild(
         "head",
         CubeListBuilder.create(),
         PartPose.offsetAndRotation(0.0F, -0.5F, -4.5F, -0.25F, 0.0F, 0.0F)
      );

      // Oversized skeletal cranium
      head.addOrReplaceChild(
         "cranium",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0F, -6.5F, -6.0F, 10.0F, 7.0F, 10.0F, none)
            .texOffs(40, 0)
            .addBox(-4.0F, -8.0F, -4.5F, 8.0F, 2.0F, 8.0F, none)
            .texOffs(72, 0)
            .addBox(-3.5F, -5.5F, -8.5F, 7.0F, 4.0F, 3.0F, none),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "brow_ridge",
         CubeListBuilder.create()
            .texOffs(0, 18)
            .addBox(-5.5F, -1.5F, -2.0F, 11.0F, 2.5F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, -5.0F, -5.5F, -0.2F, 0.0F, 0.0F)
      );
      head.addOrReplaceChild(
         "zygomatic_l",
         CubeListBuilder.create().texOffs(30, 18).addBox(-1.0F, -2.0F, -2.5F, 2.5F, 4.0F, 5.0F, none),
         PartPose.offsetAndRotation(4.8F, -2.0F, -2.0F, 0.0F, -0.15F, -0.1F)
      );
      head.addOrReplaceChild(
         "zygomatic_r",
         CubeListBuilder.create().texOffs(46, 18).addBox(-1.5F, -2.0F, -2.5F, 2.5F, 4.0F, 5.0F, none),
         PartPose.offsetAndRotation(-4.8F, -2.0F, -2.0F, 0.0F, 0.15F, 0.1F)
      );
      head.addOrReplaceChild(
         "muzzle",
         CubeListBuilder.create()
            .texOffs(64, 12)
            .addBox(-3.0F, -2.0F, -7.0F, 6.0F, 3.5F, 7.0F, none)
            .texOffs(90, 12)
            .addBox(-2.5F, -2.5F, -8.5F, 5.0F, 2.5F, 2.0F, none),
         PartPose.offset(0.0F, -1.5F, -6.5F)
      );
      head.addOrReplaceChild(
         "teeth_upper",
         CubeListBuilder.create()
            .texOffs(100, 0)
            .addBox(-2.8F, 0.0F, -1.0F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(104, 0)
            .addBox(-1.2F, 0.0F, -1.5F, 1.0F, 4.0F, 1.0F, none)
            .texOffs(108, 0)
            .addBox(0.2F, 0.0F, -1.5F, 1.0F, 4.0F, 1.0F, none)
            .texOffs(112, 0)
            .addBox(1.8F, 0.0F, -1.0F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(116, 0)
            .addBox(-2.0F, 0.0F, 1.0F, 0.8F, 2.5F, 0.8F, none)
            .texOffs(120, 0)
            .addBox(1.2F, 0.0F, 1.0F, 0.8F, 2.5F, 0.8F, none),
         PartPose.offset(0.0F, 0.5F, -12.5F)
      );
      head.addOrReplaceChild(
         "mouth_cavity",
         CubeListBuilder.create().texOffs(200, 16).addBox(-2.5F, -1.5F, -5.0F, 5.0F, 3.0F, 6.0F, none),
         PartPose.offset(0.0F, 0.5F, -8.0F)
      );
      head.addOrReplaceChild(
         "eye_l",
         CubeListBuilder.create().texOffs(200, 28).addBox(-1.2F, -1.0F, -0.5F, 2.4F, 1.8F, 1.0F, none),
         PartPose.offset(2.6F, -3.8F, -6.4F)
      );
      head.addOrReplaceChild(
         "eye_r",
         CubeListBuilder.create().texOffs(208, 28).addBox(-1.2F, -1.0F, -0.5F, 2.4F, 1.8F, 1.0F, none),
         PartPose.offset(-2.6F, -3.8F, -6.4F)
      );
      head.addOrReplaceChild(
         "skull_crack",
         CubeListBuilder.create().texOffs(216, 28).addBox(-0.5F, -3.0F, -0.4F, 1.0F, 4.0F, 0.8F, none),
         PartPose.offsetAndRotation(1.5F, -6.0F, -3.0F, 0.2F, 0.3F, 0.15F)
      );

      // Independent lower jaw — hinged wide by default
      PartDefinition jaw = head.addOrReplaceChild(
         "jaw",
         CubeListBuilder.create()
            .texOffs(0, 28)
            .addBox(-3.0F, 0.0F, -8.5F, 6.0F, 2.5F, 9.0F, none)
            .texOffs(30, 28)
            .addBox(-2.5F, -0.5F, -9.5F, 5.0F, 2.0F, 2.0F, none)
            .texOffs(124, 0)
            .addBox(-2.5F, -2.5F, -8.0F, 1.0F, 3.0F, 1.0F, none)
            .texOffs(128, 0)
            .addBox(-0.8F, -3.0F, -8.5F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(132, 0)
            .addBox(0.8F, -3.0F, -8.5F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(136, 0)
            .addBox(1.5F, -2.5F, -8.0F, 1.0F, 3.0F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, 1.0F, -4.0F, 0.55F, 0.0F, 0.0F)
      );
      jaw.addOrReplaceChild(
         "jaw_glow",
         CubeListBuilder.create().texOffs(200, 36).addBox(-2.2F, -0.8F, -6.5F, 4.4F, 1.2F, 5.5F, none),
         PartPose.offset(0.0F, 0.8F, 0.0F)
      );
      jaw.addOrReplaceChild(
         "drool_l",
         CubeListBuilder.create().texOffs(220, 36).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 3.5F, 0.8F, none),
         PartPose.offsetAndRotation(-1.8F, 2.2F, -7.5F, 0.15F, 0.0F, 0.1F)
      );
      jaw.addOrReplaceChild(
         "drool_r",
         CubeListBuilder.create().texOffs(224, 36).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 3.0F, 0.8F, none),
         PartPose.offsetAndRotation(1.5F, 2.2F, -7.0F, 0.2F, 0.0F, -0.12F)
      );
   }

   private static void buildFrontLeg(PartDefinition chest, boolean left) {
      CubeDeformation none = CubeDeformation.NONE;
      String p = left ? "fl" : "fr";
      float s = left ? 1.0F : -1.0F;

      PartDefinition upper = chest.addOrReplaceChild(
         p + "_upper",
         CubeListBuilder.create()
            .texOffs(0, 172)
            .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F, none)
            .texOffs(20, 172)
            .addBox(-3.0F, -1.0F, -3.0F, 6.0F, 3.0F, 6.0F, none),
         PartPose.offsetAndRotation(4.2F * s, 2.0F, -3.5F, 0.15F, 0.0F, 0.08F * s)
      );
      PartDefinition joint = upper.addOrReplaceChild(
         p + "_joint",
         CubeListBuilder.create().texOffs(44, 172).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.5F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
      PartDefinition lower = joint.addOrReplaceChild(
         p + "_lower",
         CubeListBuilder.create()
            .texOffs(60, 172)
            .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 7.0F, 3.5F, none)
            .texOffs(74, 172)
            .addBox(-2.25F, 2.0F, -2.25F, 4.5F, 3.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.55F, 0.0F, 0.0F)
      );
      PartDefinition paw = lower.addOrReplaceChild(
         p + "_paw",
         CubeListBuilder.create().texOffs(88, 172).addBox(-2.25F, -1.0F, -4.5F, 4.5F, 2.5F, 6.0F, none),
         PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, 0.05F, 0.0F, 0.0F)
      );
      addClaws(paw, p, s, true);
   }

   private static void buildHindLeg(PartDefinition pelvis, boolean left) {
      CubeDeformation none = CubeDeformation.NONE;
      String p = left ? "bl" : "br";
      float s = left ? 1.0F : -1.0F;

      // Digitigrade Z-bend — ready to sprint
      PartDefinition upper = pelvis.addOrReplaceChild(
         p + "_upper",
         CubeListBuilder.create()
            .texOffs(0, 192)
            .addBox(-2.25F, 0.0F, -2.25F, 4.5F, 6.5F, 4.5F, none)
            .texOffs(18, 192)
            .addBox(-2.75F, -0.5F, -2.75F, 5.5F, 3.0F, 3.5F, none),
         PartPose.offsetAndRotation(3.5F * s, 1.5F, 1.5F, -0.65F, 0.0F, 0.06F * s)
      );
      PartDefinition joint = upper.addOrReplaceChild(
         p + "_joint",
         CubeListBuilder.create().texOffs(40, 192).addBox(-1.75F, -1.5F, -1.75F, 3.5F, 3.5F, 3.5F, none),
         PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 1.25F, 0.0F, 0.0F)
      );
      PartDefinition lower = joint.addOrReplaceChild(
         p + "_lower",
         CubeListBuilder.create()
            .texOffs(54, 192)
            .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.5F, 3.0F, none)
            .texOffs(66, 192)
            .addBox(-2.0F, 3.0F, -2.0F, 4.0F, 2.5F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.85F, 0.0F, 0.0F)
      );
      PartDefinition paw = lower.addOrReplaceChild(
         p + "_paw",
         CubeListBuilder.create().texOffs(80, 192).addBox(-2.0F, -1.0F, -4.0F, 4.0F, 2.5F, 5.5F, none),
         PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.25F, 0.0F, 0.0F)
      );
      addClaws(paw, p, s, false);
   }

   private static void addClaws(PartDefinition paw, String prefix, float side, boolean front) {
      CubeDeformation none = CubeDeformation.NONE;
      float len = front ? 5.0F : 4.5F;
      float z = front ? -4.2F : -3.8F;
      float spread = front ? 1.5F : 1.3F;

      for (int i = 0; i < 3; i++) {
         float x = (i - 1) * spread;
         float yaw = (1 - i) * 0.18F;
         float pitch = 0.25F + (i == 1 ? 0.08F : 0.0F);
         int u = 200 + i * 8;
         paw.addOrReplaceChild(
            prefix + "_claw_" + i,
            CubeListBuilder.create()
               .texOffs(u, 48)
               .addBox(-0.6F, -0.5F, -len, 1.2F, 1.2F, len, none)
               .texOffs(u, 56)
               .addBox(-0.4F, -0.35F, -len - 2.0F, 0.8F, 0.8F, 2.5F, none),
            PartPose.offsetAndRotation(x, 0.6F, z, pitch, yaw * side, 0.0F)
         );
      }
   }

   private static void buildTail(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition base = pelvis.addOrReplaceChild(
         "tail_base",
         CubeListBuilder.create()
            .texOffs(140, 80)
            .addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 6.0F, none)
            .texOffs(200, 64)
            .addBox(-1.0F, -0.5F, 2.0F, 2.0F, 1.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, -1.5F, 3.0F, 0.45F, 0.0F, 0.0F)
      );
      base.addOrReplaceChild(
         "tail_spike_0",
         CubeListBuilder.create()
            .texOffs(160, 80)
            .addBox(-0.75F, -3.5F, -0.75F, 1.5F, 3.5F, 1.5F, none)
            .texOffs(200, 70)
            .addBox(-0.4F, -5.0F, -0.4F, 0.8F, 2.0F, 0.8F, none),
         PartPose.offsetAndRotation(0.0F, -1.5F, 3.0F, -0.35F, 0.0F, 0.1F)
      );

      PartDefinition mid = base.addOrReplaceChild(
         "tail_mid",
         CubeListBuilder.create()
            .texOffs(140, 92)
            .addBox(-1.75F, -1.75F, 0.0F, 3.5F, 3.5F, 6.5F, none)
            .texOffs(208, 64)
            .addBox(-0.85F, -0.4F, 2.5F, 1.7F, 0.8F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, 0.35F, 0.0F, 0.0F)
      );
      mid.addOrReplaceChild(
         "tail_spike_1",
         CubeListBuilder.create()
            .texOffs(160, 88)
            .addBox(-0.65F, -3.0F, -0.65F, 1.3F, 3.0F, 1.3F, none)
            .texOffs(204, 70)
            .addBox(-0.35F, -4.5F, -0.35F, 0.7F, 1.8F, 0.7F, none),
         PartPose.offsetAndRotation(0.3F, -1.2F, 3.0F, -0.4F, 0.15F, -0.1F)
      );

      PartDefinition mid2 = mid.addOrReplaceChild(
         "tail_mid2",
         CubeListBuilder.create()
            .texOffs(140, 104)
            .addBox(-1.4F, -1.4F, 0.0F, 2.8F, 2.8F, 6.0F, none)
            .texOffs(216, 64)
            .addBox(-0.7F, -0.35F, 2.0F, 1.4F, 0.7F, 2.5F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.4F, 0.0F, 0.0F)
      );
      mid2.addOrReplaceChild(
         "tail_spike_2",
         CubeListBuilder.create()
            .texOffs(160, 96)
            .addBox(-0.55F, -2.5F, -0.55F, 1.1F, 2.5F, 1.1F, none)
            .texOffs(208, 70)
            .addBox(-0.3F, -3.8F, -0.3F, 0.6F, 1.5F, 0.6F, none),
         PartPose.offsetAndRotation(-0.2F, -1.0F, 2.5F, -0.45F, -0.2F, 0.15F)
      );

      PartDefinition tip = mid2.addOrReplaceChild(
         "tail_tip",
         CubeListBuilder.create()
            .texOffs(140, 116)
            .addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.5F, none)
            .texOffs(224, 64)
            .addBox(-0.55F, -0.3F, 1.5F, 1.1F, 0.6F, 2.5F, none)
            .texOffs(140, 126)
            .addBox(-0.6F, -0.6F, 5.0F, 1.2F, 1.2F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, 0.5F, 0.0F, 0.0F)
      );
      tip.addOrReplaceChild(
         "tail_spike_3",
         CubeListBuilder.create()
            .texOffs(160, 104)
            .addBox(-0.45F, -2.0F, -0.45F, 0.9F, 2.0F, 0.9F, none)
            .texOffs(212, 70)
            .addBox(-0.25F, -3.2F, -0.25F, 0.5F, 1.4F, 0.5F, none),
         PartPose.offsetAndRotation(0.15F, -0.8F, 2.0F, -0.5F, 0.1F, -0.2F)
      );
      tip.addOrReplaceChild(
         "tip_glow",
         CubeListBuilder.create().texOffs(232, 64).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 2.5F, none),
         PartPose.offset(0.0F, 0.0F, 7.5F)
      );
   }

   private static void buildNeckSpikes(PartDefinition neck) {
      // Varied cluster at nape — not identical
      addSpike(neck, "neck_spike_0", -1.2F, -3.5F, -1.0F, -0.55F, -0.2F, -0.25F, 4.5F, 1.4F, 168, 80, true);
      addSpike(neck, "neck_spike_1", 0.3F, -4.0F, -0.2F, -0.75F, 0.05F, 0.1F, 5.5F, 1.6F, 172, 80, true);
      addSpike(neck, "neck_spike_2", 1.4F, -3.2F, 0.8F, -0.45F, 0.25F, 0.3F, 3.8F, 1.2F, 176, 80, true);
      addSpike(neck, "neck_spike_3", -0.4F, -3.0F, 1.5F, -0.35F, -0.1F, -0.05F, 3.2F, 1.0F, 180, 80, true);
   }

   private static void buildBackSpikes(PartDefinition chest, PartDefinition spineMid, PartDefinition spineLower) {
      addSpike(chest, "back_spike_0", -0.8F, -5.0F, -3.0F, -0.15F, -0.15F, -0.2F, 5.0F, 1.5F, 168, 96, true);
      addSpike(chest, "back_spike_1", 1.0F, -4.5F, -1.0F, -0.05F, 0.2F, 0.25F, 4.2F, 1.3F, 172, 96, true);
      addSpike(spineMid, "back_spike_2", 0.2F, -4.0F, -4.0F, 0.05F, 0.0F, -0.1F, 4.8F, 1.4F, 176, 96, true);
      addSpike(spineMid, "back_spike_3", -1.1F, -3.5F, -1.5F, 0.1F, -0.25F, 0.15F, 3.6F, 1.15F, 180, 96, true);
      addSpike(spineLower, "back_spike_4", 0.6F, -3.5F, -3.0F, 0.15F, 0.15F, -0.18F, 3.4F, 1.1F, 184, 96, true);
      addSpike(spineLower, "back_spike_5", -0.5F, -3.0F, -0.5F, 0.2F, -0.1F, 0.12F, 2.8F, 0.95F, 188, 96, false);
   }

   private static void addSpike(
      PartDefinition parent,
      String name,
      float x,
      float y,
      float z,
      float rx,
      float ry,
      float rz,
      float height,
      float width,
      int u,
      int v,
      boolean glowTip
   ) {
      CubeDeformation none = CubeDeformation.NONE;
      float hw = width * 0.5F;
      PartDefinition spike = parent.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(-hw, -height, -hw, width, height, width, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      float midH = height * 0.45F;
      float midW = width * 0.65F;
      PartDefinition mid = spike.addOrReplaceChild(
         "mid",
         CubeListBuilder.create().texOffs(u, v + 12).addBox(-midW * 0.5F, -midH, -midW * 0.5F, midW, midH, midW, none),
         PartPose.offsetAndRotation(0.1F, -height + 0.3F, 0.0F, -0.15F, 0.08F, 0.05F)
      );
      if (glowTip) {
         mid.addOrReplaceChild(
            "tip",
            CubeListBuilder.create().texOffs(200, 74).addBox(-0.4F, -2.2F, -0.4F, 0.8F, 2.5F, 0.8F, none),
            PartPose.offset(0.0F, -midH + 0.2F, 0.0F)
         );
      } else {
         mid.addOrReplaceChild(
            "tip",
            CubeListBuilder.create().texOffs(u, v + 20).addBox(-0.35F, -1.8F, -0.35F, 0.7F, 2.0F, 0.7F, none),
            PartPose.offset(0.0F, -midH + 0.2F, 0.0F)
         );
      }
   }

   @Override
   public void setupAnim(
      InfernalHellhoundEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      float walk = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
      boolean running = entity.isRunning() && walk > 0.15F;
      float gaitSpeed = running ? 0.85F : 0.55F;
      float walkCycle = limbSwing * gaitSpeed;
      float breathe = Mth.sin(ageInTicks * 0.09F);
      float breatheSlow = Mth.sin(ageInTicks * 0.045F);
      float corePulse = Mth.sin(ageInTicks * 0.14F);

      // --- Idle: breathe, head shift, jaw twitch, core pulse, tail sway ---
      float idleY = PELVIS_Y + Mth.sin(ageInTicks * 0.08F) * 0.25F;
      this.pelvis.y = idleY;
      this.pelvis.xRot = 0.0F;
      this.pelvis.yRot = 0.0F;
      this.pelvis.zRot = 0.0F;
      this.pelvis.z = PELVIS_Z;
      this.pelvis.x = 0.0F;

      this.spineLower.xRot = -0.08F + breathe * 0.03F;
      this.spineLower.yRot = breatheSlow * 0.02F;
      this.spineLower.zRot = Mth.sin(ageInTicks * 0.06F) * 0.015F;
      this.spineMid.xRot = -0.1F + breathe * 0.035F;
      this.spineMid.yRot = breatheSlow * 0.025F;
      this.spineMid.zRot = 0.0F;
      this.chest.xRot = -0.06F + breathe * 0.045F;
      this.chest.yRot = 0.0F;
      this.chest.zRot = Mth.sin(ageInTicks * 0.07F) * 0.02F;

      this.infernalCore.xScale = 1.0F + corePulse * 0.1F;
      this.infernalCore.yScale = 1.0F + corePulse * 0.14F;
      this.infernalCore.zScale = 1.0F + corePulse * 0.1F;

      float headTwitch = 0.0F;
      if ((int)(ageInTicks / 35.0F) % 6 == 0) {
         headTwitch = Mth.sin(ageInTicks * 1.6F) * 0.07F;
      }
      this.neck.xRot = 0.35F + breathe * 0.03F;
      this.neck.yRot = headTwitch * 0.4F;
      this.neck.zRot = headTwitch * 0.3F;
      this.head.yRot = netHeadYaw * ((float)Math.PI / 180F) * 0.8F + headTwitch;
      this.head.xRot = -0.25F + headPitch * ((float)Math.PI / 180F) * 0.65F + breatheSlow * 0.03F;
      this.head.zRot = headTwitch * 0.35F;

      float jawIdle = 0.55F + Mth.sin(ageInTicks * 0.11F) * 0.05F;
      if ((int)(ageInTicks / 28.0F) % 5 == 0) {
         jawIdle += Mth.sin(ageInTicks * 2.2F) * 0.12F;
      }
      this.jaw.xRot = jawIdle;

      // Rest poses for legs
      float flUx = 0.15F;
      float flJx = 0.35F;
      float flLx = -0.55F;
      float flPx = 0.05F;
      float blUx = -0.65F;
      float blJx = 1.25F;
      float blLx = -0.85F;
      float blPx = 0.25F;

      // --- Walk / Run gaits ---
      float strideAmp = running ? 1.15F : 0.75F;
      float bodyDrop = running ? 1.4F : 0.0F;
      float spineFlex = running ? 0.18F : 0.06F;

      float fl = Mth.cos(walkCycle) * walk * strideAmp;
      float fr = Mth.cos(walkCycle + (float)Math.PI) * walk * strideAmp;
      // Hind legs phase-offset from front (trot → gallop when running)
      float hindPhase = running ? 0.55F : (float)Math.PI * 0.85F;
      float bl = Mth.cos(walkCycle + hindPhase) * walk * strideAmp;
      float br = Mth.cos(walkCycle + hindPhase + (float)Math.PI) * walk * strideAmp;
      float bob = Mth.sin(walkCycle * 2.0F) * walk * (running ? 0.9F : 0.45F);

      this.pelvis.y = idleY + bob * 0.35F + bodyDrop;
      this.pelvis.zRot = (fl - fr) * 0.04F;
      this.spineLower.yRot += (fl - fr) * (running ? 0.1F : 0.06F);
      this.spineLower.zRot += (fl - fr) * 0.05F;
      this.spineMid.xRot += Mth.sin(walkCycle * 2.0F) * walk * spineFlex;
      this.spineMid.yRot += (fr - fl) * 0.05F;
      this.chest.xRot += (running ? -0.12F : 0.05F) * walk + Mth.sin(walkCycle * 2.0F) * walk * spineFlex * 0.6F;
      this.chest.yRot += (fl - fr) * 0.04F;

      if (running) {
         this.neck.xRot += 0.2F * walk;
         this.head.xRot += 0.1F * walk;
         this.pelvis.xRot = 0.08F * walk;
      }

      // Front legs
      this.flUpper.xRot = flUx + fl * 0.95F;
      this.frUpper.xRot = flUx + fr * 0.95F;
      this.flUpper.zRot = 0.08F;
      this.frUpper.zRot = -0.08F;
      this.flJoint.xRot = flJx + Mth.clamp(-fl, 0.0F, 1.0F) * 0.45F;
      this.frJoint.xRot = flJx + Mth.clamp(-fr, 0.0F, 1.0F) * 0.45F;
      this.flLower.xRot = flLx - fl * 0.4F - (running ? 0.1F * walk : 0.0F);
      this.frLower.xRot = flLx - fr * 0.4F - (running ? 0.1F * walk : 0.0F);
      this.flPaw.xRot = flPx - fl * 0.3F;
      this.frPaw.xRot = flPx - fr * 0.3F;

      // Hind legs — sharper Z articulation when sprinting
      float hindKick = running ? 1.1F : 0.85F;
      this.blUpper.xRot = blUx + bl * hindKick;
      this.brUpper.xRot = blUx + br * hindKick;
      this.blUpper.zRot = 0.06F;
      this.brUpper.zRot = -0.06F;
      this.blJoint.xRot = blJx + Mth.clamp(-bl, 0.0F, 1.0F) * (running ? 0.65F : 0.5F);
      this.brJoint.xRot = blJx + Mth.clamp(-br, 0.0F, 1.0F) * (running ? 0.65F : 0.5F);
      this.blLower.xRot = blLx - bl * 0.45F;
      this.brLower.xRot = blLx - br * 0.45F;
      this.blPaw.xRot = blPx - bl * 0.35F;
      this.brPaw.xRot = blPx - br * 0.35F;

      // Tail — rhythmic S-curve; more whip when running
      float tailAmp = running ? 0.35F : 0.22F;
      float t1 = ageInTicks * (running ? 0.22F : 0.12F);
      float tWalk = Mth.sin(walkCycle * 1.5F) * walk * 0.15F;
      this.tailBase.xRot = 0.45F + Mth.sin(t1) * tailAmp * 0.5F + tWalk;
      this.tailBase.yRot = Mth.sin(t1 * 0.7F) * tailAmp + (fl - fr) * 0.08F;
      this.tailBase.zRot = Mth.cos(t1 * 0.5F) * 0.08F;
      this.tailMid.xRot = 0.35F + Mth.sin(t1 + 0.8F) * tailAmp * 0.7F;
      this.tailMid.yRot = Mth.cos(t1 * 0.8F + 0.4F) * tailAmp * 0.6F;
      this.tailMid2.xRot = 0.4F + Mth.sin(t1 + 1.4F) * tailAmp * 0.85F;
      this.tailMid2.yRot = Mth.sin(t1 * 0.9F + 1.0F) * tailAmp * 0.5F;
      this.tailTip.xRot = 0.5F + Mth.sin(t1 + 2.0F) * tailAmp;
      this.tailTip.yRot = Mth.cos(t1 + 1.6F) * tailAmp * 0.4F;
      this.tailTip.zRot = Mth.sin(t1 * 1.1F) * 0.12F;

      // --- Attack: crouch → lunge → bite/swipe → recover ---
      float attack = entity.getMeleeAttackProgress(0.0F);
      int attackType = entity.getMeleeAttackType();
      if (attack > 0.0F) {
         float crouch = smoothStep(attack, 0.0F, 0.28F);
         float lunge = smoothStep(attack, 0.28F, 0.55F);
         float strike = Mth.sin(smoothStep(attack, 0.45F, 0.75F) * (float)Math.PI);
         float recover = smoothStep(attack, 0.75F, 1.0F);

         this.pelvis.y += 1.8F * crouch - 1.2F * lunge;
         this.pelvis.z = PELVIS_Z - 4.5F * lunge + 1.5F * recover;
         this.chest.xRot += -0.25F * crouch + 0.35F * lunge - 0.1F * recover;
         this.spineMid.xRot += -0.15F * crouch + 0.25F * strike;
         this.flUpper.xRot = flUx - 0.55F * crouch + 0.4F * lunge;
         this.frUpper.xRot = flUx - 0.55F * crouch + 0.4F * lunge;
         this.blUpper.xRot = blUx - 0.35F * crouch + 0.5F * lunge;
         this.brUpper.xRot = blUx - 0.35F * crouch + 0.5F * lunge;

         this.head.xRot += -0.4F * crouch + 0.65F * strike - 0.15F * recover;
         this.jaw.xRot = jawIdle + 0.85F * crouch + 0.2F * strike - 0.5F * recover;
         this.neck.xRot += -0.2F * crouch + 0.35F * strike;

         if (attackType != 1) {
            // Claw swipe accent on front-right
            this.frUpper.xRot = flUx - 1.0F * crouch - 1.3F * strike + 0.2F * recover;
            this.frUpper.yRot = -0.5F * crouch - 0.7F * strike;
            this.frUpper.zRot = -0.08F - 0.45F * crouch;
            this.frLower.xRot = flLx - 0.5F * strike;
            this.chest.yRot += -0.3F * crouch - 0.4F * strike + 0.15F * recover;
         } else {
            this.frUpper.yRot = 0.0F;
         }

         this.tailBase.xRot += 0.35F * crouch - 0.2F * lunge;
         this.infernalCore.xScale += 0.15F * strike;
         this.infernalCore.yScale += 0.2F * strike;
      } else {
         this.frUpper.yRot = 0.0F;
      }

      // --- Hurt ---
      float hurt = entity.getHurtReaction(0.0F);
      if (hurt > 0.0F) {
         float h = Mth.sin(hurt * (float)Math.PI);
         this.chest.xRot -= 0.2F * h;
         this.head.zRot += 0.4F * h;
         this.jaw.xRot += 0.35F * h;
         this.spineMid.zRot += 0.15F * h;
         this.infernalCore.xScale += 0.25F * h;
         this.infernalCore.yScale += 0.3F * h;
         this.pelvis.x -= 0.8F * h;
      }

      // --- Death: collapse onto chest, head lolls ---
      float death = entity.getDeathReaction(0.0F);
      if (death > 0.0F) {
         float d = Mth.clamp(death, 0.0F, 1.0F);
         this.pelvis.y = PELVIS_Y + 5.5F * d;
         this.pelvis.xRot = 0.55F * d;
         this.spineLower.xRot = -0.08F + 0.35F * d;
         this.spineMid.xRot = -0.1F + 0.4F * d;
         this.chest.xRot = -0.06F + 0.5F * d;
         this.neck.xRot = 0.35F + 0.6F * d;
         this.head.xRot = -0.25F + 0.35F * d;
         this.head.zRot = 0.7F * d;
         this.jaw.xRot = 0.55F + 0.65F * d;
         this.flUpper.xRot = flUx + 0.7F * d;
         this.frUpper.xRot = flUx + 0.65F * d;
         this.blUpper.xRot = blUx - 0.5F * d;
         this.brUpper.xRot = blUx - 0.45F * d;
         this.blJoint.xRot = blJx - 0.4F * d;
         this.brJoint.xRot = blJx - 0.35F * d;
         this.tailBase.xRot = 0.45F + 0.8F * d;
         this.tailMid.xRot = 0.35F + 0.5F * d;
         this.infernalCore.xScale = 1.0F - 0.75F * d;
         this.infernalCore.yScale = 1.0F - 0.75F * d;
         this.infernalCore.zScale = 1.0F - 0.75F * d;
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
