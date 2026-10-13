package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record WorldLedgerPayload(
   int villageCount,
   int loneBuildingCount,
   int activeRaidCount,
   int activeWarCount,
   String playerCulturePath,
   boolean hasOwnedCity,
   String ownedVillageUuid,
   String ownedVillageName,
   String ownedCulturePath,
   int ownedPopulation,
   int ownedMaxSlots,
   int ownedOpenJobs,
   int ownedOpenBeds,
   String ownedThreatLabel,
   List<WorldLedgerPayload.CultureRow> cultures,
   List<WorldLedgerPayload.WarRow> wars,
   List<WorldLedgerPayload.TradeRow> tradeRanks
) implements CustomPacketPayload {
   private static final int MAX_CULTURES = 48;
   private static final int MAX_WARS = 64;
   private static final int MAX_TRADE = 48;
   public static final Type<WorldLedgerPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "world_ledger"));
   public static final StreamCodec<ByteBuf, WorldLedgerPayload> STREAM_CODEC = StreamCodec.of(
      WorldLedgerPayload::encode, WorldLedgerPayload::decode
   );

   private static void encode(ByteBuf buf, WorldLedgerPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.villageCount);
      ByteBufCodecs.VAR_INT.encode(buf, p.loneBuildingCount);
      ByteBufCodecs.VAR_INT.encode(buf, p.activeRaidCount);
      ByteBufCodecs.VAR_INT.encode(buf, p.activeWarCount);
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(p.playerCulturePath));
      buf.writeBoolean(p.hasOwnedCity);
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(p.ownedVillageUuid));
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(p.ownedVillageName));
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(p.ownedCulturePath));
      ByteBufCodecs.VAR_INT.encode(buf, p.ownedPopulation);
      ByteBufCodecs.VAR_INT.encode(buf, p.ownedMaxSlots);
      ByteBufCodecs.VAR_INT.encode(buf, p.ownedOpenJobs);
      ByteBufCodecs.VAR_INT.encode(buf, p.ownedOpenBeds);
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(p.ownedThreatLabel));

      int cultureCount = Math.min(p.cultures.size(), MAX_CULTURES);
      ByteBufCodecs.VAR_INT.encode(buf, cultureCount);
      for (int i = 0; i < cultureCount; i++) {
         CultureRow c = p.cultures.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, c.culturePath());
         ByteBufCodecs.STRING_UTF8.encode(buf, c.displayNameKey());
         ByteBufCodecs.VAR_INT.encode(buf, c.reputation());
         ByteBufCodecs.STRING_UTF8.encode(buf, c.reputationLabel());
         ByteBufCodecs.VAR_INT.encode(buf, c.languageScore());
         ByteBufCodecs.VAR_INT.encode(buf, c.villageCount());
         buf.writeBoolean(c.controlledByPlayer());
         ByteBufCodecs.VAR_INT.encode(buf, c.bounty());
      }

      int warCount = Math.min(p.wars.size(), MAX_WARS);
      ByteBufCodecs.VAR_INT.encode(buf, warCount);
      for (int i = 0; i < warCount; i++) {
         WarRow w = p.wars.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, w.cultureAPath());
         ByteBufCodecs.STRING_UTF8.encode(buf, w.cultureBPath());
         ByteBufCodecs.STRING_UTF8.encode(buf, w.displayAKey());
         ByteBufCodecs.STRING_UTF8.encode(buf, w.displayBKey());
         ByteBufCodecs.VAR_INT.encode(buf, w.standing());
         buf.writeBoolean(w.atWar());
      }

      int tradeCount = Math.min(p.tradeRanks.size(), MAX_TRADE);
      ByteBufCodecs.VAR_INT.encode(buf, tradeCount);
      for (int i = 0; i < tradeCount; i++) {
         TradeRow t = p.tradeRanks.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, t.culturePath());
         ByteBufCodecs.STRING_UTF8.encode(buf, t.displayNameKey());
         ByteBufCodecs.VAR_INT.encode(buf, t.standingVsPlayer());
         ByteBufCodecs.STRING_UTF8.encode(buf, t.tradeHint());
         ByteBufCodecs.VAR_INT.encode(buf, t.rankScore());
      }
   }

   private static WorldLedgerPayload decode(ByteBuf buf) {
      int villageCount = ByteBufCodecs.VAR_INT.decode(buf);
      int loneBuildingCount = ByteBufCodecs.VAR_INT.decode(buf);
      int activeRaidCount = ByteBufCodecs.VAR_INT.decode(buf);
      int activeWarCount = ByteBufCodecs.VAR_INT.decode(buf);
      String playerCulturePath = emptyToNull(ByteBufCodecs.STRING_UTF8.decode(buf));
      boolean hasOwnedCity = buf.readBoolean();
      String ownedVillageUuid = emptyToNull(ByteBufCodecs.STRING_UTF8.decode(buf));
      String ownedVillageName = emptyToNull(ByteBufCodecs.STRING_UTF8.decode(buf));
      String ownedCulturePath = emptyToNull(ByteBufCodecs.STRING_UTF8.decode(buf));
      int ownedPopulation = ByteBufCodecs.VAR_INT.decode(buf);
      int ownedMaxSlots = ByteBufCodecs.VAR_INT.decode(buf);
      int ownedOpenJobs = ByteBufCodecs.VAR_INT.decode(buf);
      int ownedOpenBeds = ByteBufCodecs.VAR_INT.decode(buf);
      String ownedThreatLabel = emptyToNull(ByteBufCodecs.STRING_UTF8.decode(buf));

      int cultureCount = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_CULTURES);
      List<CultureRow> cultures = new ArrayList<>(cultureCount);
      for (int i = 0; i < cultureCount; i++) {
         cultures.add(
            new CultureRow(
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               buf.readBoolean(),
               ByteBufCodecs.VAR_INT.decode(buf)
            )
         );
      }

      int warCount = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_WARS);
      List<WarRow> wars = new ArrayList<>(warCount);
      for (int i = 0; i < warCount; i++) {
         wars.add(
            new WarRow(
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               buf.readBoolean()
            )
         );
      }

      int tradeCount = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_TRADE);
      List<TradeRow> tradeRanks = new ArrayList<>(tradeCount);
      for (int i = 0; i < tradeCount; i++) {
         tradeRanks.add(
            new TradeRow(
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf)
            )
         );
      }

      return new WorldLedgerPayload(
         villageCount,
         loneBuildingCount,
         activeRaidCount,
         activeWarCount,
         playerCulturePath,
         hasOwnedCity,
         ownedVillageUuid,
         ownedVillageName,
         ownedCulturePath,
         ownedPopulation,
         ownedMaxSlots,
         ownedOpenJobs,
         ownedOpenBeds,
         ownedThreatLabel == null ? "none" : ownedThreatLabel,
         cultures,
         wars,
         tradeRanks
      );
   }

   private static String nullToEmpty(String s) {
      return s == null ? "" : s;
   }

   private static String emptyToNull(String s) {
      return s == null || s.isEmpty() ? null : s;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record CultureRow(
      String culturePath,
      String displayNameKey,
      int reputation,
      String reputationLabel,
      int languageScore,
      int villageCount,
      boolean controlledByPlayer,
      int bounty
   ) {
   }

   public record WarRow(String cultureAPath, String cultureBPath, String displayAKey, String displayBKey, int standing, boolean atWar) {
   }

   public record TradeRow(String culturePath, String displayNameKey, int standingVsPlayer, String tradeHint, int rankScore) {
   }
}
