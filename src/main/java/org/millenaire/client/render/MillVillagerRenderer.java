package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.millenaire.DisplayUtils;
import org.millenaire.client.ClientLanguageCache;
import org.millenaire.client.ClientQuestCache;
import org.millenaire.client.model.MillFemaleAsymModel;
import org.millenaire.client.model.MillFemaleSymModel;
import org.millenaire.client.model.MillMaleModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.config.MillenaireClientConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModelType;
import org.millenaire.item.ModItems;
import org.millenaire.language.DisplayNameResolver;
import org.millenaire.language.SpeechResolver;

@OnlyIn(Dist.CLIENT)
public class MillVillagerRenderer extends MobRenderer<MillVillager, HumanoidModel<MillVillager>> {
   private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/villager/default.png");
   private static final BufferSource NAME_TAG_BUFFER = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
   private final HumanoidModel<MillVillager> maleModel = (HumanoidModel<MillVillager>)this.model;
   private final HumanoidModel<MillVillager> femaleSymModel;
   private final HumanoidModel<MillVillager> femaleAsymModel;
   private UUID nameCacheEntityId;
   private String nameCacheDisplayName;
   private String nameCacheNativeRole;
   private String nameCacheRoleName;
   private String nameCacheGoalLabel;
   private String nameCacheQuestLabel;
   private String nameCacheSpeechText;
   private boolean nameCacheCanRead;
   private boolean nameCacheHired;
   private boolean nameCacheAggressive;
   private int nameCacheHireHours;
   private int nameCacheHealthHearts;
   private int nameCacheMaxHearts;
   private Component nameCacheNameRoleLine;
   private Component nameCacheGoalLine;
   private Component nameCacheQuestLine;
   private Component nameCacheHireLine;
   private List<Component> nameCacheSpeechNative = List.of();
   private List<Component> nameCacheSpeechTranslation = List.of();

