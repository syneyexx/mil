package org.millenaire.compat.xaero;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.millenaire.client.map.SettlementMapClientCache;
import org.millenaire.config.MillenaireClientConfig;
import org.slf4j.Logger;

/**
 * Soft gate for Xaero Minimap third-party waypoint sync. No Xaero types here.
 */
public final class XaeroCompatBootstrap {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static boolean available;
   private static boolean disabled;
   private static boolean loggedOnce;
   private static int lastSyncedRevision = Integer.MIN_VALUE;

   private XaeroCompatBootstrap() {
   }

   public static void initClient() {
      available = ModList.get().isLoaded("xaerominimap");
      if (available) {
         LOGGER.info("[Millenaire] Xaero Minimap detected — settlement map waypoints enabled (set origin millenaire:settlements)");
      } else if (!loggedOnce) {
         loggedOnce = true;
         LOGGER.debug("[Millenaire] Xaero Minimap not present — settlement World Map markers inactive");
      }
   }

   public static boolean isActive() {
      if (disabled || !available) {
         return false;
      }
      try {
         return Boolean.TRUE.equals(MillenaireClientConfig.CLIENT.enableXaeroSync.get())
            && Boolean.TRUE.equals(MillenaireClientConfig.CLIENT.showXaeroSettlements.get());
      } catch (IllegalStateException e) {
         return available;
      }
   }

   /** Called from client tick when cache revision changes. */
   public static void syncFromCacheIfNeeded() {
      if (!isActive()) {
         return;
      }
      SettlementMapClientCache cache = SettlementMapClientCache.get();
      int rev = cache.appliedRevision();
      if (rev == lastSyncedRevision) {
         return;
      }
      try {
         XaeroSettlementWaypointSync.sync(cache);
         lastSyncedRevision = rev;
      } catch (NoClassDefFoundError | NoSuchMethodError | Exception e) {
         disabled = true;
         LOGGER.warn("[Millenaire] Disabling Xaero settlement sync after API failure: {}", e.toString());
      }
   }

   public static void clearMarkers() {
      lastSyncedRevision = Integer.MIN_VALUE;
      if (!available || disabled) {
         return;
      }
      try {
         XaeroSettlementWaypointSync.clearAll();
      } catch (NoClassDefFoundError | NoSuchMethodError | Exception e) {
         disabled = true;
         LOGGER.warn("[Millenaire] Failed to clear Xaero settlement waypoints: {}", e.toString());
      }
   }

   public static void onClientLogout() {
      SettlementMapClientCache.get().clear();
      clearMarkers();
   }
}
