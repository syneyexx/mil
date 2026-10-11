package org.millenaire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class MillenaireClientConfig {
   public static final MillenaireClientConfig CLIENT;
   public static final ModConfigSpec SPEC;
   public final BooleanValue showStartMessage;
   public final BooleanValue showNames;
   public final IntValue namesDistance;
   public final BooleanValue enableXaeroSync;
   public final BooleanValue showXaeroSettlements;
   public final BooleanValue showHamlets;
   public final BooleanValue showVillages;
   public final BooleanValue showTowns;
   public final BooleanValue showCities;
   public final BooleanValue showMyCity;
   public final BooleanValue showSettlementNames;
   public final BooleanValue showApproxBounds;

   private MillenaireClientConfig(Builder builder) {
      builder.comment("Display settings").push("display");
      this.showStartMessage = builder.comment("Display Millenaire version at startup (not yet wired)")
         .translation("millenaire.config.showStartMessage")
         .define("showStartMessage", true);
      this.showNames = builder.comment("Display names and occupations above villagers").translation("millenaire.config.showNames").define("showNames", true);
      this.namesDistance = builder.comment("Distance from which villager names are visible (blocks)")
         .translation("millenaire.config.namesDistance")
         .defineInRange("namesDistance", 12, 5, 50);
      builder.pop();

      builder.comment("Xaero World Map / Minimap settlement markers (requires Xaero Minimap)").push("xaero_map");
      this.enableXaeroSync = builder.comment("Sync Millénaire settlements to Xaero third-party waypoints")
         .translation("millenaire.config.enableXaeroSync")
         .define("enableXaeroSync", true);
      this.showXaeroSettlements = builder.comment("Show Millénaire settlement markers on Xaero maps")
         .translation("millenaire.config.showXaeroSettlements")
         .define("showXaeroSettlements", true);
      this.showHamlets = builder.comment("Show hamlet markers")
         .translation("millenaire.config.showHamlets")
         .define("showHamlets", true);
      this.showVillages = builder.comment("Show village markers")
         .translation("millenaire.config.showVillages")
         .define("showVillages", true);
      this.showTowns = builder.comment("Show town markers")
         .translation("millenaire.config.showTowns")
         .define("showTowns", true);
      this.showCities = builder.comment("Show city / regional hub markers")
         .translation("millenaire.config.showCities")
         .define("showCities", true);
      this.showMyCity = builder.comment("Show your sovereign / controlled city with gold highlight")
         .translation("millenaire.config.showMyCity")
         .define("showMyCity", true);
      this.showSettlementNames = builder.comment("Show settlement names on markers")
         .translation("millenaire.config.showSettlementNames")
         .define("showSettlementNames", true);
      this.showApproxBounds = builder.comment(
            "Show approximate building-envelope corner markers (NOT authoritative territory borders)"
         )
         .translation("millenaire.config.showApproxBounds")
         .define("showApproxBounds", false);
      builder.pop();
   }

   static {
      Pair<MillenaireClientConfig, ModConfigSpec> pair = new Builder().configure(MillenaireClientConfig::new);
      CLIENT = (MillenaireClientConfig)pair.getLeft();
      SPEC = (ModConfigSpec)pair.getRight();
   }
}
