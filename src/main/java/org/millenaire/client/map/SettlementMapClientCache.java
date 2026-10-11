package org.millenaire.client.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.millenaire.map.sync.SettlementMapEntry;

/**
 * Client-side cache of settlement map DTOs. Cleared on logout / world token change.
 */
public final class SettlementMapClientCache {
   private static final SettlementMapClientCache INSTANCE = new SettlementMapClientCache();

   private final Map<UUID, SettlementMapEntry> byId = new HashMap<>();
   private final Map<Integer, Map<UUID, SettlementMapEntry>> snapshotParts = new HashMap<>();
   private String worldToken = "";
   private int revision;
   private int appliedRevision = -1;

   private SettlementMapClientCache() {
   }

   public static SettlementMapClientCache get() {
      return INSTANCE;
   }

   public synchronized void clear() {
      byId.clear();
      snapshotParts.clear();
      worldToken = "";
      revision = 0;
      appliedRevision = -1;
   }

   public synchronized boolean applySnapshotPart(String token, int rev, int partIndex, int partCount, Collection<SettlementMapEntry> entries) {
      if (token == null) {
         token = "";
      }
      if (!token.equals(worldToken)) {
         byId.clear();
         snapshotParts.clear();
         worldToken = token;
      }
      revision = rev;
      Map<UUID, SettlementMapEntry> part = new HashMap<>();
      for (SettlementMapEntry e : entries) {
         part.put(e.villageId(), e);
      }
      snapshotParts.put(partIndex, part);
      if (snapshotParts.size() < partCount) {
         return false;
      }
      byId.clear();
      for (Map<UUID, SettlementMapEntry> p : snapshotParts.values()) {
         byId.putAll(p);
      }
      snapshotParts.clear();
      appliedRevision = rev;
      return true;
   }

   public synchronized boolean applyDelta(String token, int rev, Collection<SettlementMapEntry> upserts, Collection<UUID> removed) {
      if (token == null) {
         token = "";
      }
      if (!token.equals(worldToken)) {
         // Stale or first delta without snapshot — ignore; wait for snapshot.
         return false;
      }
      for (UUID id : removed) {
         byId.remove(id);
      }
      for (SettlementMapEntry e : upserts) {
         byId.put(e.villageId(), e);
      }
      revision = rev;
      appliedRevision = rev;
      return true;
   }

   public synchronized Collection<SettlementMapEntry> entries() {
      return Set.copyOf(byId.values());
   }

   public synchronized Set<UUID> ids() {
      return new HashSet<>(byId.keySet());
   }

   public synchronized int appliedRevision() {
      return appliedRevision;
   }

   public synchronized String worldToken() {
      return worldToken;
   }
}
