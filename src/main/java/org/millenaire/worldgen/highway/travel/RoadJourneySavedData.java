package org.millenaire.worldgen.highway.travel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class RoadJourneySavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_highway_journeys";
   private final Map<UUID, RoadJourney> journeys = new HashMap<>();
   private final Map<UUID, Long> routeCooldowns = new HashMap<>();

   public static Factory<RoadJourneySavedData> factory() {
      return new Factory<>(RoadJourneySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static RoadJourneySavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Collection<RoadJourney> all() {
      return List.copyOf(this.journeys.values());
   }

   public int size() {
      return this.journeys.size();
   }

   @Nullable
   public RoadJourney get(UUID id) {
      return this.journeys.get(id);
   }

   public void put(RoadJourney journey) {
      if (journey != null) {
         this.journeys.put(journey.journeyId(), journey);
         this.setDirty();
      }
   }

   public void remove(UUID id) {
      if (this.journeys.remove(id) != null) {
         this.setDirty();
      }
   }

   public boolean onCooldown(UUID routeId, long gameTime, long cooldownTicks) {
      Long until = this.routeCooldowns.get(routeId);
      return until != null && gameTime < until;
   }

   public void setCooldown(UUID routeId, long untilGameTime) {
      this.routeCooldowns.put(routeId, untilGameTime);
      this.setDirty();
   }

   public void purgeTerminal() {
      List<UUID> remove = new ArrayList<>();
      for (RoadJourney j : this.journeys.values()) {
         if (j.phase() == RoadJourney.Phase.ARRIVED || j.phase() == RoadJourney.Phase.CANCELLED) {
            remove.add(j.journeyId());
         }
      }
      for (UUID id : remove) {
         this.journeys.remove(id);
      }
      if (!remove.isEmpty()) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();
      for (RoadJourney j : this.journeys.values()) {
         list.add(j.save());
      }
      tag.put("journeys", list);
      ListTag cds = new ListTag();
      for (Map.Entry<UUID, Long> e : this.routeCooldowns.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putUUID("route", e.getKey());
         c.putLong("until", e.getValue());
         cds.add(c);
      }
      tag.put("cooldowns", cds);
      return tag;
   }

   private static RoadJourneySavedData load(CompoundTag tag) {
      RoadJourneySavedData data = new RoadJourneySavedData();
      ListTag list = tag.getList("journeys", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         try {
            RoadJourney j = RoadJourney.load(list.getCompound(i));
            data.journeys.put(j.journeyId(), j);
         } catch (Exception ignored) {
         }
      }
      ListTag cds = tag.getList("cooldowns", Tag.TAG_COMPOUND);
      for (int i = 0; i < cds.size(); i++) {
         CompoundTag c = cds.getCompound(i);
         if (c.hasUUID("route")) {
            data.routeCooldowns.put(c.getUUID("route"), c.getLong("until"));
         }
      }
      return data;
   }
}
