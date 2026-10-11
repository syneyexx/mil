package org.millenaire.worldgen.content;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.millenaire.culture.VillageType;

/**
 * Immutable validated view of a village type for worldgen workers / planners.
 */
public final class VillageTypeWorldgenEntry {
   private final ResourceLocation id;
   private final ResourceLocation cultureId;
   private final int weight;
   private final List<TagKey<Biome>> biomeTags;
   private final List<ResourceKey<Biome>> biomeKeys;
   private final float minimumBiomeValidity;
   private final int radius;
   private final boolean loneBuilding;
   private final boolean spawnable;
   private final boolean playerControlled;
   private final boolean keyLoneBuilding;
   private final @Nullable String specialType;
   private final List<ResourceLocation> hamlets;
   private final @Nullable ResourceLocation outerWallType;
   private final @Nullable ResourceLocation innerWallType;
   private final int innerWallRadius;
   private final List<String> pathMaterials;
   private final @Nullable String nameList;
   private final List<LayoutSlotRef> layout;
   private final List<String> validationErrors;
   private final boolean eligibleNatural;

   public VillageTypeWorldgenEntry(VillageType source, List<LayoutSlotRef> layout, List<String> validationErrors) {
      this.id = source.id();
      this.cultureId = source.culture();
      this.weight = source.weight();
      this.biomeTags = List.copyOf(source.biomeTags());
      this.biomeKeys = List.copyOf(source.biomeKeys());
      this.minimumBiomeValidity = source.minimumBiomeValidity();
      this.radius = source.radius();
      this.loneBuilding = source.loneBuilding();
      this.spawnable = source.spawnable();
      this.playerControlled = source.playerControlled();
      this.keyLoneBuilding = source.keyLoneBuilding();
      this.specialType = source.specialType();
      this.hamlets = List.copyOf(source.hamlets());
      this.outerWallType = source.outerWallType();
      this.innerWallType = source.innerWallType();
      this.innerWallRadius = source.innerWallRadius();
      this.pathMaterials = List.copyOf(source.pathMaterials());
      this.nameList = source.nameList();
      this.layout = List.copyOf(layout);
      this.validationErrors = List.copyOf(validationErrors);
      boolean hasCentre = false;
      for (LayoutSlotRef slot : layout) {
         if (slot.isCentre()) {
            hasCentre = true;
            break;
         }
      }
      this.eligibleNatural = validationErrors.isEmpty()
         && source.spawnable()
         && !source.playerControlled()
         && source.weight() > 0
         && (source.loneBuilding() || hasCentre)
         && !"player".equals(source.specialType());
   }

   public ResourceLocation id() {
      return this.id;
   }

   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public int weight() {
      return this.weight;
   }

   public List<TagKey<Biome>> biomeTags() {
      return this.biomeTags;
   }

   public List<ResourceKey<Biome>> biomeKeys() {
      return this.biomeKeys;
   }

   public float minimumBiomeValidity() {
      return this.minimumBiomeValidity;
   }

   public int radius() {
      return this.radius;
   }

   public boolean loneBuilding() {
      return this.loneBuilding;
   }

   public boolean spawnable() {
      return this.spawnable;
   }

   public boolean playerControlled() {
      return this.playerControlled;
   }

   public boolean keyLoneBuilding() {
      return this.keyLoneBuilding;
   }

   @Nullable
   public String specialType() {
      return this.specialType;
   }

   public List<ResourceLocation> hamlets() {
      return this.hamlets;
   }

   @Nullable
   public ResourceLocation outerWallType() {
      return this.outerWallType;
   }

   @Nullable
   public ResourceLocation innerWallType() {
      return this.innerWallType;
   }

   public int innerWallRadius() {
      return this.innerWallRadius;
   }

   public List<String> pathMaterials() {
      return this.pathMaterials;
   }

   @Nullable
   public String nameList() {
      return this.nameList;
   }

   public List<LayoutSlotRef> layout() {
      return this.layout;
   }

   public List<String> validationErrors() {
      return this.validationErrors;
   }

   public boolean eligibleNatural() {
      return this.eligibleNatural;
   }

   public boolean isHamlet() {
      return "hameau".equals(this.specialType);
   }

   public boolean matchesBiome(Holder<Biome> biome) {
      if (biome == null) {
         return false;
      }
      if (this.biomeTags.isEmpty() && this.biomeKeys.isEmpty()) {
         return true;
      }
      for (TagKey<Biome> tag : this.biomeTags) {
         if (biome.is(tag)) {
            return true;
         }
      }
      for (ResourceKey<Biome> key : this.biomeKeys) {
         if (biome.is(key)) {
            return true;
         }
      }
      return false;
   }

   public boolean hasBiomeConstraints() {
      return !this.biomeTags.isEmpty() || !this.biomeKeys.isEmpty();
   }

   public record LayoutSlotRef(
      ResourceLocation planRef,
      @Nullable ResourceLocation planSetId,
      @Nullable ResourceLocation planId,
      String variant,
      int width,
      int depth,
      int groundLevel,
      int height,
      String role,
      @Nullable net.minecraft.core.BlockPos offset,
      @Nullable net.minecraft.world.level.block.Rotation rotation,
      int clearMargin,
      double minDistance,
      double maxDistance,
      boolean mandatory
   ) {
      public boolean isCentre() {
         return "centre".equals(this.role) || "townhall".equals(this.role);
      }

      public boolean isStart() {
         return "start".equals(this.role) || "starter".equals(this.role) || this.isCentre();
      }
   }

   public static List<String> emptyErrors() {
      return new ArrayList<>();
   }
}
