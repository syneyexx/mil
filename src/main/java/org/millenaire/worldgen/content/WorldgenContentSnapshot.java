package org.millenaire.worldgen.content;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.WallType;
import org.millenaire.worldgen.plan.WorldgenSeeds;
import org.slf4j.Logger;

/**
 * Immutable content index captured after CultureLoader.loadAll().
 * Worldgen planners must read this snapshot, never mutate ModCultures from workers.
 */
public final class WorldgenContentSnapshot {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final AtomicReference<WorldgenContentSnapshot> CURRENT = new AtomicReference<>();

   private final int plannerVersion;
   private final Map<ResourceLocation, VillageTypeWorldgenEntry> villageTypes;
   private final List<VillageTypeWorldgenEntry> naturalVillages;
   private final List<VillageTypeWorldgenEntry> naturalLoneBuildings;
   private final Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> villagesByBiomeTag;
   private final Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> loneByBiomeTag;
   private final List<String> reportLines;
   private final int invalidCount;

   private WorldgenContentSnapshot(
      Map<ResourceLocation, VillageTypeWorldgenEntry> villageTypes,
      List<VillageTypeWorldgenEntry> naturalVillages,
      List<VillageTypeWorldgenEntry> naturalLoneBuildings,
      Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> villagesByBiomeTag,
      Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> loneByBiomeTag,
      List<String> reportLines,
      int invalidCount
   ) {
      this.plannerVersion = WorldgenSeeds.PLANNER_VERSION;
      this.villageTypes = Collections.unmodifiableMap(villageTypes);
      this.naturalVillages = List.copyOf(naturalVillages);
      this.naturalLoneBuildings = List.copyOf(naturalLoneBuildings);
      this.villagesByBiomeTag = freezeIndex(villagesByBiomeTag);
      this.loneByBiomeTag = freezeIndex(loneByBiomeTag);
      this.reportLines = List.copyOf(reportLines);
      this.invalidCount = invalidCount;
   }

   private static Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> freezeIndex(Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> src) {
      Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> out = new HashMap<>();
      for (Map.Entry<TagKey<Biome>, List<VillageTypeWorldgenEntry>> e : src.entrySet()) {
         out.put(e.getKey(), List.copyOf(e.getValue()));
      }
      return Collections.unmodifiableMap(out);
   }

   public static void rebuildFromLoadedCultures() {
      Map<ResourceLocation, VillageTypeWorldgenEntry> types = new LinkedHashMap<>();
      List<VillageTypeWorldgenEntry> villages = new ArrayList<>();
      List<VillageTypeWorldgenEntry> lones = new ArrayList<>();
      Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> villageIndex = new HashMap<>();
      Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> loneIndex = new HashMap<>();
      List<String> report = new ArrayList<>();
      int invalid = 0;

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         List<String> errors = new ArrayList<>();
         List<VillageTypeWorldgenEntry.LayoutSlotRef> layout = resolveLayout(vt, errors);
         VillageTypeWorldgenEntry entry = new VillageTypeWorldgenEntry(vt, layout, errors);
         types.put(entry.id(), entry);
         if (!errors.isEmpty()) {
            invalid++;
            for (String err : errors) {
               report.add("INVALID culture=" + vt.culture() + " village=" + vt.id() + " :: " + err);
            }
         } else if (entry.eligibleNatural()) {
            if (entry.loneBuilding()) {
               lones.add(entry);
               indexBiomeTags(loneIndex, entry);
            } else if (!entry.isHamlet()) {
               villages.add(entry);
               indexBiomeTags(villageIndex, entry);
            }
            report.add("OK culture=" + vt.culture() + " village=" + vt.id() + " weight=" + vt.weight() + " lone=" + vt.loneBuilding());
         } else {
            report.add(
               "SKIP culture="
                  + vt.culture()
                  + " village="
                  + vt.id()
                  + " spawnable="
                  + vt.spawnable()
                  + " playerControlled="
                  + vt.playerControlled()
                  + " weight="
                  + vt.weight()
            );
         }
      }

