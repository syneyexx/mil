package org.millenaire.crime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.config.MillenaireServerConfig;

public class PlayerBountySavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_player_bounty";
   private final Map<UUID, Map<ResourceLocation, Integer>> bounties = new HashMap<>();
   private final Map<UUID, Map<ResourceLocation, Long>> lastCrimeRaidTick = new HashMap<>();

   private PlayerBountySavedData() {
   }

   public static Factory<PlayerBountySavedData> factory() {
      return new Factory<>(PlayerBountySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static PlayerBountySavedData get(ServerLevel level) {
      return (PlayerBountySavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public int get(UUID player, ResourceLocation cultureId) {
      Map<ResourceLocation, Integer> map = this.bounties.get(player);
      return map == null ? 0 : map.getOrDefault(cultureId, 0);
   }

   public int add(UUID player, ResourceLocation cultureId, int amount) {
      if (amount == 0) {
         return this.get(player, cultureId);
      }

      Map<ResourceLocation, Integer> map = this.bounties.computeIfAbsent(player, k -> new HashMap<>());
      int next = Math.max(0, Math.min(CrimeConstants.BOUNTY_MAX, map.getOrDefault(cultureId, 0) + amount));
      map.put(cultureId, next);
      this.setDirty();
      return next;
   }

   public void set(UUID player, ResourceLocation cultureId, int value) {
      Map<ResourceLocation, Integer> map = this.bounties.computeIfAbsent(player, k -> new HashMap<>());
      map.put(cultureId, Math.max(0, Math.min(CrimeConstants.BOUNTY_MAX, value)));
      this.setDirty();
   }

   public Map<ResourceLocation, Integer> getAll(UUID player) {
      Map<ResourceLocation, Integer> map = this.bounties.get(player);
      return map == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(map));
   }

   public boolean isTradeBanned(UUID player, ResourceLocation cultureId) {
      return this.get(player, cultureId) >= tradeBanThreshold();
   }

   public boolean isHostile(UUID player, ResourceLocation cultureId) {
      return this.get(player, cultureId) >= hostileThreshold();
   }

   public boolean shouldRaidCity(UUID player, ResourceLocation cultureId) {
      return this.get(player, cultureId) >= raidCityThreshold();
   }

   public boolean tryMarkCrimeRaid(UUID player, ResourceLocation cultureId, long gameTime) {
      Map<ResourceLocation, Long> map = this.lastCrimeRaidTick.computeIfAbsent(player, k -> new HashMap<>());
      long last = map.getOrDefault(cultureId, Long.MIN_VALUE);
      if (gameTime - last < CrimeConstants.CRIME_RAID_COOLDOWN_TICKS) {
         return false;
      }

      map.put(cultureId, gameTime);
      this.setDirty();
      return true;
   }

   public static int tradeBanThreshold() {
      try {
         return MillenaireServerConfig.SERVER.crimeTradeBanThreshold.getAsInt();
      } catch (IllegalStateException e) {
         return CrimeConstants.TRADE_BAN_THRESHOLD;
      }
   }

   public static int hostileThreshold() {
      try {
         return MillenaireServerConfig.SERVER.crimeHostileThreshold.getAsInt();
      } catch (IllegalStateException e) {
         return CrimeConstants.HOSTILE_THRESHOLD;
      }
   }

   public static int raidCityThreshold() {
      try {
         return MillenaireServerConfig.SERVER.crimeRaidCityThreshold.getAsInt();
      } catch (IllegalStateException e) {
         return CrimeConstants.RAID_CITY_THRESHOLD;
      }
   }

   public static boolean crimeEnabled() {
      try {
         return MillenaireServerConfig.SERVER.enableCrime.getAsBoolean();
      } catch (IllegalStateException e) {
         return true;
      }
   }

   @Override
   public CompoundTag save(CompoundTag root, Provider registries) {
      ListTag players = new ListTag();

      for (Entry<UUID, Map<ResourceLocation, Integer>> playerEntry : this.bounties.entrySet()) {
         CompoundTag playerTag = new CompoundTag();
         playerTag.putUUID("player", playerEntry.getKey());
         ListTag cultures = new ListTag();

         for (Entry<ResourceLocation, Integer> cultureEntry : playerEntry.getValue().entrySet()) {
            CompoundTag cultureTag = new CompoundTag();
            cultureTag.putString("culture", cultureEntry.getKey().toString());
            cultureTag.putInt("bounty", cultureEntry.getValue());
            cultures.add(cultureTag);
         }

         playerTag.put("cultures", cultures);
         Map<ResourceLocation, Long> raids = this.lastCrimeRaidTick.get(playerEntry.getKey());
         if (raids != null && !raids.isEmpty()) {
            ListTag raidList = new ListTag();

            for (Entry<ResourceLocation, Long> raidEntry : raids.entrySet()) {
               CompoundTag raidTag = new CompoundTag();
               raidTag.putString("culture", raidEntry.getKey().toString());
               raidTag.putLong("tick", raidEntry.getValue());
               raidList.add(raidTag);
            }

            playerTag.put("crime_raids", raidList);
         }

         players.add(playerTag);
      }

      root.put("players", players);
      return root;
   }

   static PlayerBountySavedData load(CompoundTag root) {
      PlayerBountySavedData result = new PlayerBountySavedData();
      ListTag players = root.getList("players", 10);

      for (int i = 0; i < players.size(); i++) {
         CompoundTag playerTag = players.getCompound(i);
         if (!playerTag.hasUUID("player")) {
            continue;
         }

         UUID playerId = playerTag.getUUID("player");
         Map<ResourceLocation, Integer> cultureMap = new HashMap<>();
         ListTag cultures = playerTag.getList("cultures", 10);

         for (int j = 0; j < cultures.size(); j++) {
            CompoundTag cultureTag = cultures.getCompound(j);
            cultureMap.put(ResourceLocation.parse(cultureTag.getString("culture")), cultureTag.getInt("bounty"));
         }

         result.bounties.put(playerId, cultureMap);
         if (playerTag.contains("crime_raids")) {
            Map<ResourceLocation, Long> raids = new HashMap<>();
            ListTag raidList = playerTag.getList("crime_raids", 10);

            for (int j = 0; j < raidList.size(); j++) {
               CompoundTag raidTag = raidList.getCompound(j);
               raids.put(ResourceLocation.parse(raidTag.getString("culture")), raidTag.getLong("tick"));
            }

            result.lastCrimeRaidTick.put(playerId, raids);
         }
      }

      return result;
   }
}
