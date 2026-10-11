package org.millenaire.compat.xaero;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.millenaire.client.map.SettlementMapClientCache;
import org.millenaire.config.MillenaireClientConfig;
import org.millenaire.map.sync.SettlementMapEntry;
import org.millenaire.map.sync.SettlementMapVisibility;
import org.slf4j.Logger;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.hud.path.XaeroPath;

/**
 * Projects Millénaire settlement DTOs into Xaero Minimap third-party waypoints
 * (shared with World Map). Pattern mirrors Xaero's SupportWaystones integration.
 */
public final class XaeroSettlementWaypointSync {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ORIGIN_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "settlements");
   private static boolean loggedSuccess;

   private XaeroSettlementWaypointSync() {
   }

   public static void sync(SettlementMapClientCache cache) {
      MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
      if (session == null) {
         return;
      }
      MinimapWorldManager worldManager = session.getWorldManager();
      MinimapWorldRootContainer root = worldManager.getAutoRootContainer();
      if (root == null) {
         return;
      }
      ResourceKey<Level> overworld = Level.OVERWORLD;
      String dimDir = session.getDimensionHelper().getDimensionDirectoryName(overworld);
      XaeroPath path = root.getPath().resolve(dimDir);
      MinimapWorldContainer container = root.addSubContainer(path);
      ThirdPartyWaypointManager manager = container.getThirdPartyWaypointManager();
      ThirdPartyWaypoints waypoints = manager.get(ORIGIN_ID);
      if (!waypoints.hasEnabledStateGetter()) {
         waypoints.setEnabledStateGetter(() -> {
            try {
               return Boolean.TRUE.equals(MillenaireClientConfig.CLIENT.showXaeroSettlements.get());
            } catch (IllegalStateException e) {
               return true;
            }
         });
      }

      boolean showHamlets = cfg(MillenaireClientConfig.CLIENT.showHamlets, true);
      boolean showVillages = cfg(MillenaireClientConfig.CLIENT.showVillages, true);
      boolean showTowns = cfg(MillenaireClientConfig.CLIENT.showTowns, true);
      boolean showCities = cfg(MillenaireClientConfig.CLIENT.showCities, true);
      boolean showMyCity = cfg(MillenaireClientConfig.CLIENT.showMyCity, true);
      boolean showNames = cfg(MillenaireClientConfig.CLIENT.showSettlementNames, true);
      boolean showApproxBounds = cfg(MillenaireClientConfig.CLIENT.showApproxBounds, false);

      Set<String> keep = new HashSet<>();
      for (SettlementMapEntry entry : cache.entries()) {
         if (!SettlementMapVisibility.clientCategoryVisible(
            entry.developmentLabel(),
            entry.isSovereignPlayerCity(),
            showHamlets,
            showVillages,
            showTowns,
            showCities,
            showMyCity
         )) {
            continue;
         }
         String id = entry.villageId().toString();
         keep.add(id);
         String name = showNames ? entry.name() : symbolFor(entry);
         String symbol = symbolFor(entry);
         WaypointColor color = colorFor(entry);
         Waypoint wp = new Waypoint(entry.x(), entry.y(), entry.z(), name, symbol, color, WaypointPurpose.NORMAL);
         waypoints.add(id, wp);

         // Optional approximate building envelope corner (not a territorial claim).
         if (showApproxBounds && entry.approxBounds() != null) {
            SettlementMapEntry.ApproxBounds b = entry.approxBounds();
            String boundId = id + ":bounds";
            keep.add(boundId);
            String label = showNames ? (entry.name() + " [approx]") : "□";
            Waypoint corner = new Waypoint(b.minX(), b.minY(), b.minZ(), label, "□", WaypointColor.DARK_GRAY, WaypointPurpose.NORMAL);
            waypoints.add(boundId, corner);
         }
      }

      Set<String> existing = new HashSet<>();
      for (String existingId : waypoints.getIds()) {
         existing.add(existingId);
      }
      for (String existingId : existing) {
         if (!keep.contains(existingId)) {
            waypoints.remove(existingId);
         }
      }

      if (!loggedSuccess && !keep.isEmpty()) {
         loggedSuccess = true;
         LOGGER.info(
            "[Millenaire] Synced {} settlement marker(s) to Xaero third-party waypoints (origin {})",
            keep.size(),
            ORIGIN_ID
         );
      }
   }

   public static void clearAll() {
      MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
      if (session == null) {
         return;
      }
      MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
      if (root == null) {
         return;
      }
      for (MinimapWorldContainer sub : root.getSubContainers()) {
         ThirdPartyWaypointManager manager = sub.getThirdPartyWaypointManager();
         ThirdPartyWaypoints waypoints = manager.get(ORIGIN_ID);
         waypoints.clear();
      }
      root.getThirdPartyWaypointManager().get(ORIGIN_ID).clear();
   }

   private static String symbolFor(SettlementMapEntry entry) {
      if (entry.isSovereignPlayerCity()) {
         return "★";
      }
      return switch (entry.developmentLabel()) {
         case "hamlet" -> "h";
         case "town" -> "T";
         case "regional_hub" -> "C";
         default -> "V";
      };
   }

   private static WaypointColor colorFor(SettlementMapEntry entry) {
      if (entry.isSovereignPlayerCity()) {
         return WaypointColor.GOLD;
      }
      if (entry.isHostile()) {
         return WaypointColor.RED;
      }
      if (entry.isControlled()) {
         return WaypointColor.GREEN;
      }
      return switch (entry.developmentLabel()) {
         case "hamlet" -> WaypointColor.GRAY;
         case "town" -> WaypointColor.AQUA;
         case "regional_hub" -> WaypointColor.BLUE;
         default -> WaypointColor.DARK_GREEN;
      };
   }

   private static boolean cfg(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value, boolean def) {
      try {
         return Boolean.TRUE.equals(value.get());
      } catch (IllegalStateException e) {
         return def;
      }
   }
}