      WorldgenContentSnapshot snap = new WorldgenContentSnapshot(types, villages, lones, villageIndex, loneIndex, report, invalid);
      CURRENT.set(snap);
      LOGGER.info(
         "[Millenaire] Worldgen content snapshot: {} types, {} natural villages, {} lone buildings, {} invalid",
         types.size(),
         villages.size(),
         lones.size(),
         invalid
      );
   }

   private static void indexBiomeTags(Map<TagKey<Biome>, List<VillageTypeWorldgenEntry>> index, VillageTypeWorldgenEntry entry) {
      for (TagKey<Biome> tag : entry.biomeTags()) {
         index.computeIfAbsent(tag, k -> new ArrayList<>()).add(entry);
      }
   }

   private static List<VillageTypeWorldgenEntry.LayoutSlotRef> resolveLayout(VillageType vt, List<String> errors) {
      List<VillageTypeWorldgenEntry.LayoutSlotRef> out = new ArrayList<>();
      boolean sawCentre = false;
      for (VillageType.LayoutSlot slot : vt.layout()) {
         ResourceLocation planRef = slot.plan();
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planRef);
         if (planSet != null) {
            String variant = planSet.variants().isEmpty() ? "default" : planSet.variants().keySet().iterator().next();
            BuildingPlanSet.LevelDef level0 = planSet.getLevel(variant, 0);
            if (level0 == null) {
               errors.add("MISSING_MANDATORY_BUILDING planSet=" + planRef + " variant=" + variant + " level=0 source=layout");
               continue;
            }
            BuildingPlan plan = ModCultures.getBuildingPlan(level0.planId());
            if (plan == null) {
               errors.add("MISSING_TEMPLATE planSet=" + planRef + " planId=" + level0.planId() + " source=layout");
               continue;
            }
            if (plan.width() <= 0 || plan.depth() <= 0) {
               errors.add("INVALID_DIMENSIONS plan=" + plan.id() + " w=" + plan.width() + " d=" + plan.depth());
            }
            boolean mandatory = isCentre(slot.role()) || isStart(slot.role());
            if (isCentre(slot.role())) {
               sawCentre = true;
            }
            out.add(
               new VillageTypeWorldgenEntry.LayoutSlotRef(
                  planRef,
                  planSet.id(),
                  plan.id(),
                  variant,
                  plan.width(),
                  plan.depth(),
                  plan.groundLevel(),
                  plan.height(),
                  slot.role() == null ? "" : slot.role(),
                  slot.offset(),
                  slot.rotation(),
                  slot.clearMargin(),
                  slot.minDistance(),
                  slot.maxDistance(),
                  mandatory
               )
            );
            continue;
         }

         BuildingPlan direct = ModCultures.getBuildingPlan(planRef);
         if (direct != null) {
            if (isCentre(slot.role())) {
               sawCentre = true;
            }
            out.add(
               new VillageTypeWorldgenEntry.LayoutSlotRef(
                  planRef,
                  null,
                  direct.id(),
                  "default",
                  direct.width(),
                  direct.depth(),
                  direct.groundLevel(),
                  direct.height(),
                  slot.role() == null ? "" : slot.role(),
                  slot.offset(),
                  slot.rotation(),
                  slot.clearMargin(),
                  slot.minDistance(),
                  slot.maxDistance(),
                  isCentre(slot.role()) || isStart(slot.role())
               )
            );
         } else {
            errors.add("MISSING_TEMPLATE planRef=" + planRef + " role=" + slot.role() + " source=layout");
         }
      }

      if (!vt.loneBuilding() && !sawCentre && vt.spawnable() && !vt.playerControlled()) {
         errors.add("MISSING_MANDATORY_BUILDING reason=no_centre_slot");
      }

      if (vt.outerWallType() != null) {
         WallType wall = ModCultures.getWallType(vt.outerWallType());
         if (wall == null) {
            errors.add("INVALID_WALL outer=" + vt.outerWallType());
         }
      }
      if (vt.innerWallType() != null) {
         WallType wall = ModCultures.getWallType(vt.innerWallType());
         if (wall == null) {
            errors.add("INVALID_WALL inner=" + vt.innerWallType());
         }
      }
      for (ResourceLocation hamletId : vt.hamlets()) {
         if (ModCultures.getVillageType(hamletId) == null) {
            errors.add("INVALID_HAMLET_REF " + hamletId);
         }
      }
      return out;
   }

   private static boolean isCentre(String role) {
      return "centre".equals(role) || "townhall".equals(role);
   }

   private static boolean isStart(String role) {
      return "start".equals(role) || "starter".equals(role);
   }

   @Nullable
   public static WorldgenContentSnapshot current() {
      return CURRENT.get();
   }

   public static WorldgenContentSnapshot require() {
      WorldgenContentSnapshot snap = CURRENT.get();
      if (snap == null) {
         rebuildFromLoadedCultures();
         snap = CURRENT.get();
      }
      return snap;
   }

   public int plannerVersion() {
      return this.plannerVersion;
   }

   public Map<ResourceLocation, VillageTypeWorldgenEntry> villageTypes() {
      return this.villageTypes;
   }

   @Nullable
   public VillageTypeWorldgenEntry get(ResourceLocation id) {
      return this.villageTypes.get(id);
   }

   public List<VillageTypeWorldgenEntry> naturalVillages() {
      return this.naturalVillages;
   }

   public List<VillageTypeWorldgenEntry> naturalLoneBuildings() {
      return this.naturalLoneBuildings;
   }

   public List<String> reportLines() {
      return this.reportLines;
   }

   public int invalidCount() {
      return this.invalidCount;
   }

   public List<VillageTypeWorldgenEntry> matchingVillages(java.util.function.Predicate<TagKey<Biome>> biomeMatcher) {
      return match(this.naturalVillages, biomeMatcher);
   }

   public List<VillageTypeWorldgenEntry> matchingLoneBuildings(java.util.function.Predicate<TagKey<Biome>> biomeMatcher) {
      return match(this.naturalLoneBuildings, biomeMatcher);
   }

   private static List<VillageTypeWorldgenEntry> match(List<VillageTypeWorldgenEntry> pool, java.util.function.Predicate<TagKey<Biome>> biomeMatcher) {
      List<VillageTypeWorldgenEntry> out = new ArrayList<>();
      for (VillageTypeWorldgenEntry e : pool) {
         if (e.biomeTags().isEmpty()) {
            out.add(e);
            continue;
         }
         for (TagKey<Biome> tag : e.biomeTags()) {
            if (biomeMatcher.test(tag)) {
               out.add(e);
               break;
            }
         }
      }
      return out;
   }

   @Nullable
   public VillageTypeWorldgenEntry pickWeighted(List<VillageTypeWorldgenEntry> candidates, RandomSource random) {
      if (candidates.isEmpty()) {
         return null;
      }
      int total = 0;
      for (VillageTypeWorldgenEntry e : candidates) {
         total += Math.max(0, e.weight());
      }
      if (total <= 0) {
         return candidates.get(random.nextInt(candidates.size()));
      }
      int roll = random.nextInt(total);
      int acc = 0;
      for (VillageTypeWorldgenEntry e : candidates) {
         acc += Math.max(0, e.weight());
         if (roll < acc) {
            return e;
         }
      }
      return candidates.getLast();
   }

   /**
    * Deterministic variant pick for a plan set using a seeded random.
    */
   public static String pickVariant(BuildingPlanSet planSet, RandomSource random) {
      List<String> keys = List.copyOf(planSet.variants().keySet());
      if (keys.isEmpty()) {
         return "default";
      }
      return keys.get(random.nextInt(keys.size()));
   }
}
