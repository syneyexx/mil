package org.millenaire.faction.crime;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.slf4j.Logger;

/**
 * Authoritative culture bounty ledger and crime application.
 */
public final class CultureBountyManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long THEFT_DEBOUNCE_TICKS = 200L;
   private static final long ASSAULT_DEBOUNCE_TICKS = 40L;
   private static final long PICKPOCKET_DEBOUNCE_TICKS = 100L;
   private static final long DECAY_INTERVAL_TICKS = 24000L;
   private static final int DECAY_AMOUNT = 8;

   private final Map<String, CultureBountyRecord> records = new LinkedHashMap<>();
   private final Runnable markDirty;

   CultureBountyManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   private static String key(UUID playerId, ResourceLocation cultureId) {
      return playerId + "|" + cultureId;
   }

   @Nullable
   public CultureBountyRecord get(UUID playerId, ResourceLocation cultureId) {
      if (playerId == null || cultureId == null) {
         return null;
      }
      return this.records.get(key(playerId, cultureId));
   }

   public CultureBountyRecord getOrCreate(UUID playerId, ResourceLocation cultureId) {
      return this.records.computeIfAbsent(key(playerId, cultureId), k -> new CultureBountyRecord(playerId, cultureId));
   }

   public Collection<CultureBountyRecord> getAllRecords() {
      return Collections.unmodifiableCollection(this.records.values());
   }

   public List<CultureBountyRecord> getRecordsForPlayer(UUID playerId) {
      List<CultureBountyRecord> out = new ArrayList<>();
      for (CultureBountyRecord record : this.records.values()) {
         if (record.playerId().equals(playerId) && record.bountyScore() > 0) {
            out.add(record);
         }
      }
      return out;
   }

   public WantedTier getTier(UUID playerId, ResourceLocation cultureId) {
      CultureBountyRecord record = this.get(playerId, cultureId);
      return record == null ? WantedTier.NONE : record.tier();
   }

   /**
    * Apply a crime. Returns the updated record, or null if ignored.
    */
   @Nullable
   public CultureBountyRecord recordCrime(
      ServerLevel level,
      ServerPlayer player,
      ResourceLocation cultureId,
      CrimeType type,
      @Nullable Village village
   ) {
      if (!isEnabled() || player == null || cultureId == null || type == null || level == null) {
         return null;
      }
      if (!org.millenaire.combat.CombatHelper.isPlayerTargetable(player)) {
         return null;
      }

      CultureBountyRecord record = this.getOrCreate(player.getUUID(), cultureId);
      long now = level.getGameTime();

      if (type == CrimeType.THEFT && now - record.lastTheftGameTime() < THEFT_DEBOUNCE_TICKS) {
         return record;
      }
      if (type == CrimeType.PICKPOCKET && now - record.lastTheftGameTime() < PICKPOCKET_DEBOUNCE_TICKS) {
         return record;
      }
      if (type == CrimeType.ASSAULT && now - record.lastAssaultGameTime() < ASSAULT_DEBOUNCE_TICKS) {
         return record;
      }

      WantedTier before = record.tier();
      int gained = type.bountyPoints();
      record.addBounty(gained);
      record.setLastCrimeGameTime(now);
      if (type == CrimeType.THEFT || type == CrimeType.PICKPOCKET) {
         record.setLastTheftGameTime(now);
      }
      if (type == CrimeType.ASSAULT) {
         record.setLastAssaultGameTime(now);
      }

      // Spill into classic reputation for crimes that do not already adjust in combat.
      // ASSAULT already hits village rep in VillagerCombat.onHurt.
      if (type != CrimeType.ASSAULT) {
         int repHit = -(type.bountyPoints() / 2);
         if (village != null) {
            village.adjustReputation(level, player.getUUID(), repHit);
         } else {
            PlayerCultureReputation.get(level).add(player.getUUID(), cultureId, Math.max(-40, repHit / 4));
         }
      }

      WantedTier after = record.tier();
      if (after.isAtLeast(WantedTier.WANTED) && record.pendingWaves() <= 0) {
         int waves = BountyRaidScheduler.computeWaveCount(level, player, cultureId, record.bountyScore());
         record.setPendingWaves(waves);
         record.setNextWaveGameTime(now + 1200L);
         LOGGER.info(
            "[Millenaire] Bounty campaign started: player={} culture={} score={} waves={}",
            player.getGameProfile().getName(),
            cultureId,
            record.bountyScore(),
            waves
         );
      } else if (after.ordinal() > before.ordinal() && record.pendingWaves() > 0) {
         // Escalation mid-campaign: add one extra wave.
         record.setPendingWaves(record.pendingWaves() + 1);
      }

      if (chatEnabled()) {
         player.sendSystemMessage(
            Component.translatable(
               "millenaire.crime.bounty.gained",
               Component.literal(player.getGameProfile().getName()),
               Component.literal(String.valueOf(gained)),
               Component.literal(cultureDisplayName(cultureId)),
               Component.literal(String.valueOf(record.bountyScore()))
            )
         );
      }

      if (after != before && after != WantedTier.NONE) {
         player.sendSystemMessage(
            Component.translatable(
               "millenaire.crime.wanted.notify",
               Component.translatable(after.translationKey()),
               Component.literal(cultureDisplayName(cultureId))
            )
         );
      }

      this.markDirty.run();
      BountyHudSync.sync(level, player);
      return record;
   }

   public void reduceBounty(ServerLevel level, UUID playerId, ResourceLocation cultureId, int amount) {
      CultureBountyRecord record = this.get(playerId, cultureId);
      if (record == null) {
         return;
      }
      record.reduceBounty(amount);
      this.markDirty.run();
      if (level != null) {
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
         if (player != null) {
            BountyHudSync.sync(level, player);
         }
      }
   }

   public void tickDecay(ServerLevel level) {
      if (!isEnabled() || level == null) {
         return;
      }
      long now = level.getGameTime();
      boolean dirty = false;
      for (CultureBountyRecord record : this.records.values()) {
         if (record.bountyScore() <= 0) {
            continue;
         }
         if (record.lastDecayGameTime() <= 0L) {
            record.setLastDecayGameTime(now);
            dirty = true;
            continue;
         }
         if (now - record.lastDecayGameTime() < DECAY_INTERVAL_TICKS) {
            continue;
         }
         // Idle decay only when no active campaign waves.
         if (record.pendingWaves() > 0) {
            record.setLastDecayGameTime(now);
            dirty = true;
            continue;
         }
         record.reduceBounty(DECAY_AMOUNT);
         record.setLastDecayGameTime(now);
         dirty = true;
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.playerId());
         if (player != null) {
            BountyHudSync.sync(level, player);
         }
      }
      if (dirty) {
         this.markDirty.run();
      }
   }

   private static String cultureDisplayName(ResourceLocation cultureId) {
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null && culture.displayName() != null && !culture.displayName().isBlank()) {
         return culture.displayName();
      }
      return cultureId != null ? cultureId.getPath() : "?";
   }

   private static boolean chatEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyChatEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   void putLoaded(CultureBountyRecord record) {
      if (record != null) {
         this.records.put(key(record.playerId(), record.cultureId()), record);
      }
   }

   public static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
