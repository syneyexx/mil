package org.millenaire.client;

import java.util.List;
import org.millenaire.network.BountyHudPayload;

/** Client-side cache of bounty HUD lines. */
public final class BountyHudClientState {
   private static List<BountyHudPayload.Entry> entries = List.of();

   private BountyHudClientState() {
   }

   public static void apply(BountyHudPayload payload) {
      entries = payload == null || payload.entries() == null ? List.of() : List.copyOf(payload.entries());
   }

   public static List<BountyHudPayload.Entry> entries() {
      return entries;
   }

   public static void clear() {
      entries = List.of();
   }
}