   public MillVillagerRenderer(Context context) {
      super(context, new MillMaleModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_MALE)), 0.5F);
      this.femaleSymModel = new MillFemaleSymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_SYM));
      this.femaleAsymModel = new MillFemaleAsymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM));
      Map<ModelType, HumanoidModel<MillVillager>> clothModels0 = Map.of(
         ModelType.MALE,
         new MillMaleModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_0)),
         ModelType.FEMALE_SYM,
         new MillFemaleSymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_0)),
         ModelType.FEMALE_ASYM,
         new MillFemaleAsymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_0))
      );
      Map<ModelType, HumanoidModel<MillVillager>> clothModels1 = Map.of(
         ModelType.MALE,
         new MillMaleModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_MALE_CLOTH_1)),
         ModelType.FEMALE_SYM,
         new MillFemaleSymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_SYM_CLOTH_1)),
         ModelType.FEMALE_ASYM,
         new MillFemaleAsymModel(context.bakeLayer(MillModelLayers.MILL_VILLAGER_FEMALE_ASYM_CLOTH_1))
      );
      this.addLayer(new ClothingLayer(this, clothModels0, 0));
      this.addLayer(new ClothingLayer(this, clothModels1, 1));
      this.addLayer(
         new HumanoidArmorLayer(
            this,
            new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
         )
      );
      this.addLayer(new ItemInHandLayer(this, context.getItemInHandRenderer()));
   }

   public void render(MillVillager entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      this.model = switch (entity.getModelType()) {
         case FEMALE_SYM -> this.femaleSymModel;
         case FEMALE_ASYM -> this.femaleAsymModel;
         default -> this.maleModel;
      };
      ((HumanoidModel)this.model).rightArmPose = entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
      ((HumanoidModel)this.model).leftArmPose = entity.getItemInHand(InteractionHand.OFF_HAND).isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      if (this.isFullNameDetail(entity)) {
         List<ItemStack> floatingIcons = defineFloatingIcons(entity);
         if (!floatingIcons.isEmpty()) {
            this.renderFloatingIcons(entity, floatingIcons, poseStack, bufferSource, packedLight);
         }
      }
   }

   private boolean isFullNameDetail(MillVillager entity) {
      int dist = MillenaireClientConfig.CLIENT.namesDistance.getAsInt();
      double fullDetailDist = dist * 0.45;
      return this.entityRenderDispatcher.distanceToSqr(entity) <= fullDetailDist * fullDetailDist;
   }

   protected void setupRotations(MillVillager entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
      if (entity.isVillagerSleeping()) {
         float sleepRot = 180.0F - entity.getYRot();
         poseStack.mulPose(Axis.YP.rotationDegrees(sleepRot));
         poseStack.mulPose(Axis.ZP.rotationDegrees(this.getFlipDegrees(entity)));
         poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
      } else {
         super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
      }
   }

   public ResourceLocation getTextureLocation(MillVillager entity) {
      ResourceLocation tex = entity.getTexture();
      return tex != null ? tex : DEFAULT_TEXTURE;
   }

   protected boolean shouldShowName(MillVillager entity) {
      if (entity.isGuiPreviewMode()) {
         return false;
      } else if (!(Boolean)MillenaireClientConfig.CLIENT.showNames.get()) {
         return false;
      } else {
         String name = entity.getVillagerDisplayName();
         if (name != null && !name.isEmpty()) {
            int dist = MillenaireClientConfig.CLIENT.namesDistance.getAsInt();
            double distanceSq = this.entityRenderDispatcher.distanceToSqr(entity);
            return distanceSq <= (double)dist * dist;
         } else {
            return false;
         }
      }
   }

   protected void renderNameTag(MillVillager entity, Component content, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
      String displayName = entity.getVillagerDisplayName();
      if (displayName != null && !displayName.isEmpty()) {
         boolean fullDetail = this.isFullNameDetail(entity);
         this.refreshNameTagCache(entity, fullDetail);
         Font font = this.getFont();
         Component nameRoleLine = this.nameCacheNameRoleLine;
         Component goalLine = fullDetail ? this.nameCacheGoalLine : null;
         Component questLine = fullDetail ? this.nameCacheQuestLine : null;
         Component hireLine = fullDetail ? this.nameCacheHireLine : null;
         boolean hasGoal = goalLine != null;
         boolean hasQuest = questLine != null;
         boolean hasHire = hireLine != null;
         int lineHeight = 9 + 1;
         int bgColor = -1342177280;
         poseStack.pushPose();
         float scale = entity.getVillagerScale();
         float nameTagHeight = entity.isVillagerSleeping() ? 0.2F * scale + 0.5F : entity.getBbHeight() + 0.5F;
         poseStack.translate(0.0, nameTagHeight, 0.0);
         poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
         poseStack.scale(0.025F, -0.025F, 0.025F);
         Matrix4f matrix = poseStack.last().pose();
         int emissiveLight = 15728880;
         BufferSource immediateBuffer = NAME_TAG_BUFFER;
         float nameY = -lineHeight;
         float questY = nameY - (hasQuest ? lineHeight : 0);
         float goalY = (hasQuest ? questY : nameY) - (hasGoal ? lineHeight : 0);
         if (hasGoal) {
            float goalX = -font.width(goalLine) / 2.0F;
            drawNameLine(font, goalLine, goalX, goalY, -2307474, matrix, immediateBuffer, bgColor, emissiveLight);
         }

         if (hasQuest) {
            float questX = -font.width(questLine) / 2.0F;
            drawNameLine(font, questLine, questX, questY, -1596072483, matrix, immediateBuffer, bgColor, emissiveLight);
         }

         float hireY = (hasGoal ? goalY : (hasQuest ? questY : nameY)) - (hasHire ? lineHeight : 0);
         if (hasHire) {
            float hireX = -font.width(hireLine) / 2.0F;
            drawNameLine(font, hireLine, hireX, hireY, -2307474, matrix, immediateBuffer, bgColor, emissiveLight);
         }

         float nameX = -font.width(nameRoleLine) / 2.0F;
         drawNameLine(font, nameRoleLine, nameX, nameY, -1, matrix, immediateBuffer, bgColor, emissiveLight);
         if (fullDetail && (!this.nameCacheSpeechNative.isEmpty() || !this.nameCacheSpeechTranslation.isEmpty())) {
            int speechBg = -1342177280;
            float topLine = hasHire ? hireY : (hasGoal ? goalY : (hasQuest ? questY : nameY));
            float currentY = topLine - lineHeight - 2.0F;

            for (int i = this.nameCacheSpeechTranslation.size() - 1; i >= 0; i--) {
               Component transComp = this.nameCacheSpeechTranslation.get(i);
               float transX = -font.width(transComp) / 2.0F;
               drawNameLine(font, transComp, transX, currentY, -5636096, matrix, immediateBuffer, speechBg, emissiveLight);
               currentY -= lineHeight;
            }

            for (int i = this.nameCacheSpeechNative.size() - 1; i >= 0; i--) {
               Component nativeComp = this.nameCacheSpeechNative.get(i);
               float nativeX = -font.width(nativeComp) / 2.0F;
               drawNameLine(font, nativeComp, nativeX, currentY, -11184641, matrix, immediateBuffer, speechBg, emissiveLight);
               currentY -= lineHeight;
            }
         }

         immediateBuffer.endBatch();
         poseStack.popPose();
      }
   }

   private void refreshNameTagCache(MillVillager entity, boolean fullDetail) {
      String displayName = entity.getVillagerDisplayName();
      String nativeRole = entity.getNativeRoleName();
      String roleName = entity.getRoleName();
      ResourceLocation cultureId = entity.getCultureId();
      boolean canReadNames = cultureId != null && ClientLanguageCache.canReadVillagerNames(cultureId);
      String goalLabel = fullDetail ? entity.getGoalLabel() : null;
      String questLabel = fullDetail ? ClientQuestCache.getQuestLabelForVillager(entity.getUUID()) : null;
      String speechText = fullDetail ? entity.getSpeechText() : null;
      boolean hired = fullDetail && entity.clientIsHired();
      boolean aggressive = hired && entity.clientIsAggressive();
      int hireHours = hired ? entity.clientHireHoursLeft() : 0;
      int healthHearts = hired ? (int)entity.getHealth() / 2 : 0;
      int maxHearts = hired ? (int)entity.getMaxHealth() / 2 : 0;

      boolean sameEntity = Objects.equals(this.nameCacheEntityId, entity.getUUID());
      boolean unchanged = sameEntity
         && Objects.equals(this.nameCacheDisplayName, displayName)
         && Objects.equals(this.nameCacheNativeRole, nativeRole)
         && Objects.equals(this.nameCacheRoleName, roleName)
         && this.nameCacheCanRead == canReadNames
         && Objects.equals(this.nameCacheGoalLabel, goalLabel)
         && Objects.equals(this.nameCacheQuestLabel, questLabel)
         && Objects.equals(this.nameCacheSpeechText, speechText)
         && this.nameCacheHired == hired
         && this.nameCacheAggressive == aggressive
         && this.nameCacheHireHours == hireHours
         && this.nameCacheHealthHearts == healthHearts
         && this.nameCacheMaxHearts == maxHearts;
      if (unchanged) {
         return;
      }

      this.nameCacheEntityId = entity.getUUID();
      this.nameCacheDisplayName = displayName;
      this.nameCacheNativeRole = nativeRole;
      this.nameCacheRoleName = roleName;
      this.nameCacheCanRead = canReadNames;
      this.nameCacheGoalLabel = goalLabel;
      this.nameCacheQuestLabel = questLabel;
      this.nameCacheSpeechText = speechText;
      this.nameCacheHired = hired;
      this.nameCacheAggressive = aggressive;
      this.nameCacheHireHours = hireHours;
      this.nameCacheHealthHearts = healthHearts;
      this.nameCacheMaxHearts = maxHearts;

      String nameStr = !displayName.startsWith("entity.") && !displayName.startsWith("role.")
         ? displayName
         : Component.translatable(displayName).getString();
      String nameRoleStr;
      if (nativeRole != null && !nativeRole.isEmpty()) {
         if (canReadNames && roleName != null && !roleName.isEmpty()) {
            String translatedRole = roleName.startsWith("role.") ? Component.translatable(roleName).getString() : roleName;
            nameRoleStr = DisplayNameResolver.equivalent(nativeRole, translatedRole)
               ? nameStr + ", " + nativeRole
               : nameStr + ", " + nativeRole + " (" + translatedRole + ")";
         } else {
            nameRoleStr = nameStr + ", " + nativeRole;
         }
      } else {
         nameRoleStr = nameStr;
      }
      this.nameCacheNameRoleLine = Component.literal(nameRoleStr);
      this.nameCacheGoalLine = goalLabel != null && !goalLabel.isEmpty() ? DisplayUtils.resolveGoalLabelComponent(goalLabel) : null;
      this.nameCacheQuestLine = questLabel != null ? Component.literal("[" + questLabel + "]") : null;
      if (hired) {
         String stanceKey = aggressive ? "gui.millenaire.hire.aggressive" : "gui.millenaire.hire.passive";
         String hireStr = Component.translatable("gui.millenaire.hire.health", String.valueOf(healthHearts), String.valueOf(maxHearts)).getString()
            + " — "
            + Component.translatable(stanceKey).getString()
            + " — "
            + Component.translatable("gui.millenaire.hire.hiredvillager", hireHours).getString();
         this.nameCacheHireLine = Component.literal(hireStr);
      } else {
         this.nameCacheHireLine = null;
      }

      if (speechText != null && !speechText.isEmpty()) {
         String[] speechParts = resolveSpeechLines(speechText, entity);
         List<String> nativeLines = speechParts[0] != null ? wordWrap(speechParts[0], 60) : List.of();
         List<String> translationLines = speechParts[1] != null ? wordWrap(speechParts[1], 60) : List.of();
         List<Component> nativeComps = new ArrayList<>(nativeLines.size());
         for (String line : nativeLines) {
            nativeComps.add(Component.literal(line));
         }
         List<Component> transComps = new ArrayList<>(translationLines.size());
         for (String line : translationLines) {
            transComps.add(Component.literal(line));
         }
         this.nameCacheSpeechNative = nativeComps;
         this.nameCacheSpeechTranslation = transComps;
      } else {
         this.nameCacheSpeechNative = List.of();
         this.nameCacheSpeechTranslation = List.of();
      }
   }

   private static void drawNameLine(
      Font font, Component text, float x, float y, int color, Matrix4f matrix, MultiBufferSource bufferSource, int bgColor, int emissiveLight
   ) {
      // Single pass is enough beyond very close range; dual SEE_THROUGH+NORMAL was ~2x font cost.
      font.drawInBatch(text, x, y, color, false, matrix, bufferSource, DisplayMode.NORMAL, bgColor, emissiveLight);
   }

   private static String[] resolveSpeechLines(String speechRef, MillVillager entity) {
      return SpeechResolver.resolve(speechRef);
   }

   private static final ItemStack CHIEF_ICON = new ItemStack(Items.GOLDEN_HELMET);
   private static ItemStack purseIconCached;

   private static List<ItemStack> defineFloatingIcons(MillVillager entity) {
      // Reuse stacks — allocating ItemStacks every frame was a client FPS tax in busy villages.
      List<ItemStack> icons = new ArrayList<>(3);
      if (entity.isChief()) {
         icons.add(CHIEF_ICON);
      }
      if (entity.isSelling() || entity.isForeignMerchant()) {
         if (purseIconCached == null) {
            purseIconCached = new ItemStack((ItemLike)ModItems.PURSE.get());
         }
         icons.add(purseIconCached);
      }
      return icons;
   }

   private void renderFloatingIcons(MillVillager entity, List<ItemStack> icons, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      float iconHeight = entity.getBbHeight() + 1.3F;
      poseStack.pushPose();
      poseStack.translate(0.0, iconHeight, 0.0);
      poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
      poseStack.scale(0.5F, 0.5F, 0.5F);
      float iconSpacing = 0.8F;
      float startX = -((icons.size() - 1) * iconSpacing) / 2.0F;

      for (int i = 0; i < icons.size(); i++) {
         poseStack.pushPose();
         poseStack.translate(startX + i * iconSpacing, 0.0, 0.0);
         Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(icons.get(i), ItemDisplayContext.GUI, 15728880, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());
         poseStack.popPose();
      }

      poseStack.popPose();
   }

   private static List<String> wordWrap(String text, int maxChars) {
      if (text.length() <= maxChars) {
         return List.of(text);
      }

      List<String> lines = new ArrayList<>();
      int start = 0;

      while (start < text.length()) {
         if (start + maxChars >= text.length()) {
            lines.add(text.substring(start));
            break;
         }

         int end = start + maxChars;
         int lastSpace = text.lastIndexOf(32, end);
         if (lastSpace <= start) {
            lastSpace = end;
         }

         lines.add(text.substring(start, lastSpace));
         start = lastSpace < text.length() && text.charAt(lastSpace) == ' ' ? lastSpace + 1 : lastSpace;
      }

      return lines;
   }
}
