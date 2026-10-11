package org.millenaire.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Player-fitted Bounty Hunter armor. Shares UV atlas layout with the NPC entity texture
 * so wearable gear matches the hunter silhouette (hat, mask, coat, gear).
 *
 * <pre>
 * 256x256 UV atlas (same islands as entity/bounty_hunter.png):
 *   0,0     mask / face              32x32
 *   32,0    hat crown / brim         96x48
 *   128,0   scarf / badge            48x32
 *   0,48    torso / armor / coat     96x48
 *   96,48   belt / pouches           64x32
 *   0,96    coat panels              128x48
 *   0,144   arms / bracers           128x48
 *   128,144 legs / boots             96x48
 *   0,192   equipment (rope/chain)   96x48
 *   96,192  lantern                  64x48
 * Emissive:
 *   200,40  eyes                     16x8
 *   200,52  lantern glow             16x16
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class BountyHunterArmorModel extends HumanoidModel<LivingEntity> {
   private final ModelPart hatCrown;
   private final ModelPart hatBrim;
   private final ModelPart mask;
   private final ModelPart scarf;
   private final ModelPart coatFrontLeft;
   private final ModelPart coatFrontRight;
   private final ModelPart coatSideLeft;
   private final ModelPart coatSideRight;
   private final ModelPart coatRearLeft;
   private final ModelPart coatRearCenter;
   private final ModelPart coatRearRight;
   private final ModelPart lantern;
   private final ModelPart rope;
   private final ModelPart chain;
   private final ModelPart rightBoot;
   private final ModelPart leftBoot;
   private final ModelPart rightKnee;
   private final ModelPart leftKnee;
   private final ModelPart rightPant;
   private final ModelPart leftPant;
   private final boolean slim;

   public BountyHunterArmorModel(ModelPart root, boolean slim) {
      super(root);
      this.slim = slim;
      this.hatCrown = this.head.getChild("hat_crown");
      this.hatBrim = this.head.getChild("hat_brim");
      this.mask = this.head.getChild("mask");
      this.scarf = this.body.getChild("scarf");
      ModelPart coat = this.body.getChild("coat_root");
      this.coatFrontLeft = coat.getChild("front_left_coat");
      this.coatFrontRight = coat.getChild("front_right_coat");
      this.coatSideLeft = coat.getChild("left_side_coat");
      this.coatSideRight = coat.getChild("right_side_coat");
      this.coatRearLeft = coat.getChild("back_left_coat");
      this.coatRearCenter = coat.getChild("back_center_coat");
      this.coatRearRight = coat.getChild("back_right_coat");
      ModelPart gear = this.body.getChild("gear");
      this.lantern = gear.getChild("lantern");
      this.rope = gear.getChild("rope");
      this.chain = gear.getChild("chain");
      this.rightBoot = this.rightLeg.getChild("right_boot");
      this.leftBoot = this.leftLeg.getChild("left_boot");
      this.rightKnee = this.rightLeg.getChild("right_knee_plate");
      this.leftKnee = this.leftLeg.getChild("left_knee_plate");
      this.rightPant = this.rightLeg.getChild("right_pant");
      this.leftPant = this.leftLeg.getChild("left_pant");
   }

   public static LayerDefinition createBodyLayer(boolean slim) {
      MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;

      // Replace vanilla humanoid cubes with armor-specific shells + attachments.
      PartDefinition head = root.addOrReplaceChild(
         "head",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.35F)),
         PartPose.ZERO
      );
      root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

      buildHelmet(head);
      buildBody(root, slim);
      buildArms(root, slim);
      buildLegs(root);

      return LayerDefinition.create(mesh, 256, 256);
   }

   private static void buildHelmet(PartDefinition head) {
      CubeDeformation none = CubeDeformation.NONE;

      PartDefinition mask = head.addOrReplaceChild(
         "mask",
         CubeListBuilder.create()
            .texOffs(0, 16)
            .addBox(-3.6F, -7.2F, -4.55F, 7.2F, 7.0F, 1.4F, none)
            .texOffs(16, 16)
            .addBox(-1.1F, -5.8F, -4.9F, 2.2F, 4.8F, 0.7F, none)
            .texOffs(0, 0)
            .addBox(-3.8F, -7.6F, -4.2F, 7.6F, 2.2F, 1.0F, none),
         PartPose.ZERO
      );
      mask.addOrReplaceChild(
         "eye_left",
         CubeListBuilder.create().texOffs(200, 40).addBox(-1.0F, -0.85F, -0.45F, 2.0F, 1.5F, 0.9F, none),
         PartPose.offset(1.55F, -4.55F, -4.95F)
      );
      mask.addOrReplaceChild(
         "eye_right",
         CubeListBuilder.create().texOffs(208, 40).addBox(-1.0F, -0.85F, -0.45F, 2.0F, 1.5F, 0.9F, none),
         PartPose.offset(-1.55F, -4.55F, -4.95F)
      );
      mask.addOrReplaceChild(
         "lower_grill",
         CubeListBuilder.create()
            .texOffs(8, 24)
            .addBox(-2.6F, -0.2F, -0.2F, 5.2F, 2.4F, 0.5F, none),
         PartPose.offset(0.0F, -2.2F, -4.85F)
      );

      head.addOrReplaceChild(
         "hat_crown",
         CubeListBuilder.create()
            .texOffs(32, 0)
            .addBox(-4.2F, -3.8F, -4.2F, 8.4F, 3.8F, 8.4F, none)
            .texOffs(32, 12)
            .addBox(-3.6F, -4.6F, -3.6F, 7.2F, 1.1F, 7.2F, none)
            .texOffs(64, 0)
            .addBox(-4.4F, -1.2F, -4.4F, 8.8F, 1.0F, 8.8F, none),
         PartPose.offset(0.0F, -8.0F, 0.0F)
      );
      head.addOrReplaceChild(
         "hat_brim",
         CubeListBuilder.create()
            .texOffs(32, 24)
            .addBox(-8.4F, -0.35F, -8.4F, 16.8F, 0.7F, 16.8F, none)
            .texOffs(32, 40)
            .addBox(-7.2F, -0.55F, -8.1F, 14.4F, 0.35F, 2.4F, none)
            .texOffs(64, 40)
            .addBox(-1.4F, -0.75F, -8.6F, 2.8F, 0.4F, 1.2F, none)
            .texOffs(72, 40)
            .addBox(-8.2F, -0.2F, -1.0F, 1.2F, 0.45F, 2.0F, none)
            .texOffs(80, 40)
            .addBox(7.0F, -0.2F, -1.0F, 1.2F, 0.45F, 2.0F, none),
         PartPose.offset(0.0F, -7.85F, 0.0F)
      );
   }

   private static void buildBody(PartDefinition root, boolean slim) {
      CubeDeformation none = CubeDeformation.NONE;
      PartDefinition body = root.addOrReplaceChild(
         "body",
         CubeListBuilder.create()
            .texOffs(0, 48)
            .addBox(-4.0F, 0.0F, -2.5F, 8.0F, 12.0F, 5.0F, new CubeDeformation(0.35F))
            .texOffs(28, 48)
            .addBox(-4.5F, 0.0F, -2.9F, 9.0F, 5.0F, 5.8F, none)
            .texOffs(56, 48)
            .addBox(-5.4F, -0.2F, -2.2F, 2.4F, 3.8F, 4.4F, none)
            .texOffs(68, 48)
            .addBox(3.0F, -0.2F, -2.2F, 2.4F, 3.8F, 4.4F, none),
         PartPose.ZERO
      );

      body.addOrReplaceChild(
         "scarf",
         CubeListBuilder.create()
            .texOffs(128, 8)
            .addBox(-3.8F, -1.2F, -3.3F, 7.6F, 3.6F, 6.6F, none)
            .texOffs(128, 0)
            .addBox(-2.0F, 1.5F, -3.5F, 2.2F, 4.0F, 0.6F, none)
            .texOffs(136, 0)
            .addBox(0.2F, 1.8F, -3.5F, 1.8F, 3.2F, 0.5F, none),
         PartPose.offset(0.0F, 0.2F, 0.0F)
      );

      body.addOrReplaceChild(
         "bounty_badge",
         CubeListBuilder.create()
            .texOffs(128, 0)
            .addBox(-1.4F, -1.4F, -0.35F, 2.8F, 2.8F, 0.7F, none)
            .texOffs(220, 52)
            .addBox(-0.9F, -0.9F, -0.5F, 1.8F, 1.8F, 0.45F, none),
         PartPose.offset(2.35F, 3.2F, -3.15F)
      );

      body.addOrReplaceChild(
         "bandolier",
         CubeListBuilder.create()
            .texOffs(96, 48)
            .addBox(-0.6F, -0.4F, -3.2F, 1.2F, 11.5F, 6.4F, none),
         PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, -0.55F)
      );

      PartDefinition coat = body.addOrReplaceChild("coat_root", CubeListBuilder.create(), PartPose.offset(0.0F, 10.6F, 0.0F));
      coat.addOrReplaceChild(
         "front_left_coat",
         CubeListBuilder.create().texOffs(0, 96).addBox(-0.5F, 0.0F, -0.55F, 3.9F, 15.5F, 1.1F, none),
         PartPose.offsetAndRotation(-2.3F, 0.0F, -3.05F, -0.08F, 0.0F, 0.06F)
      );
      coat.addOrReplaceChild(
         "front_right_coat",
         CubeListBuilder.create().texOffs(16, 96).addBox(-3.4F, 0.0F, -0.55F, 3.9F, 15.5F, 1.1F, none),
         PartPose.offsetAndRotation(2.3F, 0.0F, -3.05F, -0.08F, 0.0F, -0.06F)
      );
      coat.addOrReplaceChild(
         "left_side_coat",
         CubeListBuilder.create().texOffs(80, 96).addBox(-0.45F, 0.0F, -2.5F, 1.0F, 14.5F, 5.0F, none),
         PartPose.offsetAndRotation(-4.7F, 0.15F, 0.0F, 0.03F, 0.0F, 0.12F)
      );
      coat.addOrReplaceChild(
         "right_side_coat",
         CubeListBuilder.create().texOffs(96, 96).addBox(-0.55F, 0.0F, -2.5F, 1.0F, 14.5F, 5.0F, none),
         PartPose.offsetAndRotation(4.7F, 0.15F, 0.0F, 0.03F, 0.0F, -0.12F)
      );
      coat.addOrReplaceChild(
         "back_left_coat",
         CubeListBuilder.create()
            .texOffs(32, 96)
            .addBox(-0.5F, 0.0F, -0.5F, 4.3F, 16.0F, 1.15F, none),
         PartPose.offsetAndRotation(-2.5F, 0.0F, 2.85F, 0.1F, 0.0F, 0.05F)
      );
      coat.addOrReplaceChild(
         "back_center_coat",
         CubeListBuilder.create()
            .texOffs(48, 96)
            .addBox(-1.7F, 0.3F, -0.55F, 3.4F, 14.5F, 0.9F, none),
         PartPose.offsetAndRotation(0.0F, 0.0F, 3.15F, 0.12F, 0.0F, 0.0F)
      );
      coat.addOrReplaceChild(
         "back_right_coat",
         CubeListBuilder.create().texOffs(64, 96).addBox(-3.8F, 0.0F, -0.5F, 4.3F, 16.0F, 1.15F, none),
         PartPose.offsetAndRotation(2.5F, 0.0F, 2.85F, 0.1F, 0.0F, -0.05F)
      );

      PartDefinition gear = body.addOrReplaceChild(
         "gear",
         CubeListBuilder.create()
            .texOffs(96, 56)
            .addBox(-4.6F, -1.0F, -3.1F, 9.2F, 2.2F, 6.2F, none)
            .texOffs(96, 64)
            .addBox(-4.2F, -0.4F, -3.5F, 2.4F, 2.4F, 1.2F, none)
            .texOffs(104, 64)
            .addBox(1.8F, -0.4F, -3.5F, 2.4F, 2.4F, 1.2F, none)
            .texOffs(112, 64)
            .addBox(-1.2F, 0.6F, -3.55F, 2.4F, 2.8F, 0.7F, none),
         PartPose.offset(0.0F, 10.2F, 0.0F)
      );
      gear.addOrReplaceChild(
         "rope",
         CubeListBuilder.create().texOffs(0, 192).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.8F, 3.0F, none),
         PartPose.offsetAndRotation(-4.3F, 0.4F, 1.0F, 0.12F, 0.0F, 0.22F)
      );
      gear.addOrReplaceChild(
         "chain",
         CubeListBuilder.create()
            .texOffs(16, 192)
            .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.5F, 1.0F, none)
            .texOffs(20, 192)
            .addBox(-0.7F, 3.2F, -0.7F, 1.4F, 1.4F, 1.4F, none),
         PartPose.offset(3.6F, 1.0F, -2.8F)
      );
      gear.addOrReplaceChild(
         "lantern",
         CubeListBuilder.create()
            .texOffs(96, 192)
            .addBox(-1.3F, 0.0F, -1.3F, 2.6F, 1.1F, 2.6F, none)
            .texOffs(200, 52)
            .addBox(-1.05F, 1.05F, -1.05F, 2.1F, 2.5F, 2.1F, none)
            .texOffs(108, 192)
            .addBox(-1.25F, 3.45F, -1.25F, 2.5F, 0.75F, 2.5F, none),
         PartPose.offsetAndRotation(4.05F, 0.5F, 1.6F, 0.1F, 0.0F, -0.14F)
      );

      // Keep unused slim flag referenced for future arm-shell tuning via body scale.
      if (slim) {
         // no-op: arm geometry differs in buildArms
      }
   }

   private static void buildArms(PartDefinition root, boolean slim) {
      CubeDeformation none = CubeDeformation.NONE;
      float armWidth = slim ? 3.0F : 4.0F;
      float armU = slim ? -1.0F : -1.0F;
      float rightX = slim ? -5.0F : -5.0F;
      float leftX = slim ? 5.0F : 5.0F;

      PartDefinition rightArm = root.addOrReplaceChild(
         "right_arm",
         CubeListBuilder.create()
            .texOffs(0, 144)
            .addBox(armU - (slim ? 0.5F : 1.0F), -2.0F, -2.0F, armWidth, 12.0F, 4.0F, new CubeDeformation(0.25F))
            .texOffs(0, 164)
            .addBox(armU - (slim ? 0.7F : 1.3F), 4.2F, -2.4F, armWidth + 0.6F, 5.0F, 4.8F, none)
            .texOffs(0, 176)
            .addBox(armU - (slim ? 0.55F : 1.1F), 8.6F, -2.2F, armWidth + 0.3F, 2.8F, 4.4F, none),
         PartPose.offset(rightX, 2.0F, 0.0F)
      );
      rightArm.addOrReplaceChild(
         "right_shoulder_plate",
         CubeListBuilder.create()
            .texOffs(56, 48)
            .addBox(-2.8F, -3.2F, -2.6F, 4.4F, 2.0F, 5.2F, none)
            .texOffs(56, 56)
            .addBox(-2.6F, -1.4F, -2.4F, 4.0F, 1.6F, 4.8F, none)
            .texOffs(56, 62)
            .addBox(-3.1F, -2.4F, -2.0F, 1.0F, 2.6F, 4.0F, none),
         PartPose.offset(0.0F, 0.0F, 0.0F)
      );

      PartDefinition leftArm = root.addOrReplaceChild(
         "left_arm",
         CubeListBuilder.create()
            .texOffs(32, 144)
            .addBox(slim ? -1.5F : -1.0F, -2.0F, -2.0F, armWidth, 12.0F, 4.0F, new CubeDeformation(0.25F))
            .texOffs(32, 164)
            .addBox(slim ? -1.8F : -1.3F, 4.2F, -2.4F, armWidth + 0.6F, 5.0F, 4.8F, none)
            .texOffs(32, 176)
            .addBox(slim ? -1.65F : -1.1F, 8.6F, -2.2F, armWidth + 0.3F, 2.8F, 4.4F, none),
         PartPose.offset(leftX, 2.0F, 0.0F)
      );
      leftArm.addOrReplaceChild(
         "left_shoulder_plate",
         CubeListBuilder.create()
            .texOffs(68, 48)
            .addBox(-1.6F, -3.2F, -2.6F, 4.4F, 2.0F, 5.2F, none)
            .texOffs(68, 56)
            .addBox(-1.4F, -1.4F, -2.4F, 4.0F, 1.6F, 4.8F, none)
            .texOffs(68, 62)
            .addBox(2.1F, -2.4F, -2.0F, 1.0F, 2.6F, 4.0F, none),
         PartPose.offset(0.0F, 0.0F, 0.0F)
      );
   }

   private static void buildLegs(PartDefinition root) {
      CubeDeformation none = CubeDeformation.NONE;

      // Parent parts carry pose only; trousers and boots are separate children for slot visibility.
      PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
      rightLeg.addOrReplaceChild(
         "right_pant",
         CubeListBuilder.create()
            .texOffs(128, 144)
            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F))
            .texOffs(128, 160)
            .addBox(-2.35F, 1.5F, -2.35F, 4.7F, 5.0F, 4.7F, none),
         PartPose.ZERO
      );
      rightLeg.addOrReplaceChild(
         "right_knee_plate",
         CubeListBuilder.create().texOffs(144, 156).addBox(-2.2F, -1.2F, -2.4F, 4.4F, 2.6F, 4.8F, none),
         PartPose.offset(0.0F, 6.5F, 0.0F)
      );
      rightLeg.addOrReplaceChild(
         "right_boot",
         CubeListBuilder.create()
            .texOffs(128, 168)
            .addBox(-2.4F, 8.2F, -2.6F, 4.8F, 3.9F, 5.2F, none)
            .texOffs(128, 180)
            .addBox(-2.5F, 10.6F, -3.3F, 5.0F, 1.5F, 5.8F, none)
            .texOffs(148, 180)
            .addBox(-2.2F, 7.2F, -2.3F, 4.4F, 1.4F, 4.6F, none),
         PartPose.ZERO
      );

      PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
      leftLeg.addOrReplaceChild(
         "left_pant",
         CubeListBuilder.create()
            .texOffs(160, 144)
            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.2F))
            .texOffs(160, 160)
            .addBox(-2.35F, 1.5F, -2.35F, 4.7F, 5.0F, 4.7F, none),
         PartPose.ZERO
      );
      leftLeg.addOrReplaceChild(
         "left_knee_plate",
         CubeListBuilder.create().texOffs(176, 156).addBox(-2.2F, -1.2F, -2.4F, 4.4F, 2.6F, 4.8F, none),
         PartPose.offset(0.0F, 6.5F, 0.0F)
      );
      leftLeg.addOrReplaceChild(
         "left_boot",
         CubeListBuilder.create()
            .texOffs(160, 168)
            .addBox(-2.4F, 8.2F, -2.6F, 4.8F, 3.9F, 5.2F, none)
            .texOffs(160, 180)
            .addBox(-2.5F, 10.6F, -3.3F, 5.0F, 1.5F, 5.8F, none)
            .texOffs(180, 180)
            .addBox(-2.2F, 7.2F, -2.3F, 4.4F, 1.4F, 4.6F, none),
         PartPose.ZERO
      );
   }

   /**
    * Slot-specific visibility. Coat / gear / arms belong to chest; boots are leg children.
    */
   public void prepareForSlot(net.minecraft.world.entity.EquipmentSlot slot, LivingEntity entity) {
      this.setAllVisible(false);
      this.hat.visible = false;

      switch (slot) {
         case HEAD -> {
            this.head.visible = true;
            this.hatCrown.visible = true;
            this.hatBrim.visible = true;
            this.mask.visible = true;
         }
         case CHEST -> {
            this.body.visible = true;
            this.rightArm.visible = true;
            this.leftArm.visible = true;
            this.scarf.visible = true;
            setCoatVisible(true);
            setGearVisible(true);
            // Hide boots that are children of legs (legs not visible for chest).
            boolean elytra = entity != null && entity.isFallFlying();
            if (elytra) {
               this.coatRearLeft.visible = false;
               this.coatRearCenter.visible = false;
               this.coatRearRight.visible = false;
            }
         }
         case LEGS -> {
            this.rightLeg.visible = true;
            this.leftLeg.visible = true;
            this.rightPant.visible = true;
            this.leftPant.visible = true;
            setBootVisible(false);
            setKneeVisible(true);
         }
         case FEET -> {
            this.rightLeg.visible = true;
            this.leftLeg.visible = true;
            this.rightPant.visible = false;
            this.leftPant.visible = false;
            setKneeVisible(false);
            setBootVisible(true);
         }
         default -> {
         }
      }
   }

   private void setCoatVisible(boolean visible) {
      this.coatFrontLeft.visible = visible;
      this.coatFrontRight.visible = visible;
      this.coatSideLeft.visible = visible;
      this.coatSideRight.visible = visible;
      this.coatRearLeft.visible = visible;
      this.coatRearCenter.visible = visible;
      this.coatRearRight.visible = visible;
   }

   private void setGearVisible(boolean visible) {
      this.lantern.visible = visible;
      this.rope.visible = visible;
      this.chain.visible = visible;
   }

   private void setBootVisible(boolean visible) {
      this.rightBoot.visible = visible;
      this.leftBoot.visible = visible;
   }

   private void setKneeVisible(boolean visible) {
      this.rightKnee.visible = visible;
      this.leftKnee.visible = visible;
   }

   public void animateCoat(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks) {
      float walk = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount;
      float coatSwing = walk * 0.28F + Mth.sin(ageInTicks * 0.12F) * 0.03F;
      boolean running = limbSwingAmount > 0.55F;
      boolean crouch = entity != null && entity.isCrouching();

      this.coatFrontLeft.xRot = -0.05F + coatSwing * 0.45F + (crouch ? 0.35F : 0.0F);
      this.coatFrontRight.xRot = -0.05F - coatSwing * 0.45F + (crouch ? 0.35F : 0.0F);
      this.coatSideLeft.zRot = 0.08F + coatSwing * 0.15F;
      this.coatSideRight.zRot = -0.08F - coatSwing * 0.15F;
      this.coatRearLeft.xRot = 0.07F - coatSwing * 0.55F + (running ? 0.22F : 0.0F);
      this.coatRearCenter.xRot = 0.08F - coatSwing * 0.35F + (running ? 0.18F : 0.0F);
      this.coatRearRight.xRot = 0.07F + coatSwing * 0.55F + (running ? 0.22F : 0.0F);

      this.lantern.zRot = -0.14F + Mth.sin(ageInTicks * 0.15F) * 0.08F + coatSwing * 0.2F;
      this.rope.zRot = 0.22F + Mth.sin(ageInTicks * 0.1F) * 0.05F;
      this.chain.xRot = Mth.sin(ageInTicks * 0.18F) * 0.12F;
      this.hatBrim.zRot = Mth.sin(ageInTicks * 0.05F) * 0.015F;
   }

   public boolean isSlim() {
      return this.slim;
   }
}
