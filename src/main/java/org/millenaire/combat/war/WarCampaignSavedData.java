package org.millenaire.combat.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

/** Persisted war campaigns. SavedData id: millenaire_war_campaigns */
public class WarCampaignSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int SCHEMA_VERSION = 1;
   public static final String DATA_NAME = "millenaire_war_campaigns";
   private static final int MAX_CAMPAIGNS = 64;
   private static final int MAX_ACTIVE_WAVES = 64;

   private final Map<UUID, WarCampaign> campaigns = new HashMap<>();
   private final Map<UUID, WarWaveInstance> activeWaves = new HashMap<>();
   private long worldSessionId = UUID.randomUUID().getMostSignificantBits();

   public WarCampaignSavedData() {
   }

   public static Factory<WarCampaignSavedData> factory() {
      return new Factory<>(WarCampaignSavedData::new, (tag, regs) -> load(tag), null);
   }

   public static WarCampaignSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public long worldSessionId() {
      return this.worldSessionId;
   }

   public void bumpSession() {
      this.worldSessionId = UUID.randomUUID().getMostSignificantBits();
      this.setDirty();
   }

   public Collection<WarCampaign> allCampaigns() {
      return this.campaigns.values();
   }

   public List<WarCampaign> openCampaigns() {
      List<WarCampaign> out = new ArrayList<>();
      for (WarCampaign c : this.campaigns.values()) {
         if (c.isOpen()) {
            out.add(c);
         }
      }
      return out;
   }

   @Nullable
   public WarCampaign getCampaign(UUID id) {
      return id == null ? null : this.campaigns.get(id);
   }

   public boolean putCampaign(WarCampaign campaign) {
      if (campaign == null) {
         return false;
      }
      WarCampaign existing = findOpenBetween(campaign.sideA(), campaign.sideB());
      if (existing != null && !existing.id().equals(campaign.id())) {
         return false;
      }
      if (this.campaigns.size() >= MAX_CAMPAIGNS && !this.campaigns.containsKey(campaign.id())) {
         LOGGER.warn("[War] Campaign cap reached ({})", MAX_CAMPAIGNS);
         return false;
      }
      this.campaigns.put(campaign.id(), campaign);
      this.setDirty();
      return true;
   }

   @Nullable
   public WarCampaign findOpenBetween(ResourceLocation a, ResourceLocation b) {
      if (a == null || b == null) {
         return null;
      }
      for (WarCampaign c : this.campaigns.values()) {
         if (!c.isOpen()) {
            continue;
         }
         if ((c.sideA().equals(a) && c.sideB().equals(b)) || (c.sideA().equals(b) && c.sideB().equals(a))) {
            return c;
         }
      }
      return null;
   }

   @Nullable
   public WarCampaign findOpenForFront(UUID frontVillageId) {
      if (frontVillageId == null) {
         return null;
      }
      for (WarCampaign c : this.campaigns.values()) {
         if (c.isOpen() && frontVillageId.equals(c.frontVillageId())) {
            return c;
         }
      }
      return null;
   }

   @Nullable
   public WarCampaign findOpenInvolving(ResourceLocation culture) {
      if (culture == null) {
         return null;
      }
      for (WarCampaign c : this.campaigns.values()) {
         if (c.isOpen() && c.involvesCulture(culture)) {
            return c;
         }
      }
      return null;
   }

   public void putWave(WarWaveInstance wave) {
      if (this.activeWaves.size() >= MAX_ACTIVE_WAVES && !this.activeWaves.containsKey(wave.waveId())) {
         LOGGER.warn("[War] Active wave cap reached ({})", MAX_ACTIVE_WAVES);
         return;
      }
      this.activeWaves.put(wave.waveId(), wave);
      this.setDirty();
   }

   @Nullable
   public WarWaveInstance getWave(UUID waveId) {
      return waveId == null ? null : this.activeWaves.get(waveId);
   }

   public Collection<WarWaveInstance> allWaves() {
      return this.activeWaves.values();
   }

   public void removeWave(UUID waveId) {
      if (waveId != null && this.activeWaves.remove(waveId) != null) {
         this.setDirty();
      }
   }

   public void clearWavesForCampaign(UUID campaignId) {
      if (campaignId == null) {
         return;
      }
      this.activeWaves.entrySet().removeIf(e -> campaignId.equals(e.getValue().campaignId()));
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      tag.putLong("world_session", this.worldSessionId);
      ListTag campaignsTag = new ListTag();
      for (WarCampaign c : this.campaigns.values()) {
         campaignsTag.add(c.save());
      }
      tag.put("campaigns", campaignsTag);
      ListTag wavesTag = new ListTag();
      for (WarWaveInstance w : this.activeWaves.values()) {
         wavesTag.add(w.save());
      }
      tag.put("waves", wavesTag);
      return tag;
   }

   private static WarCampaignSavedData load(CompoundTag tag) {
      WarCampaignSavedData data = new WarCampaignSavedData();
      if (tag.contains("world_session")) {
         data.worldSessionId = tag.getLong("world_session");
      }
      ListTag campaignsTag = tag.getList("campaigns", 10);
      for (int i = 0; i < campaignsTag.size(); i++) {
         try {
            WarCampaign c = WarCampaign.load(campaignsTag.getCompound(i));
            data.campaigns.put(c.id(), c);
         } catch (Exception e) {
            LOGGER.warn("[War] Skipping corrupt campaign: {}", e.toString());
         }
      }
      ListTag wavesTag = tag.getList("waves", 10);
      for (int i = 0; i < wavesTag.size(); i++) {
         try {
            WarWaveInstance w = WarWaveInstance.tryLoad(wavesTag.getCompound(i));
            if (w != null) {
               data.activeWaves.put(w.waveId(), w);
            }
         } catch (Exception e) {
            LOGGER.warn("[War] Skipping corrupt wave: {}", e.toString());
         }
      }
      return data;
   }
}
