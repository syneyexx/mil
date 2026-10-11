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
import org.millenaire.entity.AntichristPriestEntity;

/**
 * Production Antichrist Priest model — tall skeletal Nether hierophant (~2.7 blocks).
 * Hierarchical robe panels, horned headdress, articulated jaw, glowing chest core,
 * clawed hands, and a ritual staff (shaft / spiked frame / floating core / chains).
 *
 * <pre>
 * 256x256 UV atlas (albedo):
 *   0,0     skull / cranium          32x24
 *   32,0    hood / headdress base    48x32
 *   80,0    horns                    48x32
 *   128,0   jaw / teeth              32x16
 *   160,0   staff shaft              16x64
 *   176,0   staff frame + spikes     24x24
 *   176,24  staff hanging chains     24x40
 *   0,32    neck / bone ornaments    48x16
 *   0,48    torso robe / ribs        96x48
 *   96,48   pelvis / chains          64x32
 *   0,96    robe panels (8 strips)   160x48
 *   0,144   rune strips (bone)       96x32
 *   0,176   arms / hands / claws     160x48
 *   160,176 legs / feet              64x48
 * Emissive islands (u &gt;= 200):
 *   200,0   eyes                     12x8
 *   200,16  chest core               16x16
 *   216,16  rib glow                 12x12
 *   200,40  hood/forehead rune       16x16
 *   216,40  robe rune strips         24x16
 *   200,64  claw tip glow            16x16
 *   216,64  hand runes               16x12
 *   232,0   staff floating core      16x16
 *   232,16  staff shaft cracks       16x32
 *   232,48  chain glow tips          16x16
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class AntichristPriestModel extends EntityModel<AntichristPriestEntity> {
   private final ModelPart root;
   private final ModelPart pelvis;
   private final ModelPart robeRoot;
   private final ModelPart[] robePanels = new ModelPart[8];
   private final ModelPart[] runeStrips = new ModelPart[4];
   private final ModelPart torso;
   private final ModelPart ribcage;
   private final ModelPart ritualCore;
   private final ModelPart headRoot;
   private final ModelPart jaw;
   private final ModelPart rightShoulder;
   private final ModelPart rightUpperArm;
   private final ModelPart rightForearm;
   private final ModelPart rightHand;
   private final ModelPart rightClaw1;
   private final ModelPart rightClaw2;
   private final ModelPart rightClaw3;
   private final ModelPart rightClaw4;
   private final ModelPart leftShoulder;
   private final ModelPart leftUpperArm;
   private final ModelPart leftForearm;
   private final ModelPart leftHand;
   private final ModelPart leftClaw1;
   private final ModelPart leftClaw2;
   private final ModelPart leftClaw3;
   private final ModelPart leftClaw4;
   private final ModelPart rightThigh;
   private final ModelPart rightLowerLeg;
   private final ModelPart rightFoot;
   private final ModelPart leftThigh;
   private final ModelPart leftLowerLeg;
   private final ModelPart leftFoot;
   private final ModelPart ritualStaff;
   private final ModelPart staffShaft;
   private final ModelPart staffFrame;
   private final ModelPart staffCore;
   private final ModelPart[] staffChains = new ModelPart[4];

   public AntichristPriestModel(ModelPart root) {
      this.root = root;
      this.pelvis = root.getChild("pelvis");
      this.robeRoot = this.pelvis.getChild("robe_root");
      for (int i = 0; i < 8; i++) {
         this.robePanels[i] = this.robeRoot.getChild("robe_panel_" + i);
      }
      for (int i = 0; i < 4; i++) {
         this.runeStrips[i] = this.robeRoot.getChild("rune_strip_" + i);
      }

      this.torso = this.pelvis.getChild("torso");
      this.ribcage = this.torso.getChild("ribcage");
      this.ritualCore = this.ribcage.getChild("ritual_core");
      this.headRoot = this.torso.getChild("head_root");
      this.jaw = this.headRoot.getChild("jaw");

      this.rightShoulder = this.torso.getChild("right_shoulder");
      this.rightUpperArm = this.rightShoulder.getChild("right_upper_arm");
      this.rightForearm = this.rightUpperArm.getChild("right_forearm");
      this.rightHand = this.rightForearm.getChild("right_hand");
      this.rightClaw1 = this.rightHand.getChild("right_claw1");
      this.rightClaw2 = this.rightHand.getChild("right_claw2");
      this.rightClaw3 = this.rightHand.getChild("right_claw3");
      this.rightClaw4 = this.rightHand.getChild("right_claw4");

      this.leftShoulder = this.torso.getChild("left_shoulder");
      this.leftUpperArm = this.leftShoulder.getChild("left_upper_arm");
      this.leftForearm = this.leftUpperArm.getChild("left_forearm");
      this.leftHand = this.leftForearm.getChild("left_hand");
      this.leftClaw1 = this.leftHand.getChild("left_claw1");
      this.leftClaw2 = this.leftHand.getChild("left_claw2");
      this.leftClaw3 = this.leftHand.getChild("left_claw3");
      this.leftClaw4 = this.leftHand.getChild("left_claw4");

      this.rightThigh = this.pelvis.getChild("right_thigh");
      this.rightLowerLeg = this.rightThigh.getChild("right_lower_leg");
      this.rightFoot = this.rightLowerLeg.getChild("right_foot");
      this.leftThigh = this.pelvis.getChild("left_thigh");
      this.leftLowerLeg = this.leftThigh.getChild("left_lower_leg");
      this.leftFoot = this.leftLowerLeg.getChild("left_foot");

      this.ritualStaff = this.rightHand.getChild("ritual_staff");
      this.staffShaft = this.ritualStaff.getChild("staff_shaft");
      this.staffFrame = this.staffShaft.getChild("staff_frame");
      this.staffCore = this.staffFrame.getChild("staff_core");
      for (int i = 0; i < 4; i++) {
         this.staffChains[i] = this.staffFrame.getChild("staff_chain_" + i);
      }
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      // Pelvis near y=30; legs down ~14 → feet ≈44 → ~2.75 blocks tall with headdress.
      PartDefinition pelvis = root.addOrReplaceChild(
         "pelvis",
         CubeListBuilder.create()
            .texOffs(96, 48)
            .addBox(-4.0F, -2.0F, -3.0F, 8.0F, 4.0F, 6.0F, none)
            .texOffs(96, 58)
            .addBox(-2.0F, -3.5F, -2.0F, 4.0F, 2.0F, 4.0F, none)
            .texOffs(120, 48)
            .addBox(-3.5F, 0.5F, -2.5F, 7.0F, 2.0F, 5.0F, none),
         PartPose.offset(0.0F, 30.0F, 0.0F)
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
         PartPose.offset(0.0F, 1.0F, 0.0F)
      );

      // Eight tattered vertical panels around the waist
      float[][] panelData = {
         {-4.5F, 0.0F, -2.8F, 0.05F, 0.0F, 0.08F},
         {-2.5F, 0.0F, -3.4F, 0.02F, 0.0F, 0.04F},
         {0.0F, 0.0F, -3.6F, 0.0F, 0.0F, 0.0F},
         {2.5F, 0.0F, -3.4F, 0.02F, 0.0F, -0.04F},
         {4.5F, 0.0F, -2.8F, 0.05F, 0.0F, -0.08F},
         {-3.8F, 0.0F, 2.6F, 0.08F, 0.0F, -0.1F},
         {0.0F, 0.0F, 3.2F, 0.1F, 0.0F, 0.0F},
         {3.8F, 0.0F, 2.6F, 0.08F, 0.0F, 0.1F}
      };
      for (int i = 0; i < 8; i++) {
         float[] d = panelData[i];
         float w = i % 2 == 0 ? 2.4F : 2.0F;
         float h = 14.0F + (i % 3) * 1.5F;
         int u = (i % 5) * 16;
         int v = 96 + (i / 5) * 20;
         robeRoot.addOrReplaceChild(
            "robe_panel_" + i,
            CubeListBuilder.create()
               .texOffs(u, v)
               .addBox(-w * 0.5F, 0.0F, -0.4F, w, h, 0.8F, none)
               .texOffs(u, v + 16)
               .addBox(-w * 0.35F, h - 2.0F, -0.35F, w * 0.7F, 3.0F + (i % 2), 0.7F, none),
            PartPose.offsetAndRotation(d[0], d[1], d[2], d[3], d[4], d[5])
         );
      }

      // Bone-colored rune strips hanging in front
      for (int i = 0; i < 4; i++) {
         float x = -3.0F + i * 2.0F;
         robeRoot.addOrReplaceChild(
            "rune_strip_" + i,
            CubeListBuilder.create()
               .texOffs(i * 12, 144)
               .addBox(-0.6F, 0.0F, -0.35F, 1.2F, 16.0F, 0.7F, none)
               .texOffs(216, 40)
               .addBox(-0.45F, 1.0F + i, -0.4F, 0.9F, 2.5F, 0.8F, none)
               .texOffs(216, 44)
               .addBox(-0.45F, 6.0F + i * 0.5F, -0.4F, 0.9F, 2.5F, 0.8F, none)
               .texOffs(220, 40)
               .addBox(-0.45F, 11.0F, -0.4F, 0.9F, 2.0F, 0.8F, none),
            PartPose.offsetAndRotation(x, 0.5F, -3.8F, 0.04F, 0.0F, (i - 1.5F) * 0.03F)
         );
      }
   }

   private static void buildTorso(PartDefinition pelvis) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition torso = pelvis.addOrReplaceChild(
         "torso",
         CubeListBuilder.create()
            .texOffs(0, 48)
            .addBox(-4.5F, -14.0F, -3.0F, 9.0F, 14.0F, 6.0F, none)
            .texOffs(30, 48)
            .addBox(-5.0F, -14.5F, -2.5F, 10.0F, 3.0F, 5.0F, none)
            .texOffs(0, 68)
            .addBox(-1.5F, -13.0F, -1.5F, 3.0F, 12.0F, 3.0F, none),
         PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.12F, 0.0F, 0.0F)
      );

      // Shoulder chains / bone trim
      torso.addOrReplaceChild(
         "chain_left",
         CubeListBuilder.create()
            .texOffs(120, 56)
            .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F, none)
            .texOffs(124, 56)
            .addBox(-0.75F, 2.0F, -0.75F, 1.5F, 1.5F, 1.5F, none)
            .texOffs(124, 60)
            .addBox(-0.75F, 5.0F, -0.75F, 1.5F, 1.5F, 1.5F, none),
         PartPose.offsetAndRotation(4.0F, -13.0F, -1.5F, 0.15F, 0.0F, -0.2F)
      );
      torso.addOrReplaceChild(
         "chain_right",
         CubeListBuilder.create()
            .texOffs(128, 56)
            .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F, none)
            .texOffs(132, 56)
            .addBox(-0.75F, 2.5F, -0.75F, 1.5F, 1.5F, 1.5F, none)
            .texOffs(132, 60)
            .addBox(-0.75F, 5.5F, -0.75F, 1.5F, 1.5F, 1.5F, none),
         PartPose.offsetAndRotation(-4.0F, -13.0F, -1.5F, 0.15F, 0.0F, 0.2F)
      );
      torso.addOrReplaceChild(
         "bone_ornament_left",
         CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, none),
         PartPose.offset(3.5F, -12.0F, -3.2F)
      );
      torso.addOrReplaceChild(
         "bone_ornament_right",
         CubeListBuilder.create().texOffs(8, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, none),
         PartPose.offset(-3.5F, -12.0F, -3.2F)
      );
      torso.addOrReplaceChild(
         "skull_ornament",
         CubeListBuilder.create().texOffs(16, 32).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, none),
         PartPose.offset(0.0F, -1.0F, -3.5F)
      );

      PartDefinition ribcage = torso.addOrReplaceChild(
         "ribcage",
         CubeListBuilder.create()
            .texOffs(48, 48)
            .addBox(-3.5F, -11.0F, -2.5F, 7.0F, 10.0F, 5.0F, none)
            .texOffs(72, 48)
            .addBox(-1.0F, -10.5F, -3.2F, 2.0F, 9.0F, 1.5F, none),
         PartPose.offset(0.0F, 0.0F, 0.0F)
      );
      addRib(ribcage, "rib_l1", 2.0F, -10.0F, 0.0F, -0.1F, -0.5F, -0.35F, 48, 64);
      addRib(ribcage, "rib_l2", 2.2F, -8.0F, 0.2F, 0.0F, -0.45F, -0.25F, 60, 64);
      addRib(ribcage, "rib_l3", 2.0F, -6.0F, 0.2F, 0.08F, -0.4F, -0.18F, 72, 64);
      addRib(ribcage, "rib_l4", 1.6F, -4.0F, 0.1F, 0.15F, -0.35F, -0.12F, 84, 64);
      addRib(ribcage, "rib_r1", -2.0F, -10.0F, 0.0F, -0.1F, 0.5F, 0.35F, 48, 72);
      addRib(ribcage, "rib_r2", -2.2F, -8.0F, 0.2F, 0.0F, 0.45F, 0.25F, 60, 72);
      addRib(ribcage, "rib_r3", -2.0F, -6.0F, 0.2F, 0.08F, 0.4F, 0.18F, 72, 72);
      addRib(ribcage, "rib_r4", -1.6F, -4.0F, 0.1F, 0.15F, 0.35F, 0.12F, 84, 72);

      ribcage.addOrReplaceChild(
         "ritual_core",
         CubeListBuilder.create()
            .texOffs(200, 16)
            .addBox(-2.0F, -2.5F, -2.0F, 4.0F, 5.0F, 4.0F, none)
            .texOffs(216, 16)
            .addBox(-1.5F, -3.5F, -1.5F, 3.0F, 2.0F, 3.0F, none)
            .texOffs(216, 22)
            .addBox(-1.0F, 2.0F, -1.0F, 2.0F, 2.0F, 2.0F, none)
            .texOffs(228, 16)
            .addBox(-0.5F, -1.0F, -2.4F, 1.0F, 2.0F, 1.0F, none),
         PartPose.offset(0.0F, -7.5F, 0.0F)
      );

      torso.addOrReplaceChild(
         "neck",
         CubeListBuilder.create()
            .texOffs(24, 32)
            .addBox(-2.0F, -3.5F, -2.0F, 4.0F, 4.0F, 4.0F, none)
            .texOffs(40, 32)
            .addBox(-1.0F, -4.5F, -1.0F, 2.0F, 2.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, -14.0F, -0.5F, 0.2F, 0.0F, 0.0F)
      );

      buildHead(torso);
      buildArm(torso, true);
      buildArm(torso, false);
   }

   private static void addRib(PartDefinition parent, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v) {
      PartDefinition rib = parent.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(0.0F, -0.9F, -0.9F, 5.0F, 1.8F, 1.8F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      rib.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(u, v + 4).addBox(0.0F, -0.7F, -0.7F, 3.5F, 1.4F, 1.4F, CubeDeformation.NONE),
         PartPose.offsetAndRotation(4.5F, 0.0F, 0.0F, 0.0F, 0.0F, name.contains("_l") ? 0.55F : -0.55F)
      );
   }

   private static void buildHead(PartDefinition torso) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition head = torso.addOrReplaceChild(
         "head_root",
         CubeListBuilder.create(),
         PartPose.offsetAndRotation(0.0F, -15.0F, -1.0F, -0.08F, 0.0F, 0.0F)
      );

      // Skull face
      head.addOrReplaceChild(
         "skull",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.0F, -8.0F, -4.5F, 8.0F, 7.0F, 8.0F, none)
            .texOffs(0, 15)
            .addBox(-3.5F, -9.0F, -3.5F, 7.0F, 1.5F, 7.0F, none)
            .texOffs(24, 0)
            .addBox(-3.0F, -4.5F, -5.5F, 6.0F, 3.0F, 2.0F, none),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "eye_left",
         CubeListBuilder.create().texOffs(200, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.5F, 1.0F, none),
         PartPose.offset(2.0F, -5.5F, -4.8F)
      );
      head.addOrReplaceChild(
         "eye_right",
         CubeListBuilder.create().texOffs(206, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 1.5F, 1.0F, none),
         PartPose.offset(-2.0F, -5.5F, -4.8F)
      );

      // Large dark hood
      head.addOrReplaceChild(
         "hood",
         CubeListBuilder.create()
            .texOffs(32, 0)
            .addBox(-5.5F, -10.0F, -5.5F, 11.0F, 11.0F, 11.0F, none)
            .texOffs(32, 22)
            .addBox(-6.0F, -4.0F, -4.0F, 12.0F, 6.0F, 4.0F, none)
            .texOffs(200, 40)
            .addBox(-1.0F, -9.5F, -5.8F, 2.0F, 5.0F, 1.0F, none),
         PartPose.ZERO
      );

      // Elaborate horned headdress
      PartDefinition headdress = head.addOrReplaceChild(
         "headdress",
         CubeListBuilder.create()
            .texOffs(80, 0)
            .addBox(-3.0F, -3.0F, -2.0F, 6.0F, 3.0F, 4.0F, none)
            .texOffs(80, 8)
            .addBox(-1.0F, -6.0F, -1.0F, 2.0F, 4.0F, 2.0F, none)
            .texOffs(204, 40)
            .addBox(-0.75F, -5.5F, -1.2F, 1.5F, 3.0F, 0.8F, none),
         PartPose.offset(0.0F, -10.0F, 0.0F)
      );
      buildHorn(headdress, true);
      buildHorn(headdress, false);
      headdress.addOrReplaceChild(
         "spike_left",
         CubeListBuilder.create().texOffs(100, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, none),
         PartPose.offsetAndRotation(1.5F, -5.5F, 0.0F, -0.15F, 0.0F, 0.2F)
      );
      headdress.addOrReplaceChild(
         "spike_right",
         CubeListBuilder.create().texOffs(104, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, none),
         PartPose.offsetAndRotation(-1.5F, -5.5F, 0.0F, -0.15F, 0.0F, -0.2F)
      );

      // Separate lower jaw
      head.addOrReplaceChild(
         "jaw",
         CubeListBuilder.create()
            .texOffs(128, 0)
            .addBox(-3.0F, 0.0F, -5.0F, 6.0F, 2.0F, 6.0F, none)
            .texOffs(128, 8)
            .addBox(-2.5F, -1.2F, -4.5F, 1.0F, 1.5F, 1.0F, none)
            .texOffs(132, 8)
            .addBox(-0.5F, -1.5F, -4.8F, 1.0F, 1.8F, 1.0F, none)
            .texOffs(136, 8)
            .addBox(1.5F, -1.2F, -4.5F, 1.0F, 1.5F, 1.0F, none),
         PartPose.offsetAndRotation(0.0F, -2.0F, -1.5F, 0.25F, 0.0F, 0.0F)
      );

      // Bone / chain drapes from hood over shoulders
      head.addOrReplaceChild(
         "hood_drape_left",
         CubeListBuilder.create().texOffs(108, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, none),
         PartPose.offsetAndRotation(4.5F, -2.0F, 0.0F, 0.3F, 0.0F, -0.35F)
      );
      head.addOrReplaceChild(
         "hood_drape_right",
         CubeListBuilder.create().texOffs(116, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, none),
         PartPose.offsetAndRotation(-4.5F, -2.0F, 0.0F, 0.3F, 0.0F, 0.35F)
      );
   }

   private static void buildHorn(PartDefinition headdress, boolean left) {
      CubeDeformation none = CubeDeformation.NONE;
      String prefix = left ? "horn_left" : "horn_right";
      float side = left ? 1.0F : -1.0F;
      PartDefinition h1 = headdress.addOrReplaceChild(
         prefix + "_1",
         CubeListBuilder.create().texOffs(80, 16).addBox(-1.25F, -5.0F, -1.25F, 2.5F, 5.0F, 2.5F, none),
         PartPose.offsetAndRotation(2.5F * side, -2.0F, 0.5F, -0.45F, 0.1F * side, 0.45F * side)
      );
      PartDefinition h2 = h1.addOrReplaceChild(
         prefix + "_2",
         CubeListBuilder.create().texOffs(90, 16).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.5F, 0.08F * side, 0.25F * side)
      );
      h2.addOrReplaceChild(
         prefix + "_3",
         CubeListBuilder.create().texOffs(98, 16).addBox(-0.6F, -4.5F, -0.6F, 1.2F, 5.0F, 1.2F, none),
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.0F, -0.4F, 0.05F * side, 0.15F * side)
      );
   }

   private static void buildArm(PartDefinition torso, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;
      int uBase = right ? 0 : 40;

      PartDefinition shoulder = torso.addOrReplaceChild(
         side + "_shoulder",
         CubeListBuilder.create()
            .texOffs(uBase, 176)
            .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, none)
            .texOffs(uBase + 16, 176)
            .addBox(-2.5F, -2.5F, -2.5F, 5.0F, 3.0F, 5.0F, none),
         PartPose.offsetAndRotation(5.5F * s, -12.5F, 0.0F, 0.1F, 0.0F, 0.15F * s)
      );
      PartDefinition upper = shoulder.addOrReplaceChild(
         side + "_upper_arm",
         CubeListBuilder.create()
            .texOffs(uBase, 186)
            .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F, none)
            .texOffs(uBase + 12, 186)
            .addBox(-2.0F, -0.5F, -2.0F, 4.0F, 3.0F, 4.0F, none),
         PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.12F, 0.0F, 0.08F * s)
      );
      PartDefinition forearm = upper.addOrReplaceChild(
         side + "_forearm",
         CubeListBuilder.create()
            .texOffs(uBase, 200)
            .addBox(-1.25F, 0.0F, -1.25F, 2.5F, 10.0F, 2.5F, none)
            .texOffs(uBase + 12, 200)
            .addBox(-1.75F, -1.0F, -1.75F, 3.5F, 2.5F, 3.5F, none),
         PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, -0.2F, 0.0F, 0.0F)
      );
      PartDefinition hand = forearm.addOrReplaceChild(
         side + "_hand",
         CubeListBuilder.create()
            .texOffs(80 + uBase, 176)
            .addBox(-2.0F, 0.0F, -1.75F, 4.0F, 3.5F, 3.5F, none)
            .texOffs(216, 64)
            .addBox(-1.5F, 0.5F, -2.0F, 3.0F, 2.0F, 0.5F, none),
         PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, -0.08F, 0.0F, 0.0F)
      );

      addClaw(hand, side + "_claw1", -1.6F, 3.2F, -0.4F, 0.35F, 0.2F * s, 0.25F * s, 80, 186);
      addClaw(hand, side + "_claw2", -0.5F, 3.5F, -0.8F, 0.45F, 0.05F * s, 0.05F * s, 88, 186);
      addClaw(hand, side + "_claw3", 0.6F, 3.5F, -0.8F, 0.45F, -0.05F * s, -0.05F * s, 96, 186);
      addClaw(hand, side + "_claw4", 1.6F, 3.2F, -0.3F, 0.35F, -0.2F * s, -0.25F * s, 104, 186);

      if (right) {
         buildStaff(hand);
      }
   }

   private static void addClaw(
      PartDefinition hand, String name, float x, float y, float z, float rx, float ry, float rz, int u, int v
   ) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition base = hand.addOrReplaceChild(
         name,
         CubeListBuilder.create().texOffs(u, v).addBox(-0.55F, 0.0F, -0.55F, 1.1F, 4.5F, 1.1F, none),
         PartPose.offsetAndRotation(x, y, z, rx, ry, rz)
      );
      PartDefinition mid = base.addOrReplaceChild(
         "mid",
         CubeListBuilder.create().texOffs(u, v + 6).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 3.5F, 0.8F, none),
         PartPose.offsetAndRotation(0.0F, 4.2F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
      mid.addOrReplaceChild(
         "tip",
         CubeListBuilder.create().texOffs(200, 64).addBox(-0.3F, 0.0F, -0.3F, 0.6F, 3.0F, 0.6F, none),
         PartPose.offsetAndRotation(0.0F, 3.2F, 0.0F, 0.4F, 0.0F, 0.0F)
      );
   }

   private static void buildStaff(PartDefinition rightHand) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition staff = rightHand.addOrReplaceChild(
         "ritual_staff",
         CubeListBuilder.create(),
         PartPose.offsetAndRotation(0.5F, 2.0F, 0.0F, -1.15F, 0.0F, 0.15F)
      );

      PartDefinition shaft = staff.addOrReplaceChild(
         "staff_shaft",
         CubeListBuilder.create()
            .texOffs(160, 0)
            .addBox(-1.0F, -22.0F, -1.0F, 2.0F, 28.0F, 2.0F, none)
            .texOffs(232, 16)
            .addBox(-0.6F, -18.0F, -1.15F, 1.2F, 4.0F, 0.4F, none)
            .texOffs(232, 22)
            .addBox(-0.6F, -10.0F, -1.15F, 1.2F, 5.0F, 0.4F, none)
            .texOffs(232, 28)
            .addBox(-0.6F, -2.0F, -1.15F, 1.2F, 3.0F, 0.4F, none)
            .texOffs(168, 0)
            .addBox(-1.4F, 4.5F, -1.4F, 2.8F, 2.5F, 2.8F, none),
         PartPose.ZERO
      );

      PartDefinition frame = shaft.addOrReplaceChild(
         "staff_frame",
         CubeListBuilder.create()
            .texOffs(176, 0)
            .addBox(-5.0F, -5.0F, -1.0F, 10.0F, 10.0F, 2.0F, none)
            .texOffs(176, 12)
            .addBox(-5.5F, -5.5F, -0.5F, 2.0F, 2.0F, 1.0F, none)
            .texOffs(182, 12)
            .addBox(3.5F, -5.5F, -0.5F, 2.0F, 2.0F, 1.0F, none)
            .texOffs(188, 12)
            .addBox(-5.5F, 3.5F, -0.5F, 2.0F, 2.0F, 1.0F, none)
            .texOffs(194, 12)
            .addBox(3.5F, 3.5F, -0.5F, 2.0F, 2.0F, 1.0F, none)
            .texOffs(176, 16)
            .addBox(-1.0F, -6.5F, -0.75F, 2.0F, 2.0F, 1.5F, none)
            .texOffs(182, 16)
            .addBox(-6.5F, -1.0F, -0.75F, 2.0F, 2.0F, 1.5F, none)
            .texOffs(188, 16)
            .addBox(4.5F, -1.0F, -0.75F, 2.0F, 2.0F, 1.5F, none),
         PartPose.offset(0.0F, -24.0F, 0.0F)
      );

      frame.addOrReplaceChild(
         "staff_core",
         CubeListBuilder.create()
            .texOffs(232, 0)
            .addBox(-1.75F, -1.75F, -1.25F, 3.5F, 3.5F, 2.5F, none)
            .texOffs(240, 0)
            .addBox(-0.75F, -2.5F, -0.75F, 1.5F, 5.0F, 1.5F, none)
            .texOffs(244, 0)
            .addBox(-2.5F, -0.75F, -0.75F, 5.0F, 1.5F, 1.5F, none),
         PartPose.offset(0.0F, 0.0F, 0.0F)
      );

      float[][] chainOffsets = {
         {-3.5F, 5.0F, 0.0F, 0.15F},
         {-1.2F, 5.5F, 0.3F, 0.05F},
         {1.2F, 5.5F, -0.2F, -0.05F},
         {3.5F, 5.0F, 0.1F, -0.15F}
      };
      for (int i = 0; i < 4; i++) {
         float[] c = chainOffsets[i];
         float len = 6.0F + i * 1.2F;
         PartDefinition chain = frame.addOrReplaceChild(
            "staff_chain_" + i,
            CubeListBuilder.create()
               .texOffs(176 + i * 4, 24)
               .addBox(-0.4F, 0.0F, -0.4F, 0.8F, len, 0.8F, none)
               .texOffs(232, 48)
               .addBox(-0.5F, len - 0.5F, -0.5F, 1.0F, 1.5F, 1.0F, none),
            PartPose.offsetAndRotation(c[0], c[1], c[2], c[3], 0.0F, 0.0F)
         );
         chain.addOrReplaceChild(
            "weight",
            CubeListBuilder.create().texOffs(180 + i * 4, 40).addBox(-0.7F, 0.0F, -0.7F, 1.4F, 1.8F, 1.4F, none),
            PartPose.offset(0.0F, len + 0.8F, 0.0F)
         );
      }
   }

   private static void buildLeg(PartDefinition pelvis, boolean right) {
      CubeDeformation none = CubeDeformation.NONE;
      String side = right ? "right" : "left";
      float s = right ? -1.0F : 1.0F;
      int u = right ? 160 : 192;

      PartDefinition thigh = pelvis.addOrReplaceChild(
         side + "_thigh",
         CubeListBuilder.create().texOffs(u, 176).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, none),
         PartPose.offsetAndRotation(2.4F * s, 1.5F, 0.0F, -0.08F, 0.0F, 0.04F * s)
      );
      PartDefinition lower = thigh.addOrReplaceChild(
         side + "_lower_leg",
         CubeListBuilder.create()
            .texOffs(u, 188)
            .addBox(-1.75F, 0.0F, -1.75F, 3.5F, 8.0F, 3.5F, none)
            .texOffs(u, 200)
            .addBox(-2.25F, -1.0F, -2.25F, 4.5F, 2.5F, 4.5F, none),
         PartPose.offsetAndRotation(0.0F, 7.5F, 0.0F, 0.12F, 0.0F, 0.0F)
      );
      lower.addOrReplaceChild(
         side + "_foot",
         CubeListBuilder.create()
            .texOffs(u, 208)
            .addBox(-2.0F, -0.5F, -4.5F, 4.0F, 2.0F, 6.5F, none)
            .texOffs(u, 218)
            .addBox(-1.5F, -0.2F, -6.0F, 3.0F, 1.5F, 2.0F, none),
         PartPose.offsetAndRotation(0.0F, 7.5F, 0.2F, -0.04F, 0.0F, 0.0F)
      );
   }

   @Override
   public void setupAnim(
      AntichristPriestEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      float walk = limbSwingAmount;
      float walkCycle = limbSwing * 0.6F;
      float breathe = Mth.sin(ageInTicks * 0.075F);
      float breatheSlow = Mth.sin(ageInTicks * 0.045F);

      // Idle breathing / bob
      this.pelvis.y = 30.0F + Mth.sin(ageInTicks * 0.09F) * 0.3F;
      this.pelvis.z = 0.0F;
      this.pelvis.xRot = 0.0F;
      this.pelvis.yRot = 0.0F;
      this.pelvis.zRot = 0.0F;
      this.root.xScale = 1.0F;
      this.root.yScale = 1.0F;
      this.root.zScale = 1.0F;

      this.torso.xRot = 0.12F + breathe * 0.035F;
      this.torso.yRot = breatheSlow * 0.025F;
      this.torso.zRot = Mth.sin(ageInTicks * 0.06F) * 0.02F;
      this.ribcage.xRot = breathe * 0.025F;

      // Ritual core pulse
      float corePulse = 1.0F + breathe * 0.1F + Mth.sin(ageInTicks * 0.2F) * 0.05F;
      this.ritualCore.xScale = corePulse;
      this.ritualCore.yScale = 1.0F + breathe * 0.14F;
      this.ritualCore.zScale = corePulse;

      // Head look
      this.headRoot.yRot = netHeadYaw * ((float)Math.PI / 180F) * 0.8F;
      this.headRoot.xRot = -0.08F + headPitch * ((float)Math.PI / 180F) * 0.65F + breathe * 0.015F;
      this.headRoot.zRot = 0.0F;
      float jawIdle = 0.25F + Mth.sin(ageInTicks * 0.11F) * 0.04F;
      this.jaw.xRot = jawIdle;

      // Robe panel sway
      for (int i = 0; i < 8; i++) {
         float phase = ageInTicks * 0.1F + i * 0.7F;
         float sway = Mth.sin(phase) * 0.06F;
         float baseX = (i < 5 ? 0.02F : 0.08F) + (i % 3) * 0.01F;
         this.robePanels[i].xRot = baseX + sway + walk * 0.08F;
         this.robePanels[i].zRot = Mth.cos(phase * 0.8F) * 0.04F * (i % 2 == 0 ? 1 : -1);
      }
      for (int i = 0; i < 4; i++) {
         float phase = ageInTicks * 0.12F + i * 0.9F;
         this.runeStrips[i].xRot = 0.04F + Mth.sin(phase) * 0.05F + walk * 0.06F;
         this.runeStrips[i].zRot = (i - 1.5F) * 0.03F + Mth.cos(phase) * 0.03F;
      }

      // Walk gait
      float stride = Mth.cos(walkCycle) * walk;
      float strideOpp = Mth.cos(walkCycle + (float)Math.PI) * walk;
      this.pelvis.y += Mth.sin(walkCycle * 2.0F) * walk * 0.35F;
      this.torso.xRot += walk * 0.08F;
      this.torso.yRot += stride * 0.06F;

      this.rightThigh.xRot = -0.08F + stride * 0.75F;
      this.leftThigh.xRot = -0.08F + strideOpp * 0.75F;
      this.rightLowerLeg.xRot = 0.12F + Mth.clamp(-stride, 0.0F, 1.0F) * 0.45F;
      this.leftLowerLeg.xRot = 0.12F + Mth.clamp(-strideOpp, 0.0F, 1.0F) * 0.45F;
      this.rightFoot.xRot = -0.04F - stride * 0.25F;
      this.leftFoot.xRot = -0.04F - strideOpp * 0.25F;

      // Arms idle / walk
      float armSwing = Mth.cos(walkCycle - 0.3F) * walk;
      this.rightShoulder.xRot = 0.1F + armSwing * 0.2F;
      this.leftShoulder.xRot = 0.1F - armSwing * 0.25F;
      this.rightShoulder.yRot = 0.0F;
      this.leftShoulder.yRot = 0.0F;
      this.rightShoulder.zRot = -0.15F + breathe * 0.015F;
      this.leftShoulder.zRot = 0.15F - breathe * 0.015F;
      this.rightUpperArm.xRot = -0.12F;
      this.leftUpperArm.xRot = -0.12F;
      this.rightForearm.xRot = -0.2F;
      this.leftForearm.xRot = -0.2F;

      float finger = Mth.sin(ageInTicks * 0.22F) * 0.07F;
      this.rightClaw1.xRot = 0.35F + finger;
      this.rightClaw2.xRot = 0.45F - finger * 0.6F;
      this.rightClaw3.xRot = 0.45F + finger * 0.4F;
      this.rightClaw4.xRot = 0.35F - finger * 0.5F;
      this.leftClaw1.xRot = 0.35F - finger;
      this.leftClaw2.xRot = 0.45F + finger * 0.6F;
      this.leftClaw3.xRot = 0.45F - finger * 0.4F;
      this.leftClaw4.xRot = 0.35F + finger * 0.5F;

      // Staff idle — slight tilt / chain sway
      this.ritualStaff.xRot = -1.15F + breatheSlow * 0.04F;
      this.ritualStaff.yRot = 0.0F;
      this.ritualStaff.zRot = 0.15F + Mth.sin(ageInTicks * 0.08F) * 0.03F;
      this.staffCore.xScale = 1.0F + Mth.sin(ageInTicks * 0.18F) * 0.08F;
      this.staffCore.yScale = 1.0F + Mth.sin(ageInTicks * 0.18F) * 0.1F;
      this.staffCore.zScale = 1.0F + Mth.sin(ageInTicks * 0.18F) * 0.08F;
      for (int i = 0; i < 4; i++) {
         float phase = ageInTicks * 0.14F + i * 1.1F;
         this.staffChains[i].xRot = 0.1F + Mth.sin(phase) * 0.12F;
         this.staffChains[i].zRot = Mth.cos(phase * 0.9F) * 0.08F * (i % 2 == 0 ? 1 : -1);
      }

      // SUMMON — staff raise overhead
      float summon = entity.getSummonProgress(0.0F);
      if (summon > 0.0F) {
         float raise = smoothStep(summon, 0.0F, 0.4F);
         float hold = smoothStep(summon, 0.4F, 0.75F);
         float lower = smoothStep(summon, 0.75F, 1.0F);
         float up = raise + hold * 0.9F - lower * 0.85F;
         this.rightShoulder.xRot = 0.1F - 1.8F * up;
         this.rightShoulder.zRot = -0.15F - 0.25F * up;
         this.rightForearm.xRot = -0.2F - 0.35F * up;
         this.ritualStaff.xRot = -1.15F - 0.55F * up;
         this.torso.xRot = 0.12F - 0.15F * up;
         this.headRoot.xRot -= 0.2F * up;
         this.ritualCore.xScale += 0.2F * up;
         this.ritualCore.yScale += 0.25F * up;
         this.staffCore.xScale += 0.25F * up;
         this.staffCore.yScale += 0.3F * up;
      }

      // HEAL — staff raise (gentler, both arms inward)
      float heal = entity.getHealProgress(0.0F);
      if (heal > 0.0F) {
         float raise = smoothStep(heal, 0.0F, 0.35F);
         float hold = smoothStep(heal, 0.35F, 0.7F);
         float lower = smoothStep(heal, 0.7F, 1.0F);
         float up = raise + hold * 0.85F - lower * 0.8F;
         this.rightShoulder.xRot = 0.1F - 1.4F * up;
         this.leftShoulder.xRot = 0.1F - 0.9F * up;
         this.leftShoulder.yRot = 0.35F * up;
         this.ritualStaff.xRot = -1.15F - 0.4F * up;
         this.torso.xRot = 0.12F - 0.1F * up;
         this.ritualCore.xScale += 0.15F * up;
         this.ritualCore.yScale += 0.2F * up;
         this.jaw.xRot = jawIdle + 0.15F * up;
      }

      // CAST — aim staff forward
      float cast = entity.getCastProgress(0.0F);
      if (cast > 0.0F) {
         float wind = smoothStep(cast, 0.0F, 0.35F);
         float aim = smoothStep(cast, 0.35F, 0.7F);
         float release = smoothStep(cast, 0.7F, 1.0F);
         float point = wind * 0.6F + aim * 1.0F - release * 0.7F;
         this.rightShoulder.xRot = 0.1F - 0.9F * point;
         this.rightShoulder.yRot = -0.45F * point;
         this.rightForearm.xRot = -0.2F - 0.5F * point;
         this.ritualStaff.xRot = -1.15F + 0.85F * point;
         this.ritualStaff.yRot = -0.2F * point;
         this.torso.yRot += -0.25F * point;
         this.headRoot.xRot = -0.08F + 0.1F * point;
         this.leftShoulder.xRot = 0.1F + 0.2F * point;
         this.staffCore.xScale += 0.3F * aim;
         this.staffCore.yScale += 0.35F * aim;
         this.staffCore.zScale += 0.3F * aim;
      }

      // MELEE — staff / claw swing
      float melee = entity.getMeleeAttackProgress(0.0F);
      if (melee > 0.0F) {
         float wind = smoothStep(melee, 0.0F, 0.3F);
         float strike = Mth.sin(smoothStep(melee, 0.3F, 0.7F) * (float)Math.PI);
         float recover = smoothStep(melee, 0.7F, 1.0F);
         this.rightShoulder.xRot = 0.1F - 1.3F * wind - 1.4F * strike + 0.3F * recover;
         this.rightShoulder.yRot = -0.55F * wind - 0.85F * strike;
         this.rightShoulder.zRot = -0.15F - 0.4F * wind;
         this.rightForearm.xRot = -0.2F - 0.5F * wind - 0.7F * strike;
         this.ritualStaff.xRot = -1.15F + 0.4F * wind + 0.9F * strike;
         this.torso.yRot += -0.35F * wind - 0.45F * strike + 0.15F * recover;
         this.torso.xRot += 0.15F * strike;
         this.pelvis.z = -1.2F * strike;
         this.jaw.xRot = jawIdle + 0.2F * strike;
      }

      // HURT
      float hurt = entity.getHurtReaction(0.0F);
      if (hurt > 0.0F) {
         float h = Mth.sin(hurt * (float)Math.PI);
         this.torso.xRot -= 0.22F * h;
         this.headRoot.zRot += 0.3F * h;
         this.jaw.xRot += 0.35F * h;
         this.ritualCore.xScale += 0.2F * h;
         this.ritualCore.yScale += 0.28F * h;
         for (int i = 0; i < 8; i++) {
            this.robePanels[i].zRot += (i % 2 == 0 ? -0.2F : 0.2F) * h;
         }
      }

      // DEATH collapse
      float death = entity.getDeathReaction(0.0F);
      if (death > 0.0F) {
         float d = Mth.clamp(death, 0.0F, 1.0F);
         this.torso.xRot = 0.12F + 0.95F * d;
         this.pelvis.y = 30.0F + 5.0F * d;
         this.headRoot.xRot = -0.08F + 0.5F * d;
         this.jaw.xRot = 0.25F + 0.6F * d;
         this.rightThigh.xRot = -0.08F - 0.35F * d;
         this.leftThigh.xRot = -0.08F - 0.3F * d;
         this.rightLowerLeg.xRot = 0.12F - 0.4F * d;
         this.leftLowerLeg.xRot = 0.12F - 0.35F * d;
         this.rightShoulder.xRot = 0.1F + 0.7F * d;
         this.leftShoulder.xRot = 0.1F + 0.65F * d;
         this.ritualStaff.xRot = -1.15F + 0.9F * d;
         this.ritualCore.xScale = Math.max(0.05F, corePulse * (1.0F - 0.85F * d));
         this.ritualCore.yScale = Math.max(0.05F, (1.0F + breathe * 0.14F) * (1.0F - 0.85F * d));
         this.ritualCore.zScale = Math.max(0.05F, corePulse * (1.0F - 0.85F * d));
         for (int i = 0; i < 8; i++) {
            this.robePanels[i].xRot += 0.5F * d;
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
