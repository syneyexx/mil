package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.civilization.presentation.CivilizationActionId;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;
import org.millenaire.civilization.presentation.WorldStatusSnapshot;

/** Server → client: full world status command-center content. */
public record WorldStatusContentPayload(WorldStatusSnapshot snapshot) implements CustomPacketPayload {
   public static final Type<WorldStatusContentPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "world_status_content"));
   public static final StreamCodec<ByteBuf, WorldStatusContentPayload> STREAM_CODEC =
      StreamCodec.of(WorldStatusContentPayload::encode, WorldStatusContentPayload::decode);

   public static WorldStatusContentPayload fromSnapshot(WorldStatusSnapshot snapshot) {
      return new WorldStatusContentPayload(snapshot);
   }

   private static void encode(ByteBuf buf, WorldStatusContentPayload payload) {
      WorldStatusSnapshot s = payload.snapshot;
      ByteBufCodecs.VAR_INT.encode(buf, s.tabIndex());
      writeString(buf, s.selectedFactionId());
      writeOverview(buf, s.overview());
      writeCultureList(buf, s.cultures());
      writeFactionList(buf, s.factions());
      writeVillageList(buf, s.villages());
      writeRankList(buf, s.tradeRanks());
      writeRankList(buf, s.militaryRanks());
      writeRankList(buf, s.populationRanks());
      writeWarList(buf, s.wars());
      writeThreatList(buf, s.threats());
      writeStandingList(buf, s.standings());
      writePlayerSettlementList(buf, s.playerSettlements());
      writeDiplomacyActionList(buf, s.diplomacyActions());
      writeActivityList(buf, s.activity());
      writeTheaterList(buf, s.theater());
      writeCorridorList(buf, s.corridors());
      writeString(buf, s.actorVillageId());
      buf.writeLong(s.capturedAtGameTime());
   }

   private static WorldStatusContentPayload decode(ByteBuf buf) {
      int tabIndex = ByteBufCodecs.VAR_INT.decode(buf);
      String selectedFactionId = readString(buf);
      WorldStatusSnapshot.Overview overview = readOverview(buf);
      List<WorldStatusSnapshot.CultureLine> cultures = readCultureList(buf);
      List<WorldStatusSnapshot.FactionLine> factions = readFactionList(buf);
      List<WorldStatusSnapshot.VillageLine> villages = readVillageList(buf);
      List<WorldStatusSnapshot.RankLine> tradeRanks = readRankList(buf);
      List<WorldStatusSnapshot.RankLine> militaryRanks = readRankList(buf);
      List<WorldStatusSnapshot.RankLine> populationRanks = readRankList(buf);
      List<WorldStatusSnapshot.WarLine> wars = readWarList(buf);
      List<WorldStatusSnapshot.ThreatLine> threats = readThreatList(buf);
      List<WorldStatusSnapshot.StandingLine> standings = readStandingList(buf);
      List<WorldStatusSnapshot.PlayerSettlementLine> playerSettlements = readPlayerSettlementList(buf);
      List<WorldStatusSnapshot.DiplomacyActionLine> diplomacyActions = readDiplomacyActionList(buf);
      List<WorldStatusSnapshot.ActivityLine> activity = readActivityList(buf);
      List<WorldStatusSnapshot.TheaterLine> theater = readTheaterList(buf);
      List<WorldStatusSnapshot.CorridorLine> corridors = readCorridorList(buf);
      String actorVillageId = readString(buf);
      long capturedAt = buf.readLong();
      return new WorldStatusContentPayload(new WorldStatusSnapshot(
         tabIndex,
         selectedFactionId,
         overview,
         cultures,
         factions,
         villages,
         tradeRanks,
         militaryRanks,
         populationRanks,
         wars,
         threats,
         standings,
         playerSettlements,
         diplomacyActions,
         activity,
         theater,
         corridors,
         actorVillageId,
         capturedAt
      ));
   }

   private static void writeOverview(ByteBuf buf, WorldStatusSnapshot.Overview o) {
      writeString(buf, o.topTradeFactionId());
      writeString(buf, o.topTradeFactionName());
      buf.writeLong(o.topTradeScore());
      writeString(buf, o.topMilitaryFactionId());
      writeString(buf, o.topMilitaryFactionName());
      ByteBufCodecs.VAR_INT.encode(buf, o.topMilitaryPower());
      writeString(buf, o.topPopulationFactionId());
      writeString(buf, o.topPopulationFactionName());
      ByteBufCodecs.VAR_INT.encode(buf, o.topPopulation());
      ByteBufCodecs.VAR_INT.encode(buf, o.activeWarCount());
      ByteBufCodecs.VAR_INT.encode(buf, o.threatCount());
      writeString(buf, o.playerFactionId());
      writeString(buf, o.playerFactionName());
      buf.writeBoolean(o.playerAtWar());
      buf.writeBoolean(o.hasPlayerSettlement());
   }

   private static WorldStatusSnapshot.Overview readOverview(ByteBuf buf) {
      return new WorldStatusSnapshot.Overview(
         readString(buf),
         readString(buf),
         buf.readLong(),
         readString(buf),
         readString(buf),
         ByteBufCodecs.VAR_INT.decode(buf),
         readString(buf),
         readString(buf),
         ByteBufCodecs.VAR_INT.decode(buf),
         ByteBufCodecs.VAR_INT.decode(buf),
         ByteBufCodecs.VAR_INT.decode(buf),
         readString(buf),
         readString(buf),
         buf.readBoolean(),
         buf.readBoolean()
      );
   }

   private static void writeCultureList(ByteBuf buf, List<WorldStatusSnapshot.CultureLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.CultureLine c : list) {
         writeString(buf, c.cultureId());
         writeString(buf, c.cultureNameKey());
         ByteBufCodecs.VAR_INT.encode(buf, c.factionCount());
         ByteBufCodecs.VAR_INT.encode(buf, c.settlementCount());
         ByteBufCodecs.VAR_INT.encode(buf, c.residents());
         buf.writeBoolean(c.anyAtWar());
         writeString(buf, c.temperamentPrimary());
         writeString(buf, c.temperamentSecondary());
      }
   }

   private static List<WorldStatusSnapshot.CultureLine> readCultureList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.CultureLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.CultureLine(
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readBoolean(),
            readString(buf),
            readString(buf)
         ));
      }
      return list;
   }

   private static void writeFactionList(ByteBuf buf, List<WorldStatusSnapshot.FactionLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.FactionLine f : list) {
         writeString(buf, f.factionId());
         writeString(buf, f.displayName());
         writeString(buf, f.cultureId());
         writeString(buf, f.cultureNameKey());
         writeString(buf, f.typeCode());
         writeString(buf, f.leaderName() == null ? "" : f.leaderName());
         ByteBufCodecs.VAR_INT.encode(buf, f.settlementCount());
         ByteBufCodecs.VAR_INT.encode(buf, f.residents());
         ByteBufCodecs.VAR_INT.encode(buf, f.housingCapacity());
         buf.writeBoolean(f.housingPressure());
         writeString(buf, f.economyLabelKey());
         buf.writeLong(f.tradeScore());
         ByteBufCodecs.VAR_INT.encode(buf, f.militaryPower());
         writeString(buf, f.militaryAlertCode());
         buf.writeBoolean(f.atWar());
         ByteBufCodecs.VAR_INT.encode(buf, f.activeTreaties());
         ByteBufCodecs.VAR_INT.encode(buf, f.activeColonizations());
         ByteBufCodecs.VAR_INT.encode(buf, f.activeMigrations());
         ByteBufCodecs.VAR_INT.encode(buf, f.warOpponentNames().size());
         for (String name : f.warOpponentNames()) {
            writeString(buf, name);
         }
         buf.writeBoolean(f.viewerOwned());
         buf.writeBoolean(f.viewerSovereign());
      }
   }

   private static List<WorldStatusSnapshot.FactionLine> readFactionList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 256);
      List<WorldStatusSnapshot.FactionLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         String factionId = readString(buf);
         String displayName = readString(buf);
         String cultureId = readString(buf);
         String cultureNameKey = readString(buf);
         String typeCode = readString(buf);
         String leaderName = readString(buf);
         int settlementCount = ByteBufCodecs.VAR_INT.decode(buf);
         int residents = ByteBufCodecs.VAR_INT.decode(buf);
         int housing = ByteBufCodecs.VAR_INT.decode(buf);
         boolean housingPressure = buf.readBoolean();
         String economy = readString(buf);
         long tradeScore = buf.readLong();
         int militaryPower = ByteBufCodecs.VAR_INT.decode(buf);
         String alert = readString(buf);
         boolean atWar = buf.readBoolean();
         int treaties = ByteBufCodecs.VAR_INT.decode(buf);
         int colonizations = ByteBufCodecs.VAR_INT.decode(buf);
         int migrations = ByteBufCodecs.VAR_INT.decode(buf);
         int oppCount = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 16);
         List<String> opponents = new ArrayList<>(oppCount);
         for (int j = 0; j < oppCount; j++) {
            opponents.add(readString(buf));
         }
         boolean viewerOwned = buf.readBoolean();
         boolean viewerSovereign = buf.readBoolean();
         list.add(new WorldStatusSnapshot.FactionLine(
            factionId,
            displayName,
            cultureId,
            cultureNameKey,
            typeCode,
            leaderName.isEmpty() ? null : leaderName,
            settlementCount,
            residents,
            housing,
            housingPressure,
            economy,
            tradeScore,
            militaryPower,
            alert,
            atWar,
            treaties,
            colonizations,
            migrations,
            List.copyOf(opponents),
            viewerOwned,
            viewerSovereign
         ));
      }
      return list;
   }

   private static void writeVillageList(ByteBuf buf, List<WorldStatusSnapshot.VillageLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.VillageLine v : list) {
         writeString(buf, v.villageId());
         writeString(buf, v.villageName());
         writeString(buf, v.factionId());
         writeString(buf, v.factionName());
         writeString(buf, v.cultureId());
         writeString(buf, v.cultureNameKey());
         ByteBufCodecs.VAR_INT.encode(buf, v.x());
         ByteBufCodecs.VAR_INT.encode(buf, v.y());
         ByteBufCodecs.VAR_INT.encode(buf, v.z());
         ByteBufCodecs.VAR_INT.encode(buf, v.residents());
         buf.writeBoolean(v.controlledByViewer());
         buf.writeBoolean(v.viewerSovereign());
      }
   }

   private static List<WorldStatusSnapshot.VillageLine> readVillageList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 512);
      List<WorldStatusSnapshot.VillageLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.VillageLine(
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readBoolean(),
            buf.readBoolean()
         ));
      }
      return list;
   }

   private static void writeRankList(ByteBuf buf, List<WorldStatusSnapshot.RankLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.RankLine r : list) {
         ByteBufCodecs.VAR_INT.encode(buf, r.rank());
         writeString(buf, r.factionId());
         writeString(buf, r.displayName());
         buf.writeLong(r.score());
         buf.writeBoolean(r.viewerOwned());
      }
   }

   private static List<WorldStatusSnapshot.RankLine> readRankList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.RankLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.RankLine(
            ByteBufCodecs.VAR_INT.decode(buf),
            readString(buf),
            readString(buf),
            buf.readLong(),
            buf.readBoolean()
         ));
      }
      return list;
   }

   private static void writeWarList(ByteBuf buf, List<WorldStatusSnapshot.WarLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.WarLine w : list) {
         writeString(buf, w.warId());
         writeString(buf, w.attackerFactionId());
         writeString(buf, w.attackerName());
         writeString(buf, w.defenderFactionId());
         writeString(buf, w.defenderName());
         writeString(buf, w.stateCode());
         ByteBufCodecs.VAR_INT.encode(buf, w.warScore());
         writeString(buf, w.primaryGoalCode());
         buf.writeBoolean(w.involvesViewer());
      }
   }

   private static List<WorldStatusSnapshot.WarLine> readWarList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.WarLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.WarLine(
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            readString(buf),
            buf.readBoolean()
         ));
      }
      return list;
   }

   private static void writeThreatList(ByteBuf buf, List<WorldStatusSnapshot.ThreatLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.ThreatLine t : list) {
         writeString(buf, t.kindCode());
         writeString(buf, t.sourceFactionId());
         writeString(buf, t.sourceName());
         writeString(buf, t.targetFactionId());
         writeString(buf, t.targetName());
         writeString(buf, t.detail());
         ByteBufCodecs.VAR_INT.encode(buf, t.severity());
      }
   }

   private static List<WorldStatusSnapshot.ThreatLine> readThreatList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.ThreatLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.ThreatLine(
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf)
         ));
      }
      return list;
   }

   private static void writeStandingList(ByteBuf buf, List<WorldStatusSnapshot.StandingLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.StandingLine s : list) {
         writeString(buf, s.factionId());
         writeString(buf, s.displayName());
         writeString(buf, s.categoryCode());
         ByteBufCodecs.VAR_INT.encode(buf, s.score());
         buf.writeBoolean(s.atWar());
         buf.writeBoolean(s.hasEmbargo());
      }
   }

   private static List<WorldStatusSnapshot.StandingLine> readStandingList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 128);
      List<WorldStatusSnapshot.StandingLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.StandingLine(
            readString(buf),
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readBoolean(),
            buf.readBoolean()
         ));
      }
      return list;
   }

   private static void writePlayerSettlementList(ByteBuf buf, List<WorldStatusSnapshot.PlayerSettlementLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.PlayerSettlementLine p : list) {
         writeString(buf, p.villageId());
         writeString(buf, p.villageName());
         writeString(buf, p.factionId());
         writeString(buf, p.developmentLabelKey());
         ByteBufCodecs.VAR_INT.encode(buf, p.residents());
         ByteBufCodecs.VAR_INT.encode(buf, p.housingCapacity());
         buf.writeBoolean(p.overcrowded());
         writeString(buf, p.inventoryCertaintyKey() == null ? "" : p.inventoryCertaintyKey());
         writeString(buf, p.topNeedSummary());
         buf.writeBoolean(p.topNeedProven());
         writeString(buf, p.projectName() == null ? "" : p.projectName());
         writeString(buf, p.colonizationStatusKey() == null ? "" : p.colonizationStatusKey());
         ByteBufCodecs.VAR_INT.encode(buf, p.actions().size());
         for (CivilizationVillageSnapshot.AllowedAction action : p.actions()) {
            writeString(buf, action.actionId().name());
            buf.writeBoolean(action.enabled());
            writeString(buf, action.labelKey());
            writeString(buf, action.blockedReasonKey() == null ? "" : action.blockedReasonKey());
            writeString(buf, action.targetId() == null ? "" : action.targetId());
         }
      }
   }

   private static List<WorldStatusSnapshot.PlayerSettlementLine> readPlayerSettlementList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 32);
      List<WorldStatusSnapshot.PlayerSettlementLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         String villageId = readString(buf);
         String villageName = readString(buf);
         String factionId = readString(buf);
         String development = readString(buf);
         int residents = ByteBufCodecs.VAR_INT.decode(buf);
         int housing = ByteBufCodecs.VAR_INT.decode(buf);
         boolean overcrowded = buf.readBoolean();
         String inventoryCertainty = readString(buf);
         String topNeed = readString(buf);
         boolean topNeedProven = buf.readBoolean();
         String project = readString(buf);
         String colonization = readString(buf);
         int actionCount = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 32);
         List<CivilizationVillageSnapshot.AllowedAction> actions = new ArrayList<>(actionCount);
         for (int j = 0; j < actionCount; j++) {
            String actionName = readString(buf);
            boolean enabled = buf.readBoolean();
            String label = readString(buf);
            String blocked = readString(buf);
            String target = readString(buf);
            CivilizationActionId actionId;
            try {
               actionId = CivilizationActionId.valueOf(actionName);
            } catch (IllegalArgumentException e) {
               continue;
            }
            actions.add(new CivilizationVillageSnapshot.AllowedAction(
               actionId, enabled, label, blocked, target.isEmpty() ? null : target
            ));
         }
         list.add(new WorldStatusSnapshot.PlayerSettlementLine(
            villageId,
            villageName,
            factionId,
            development,
            residents,
            housing,
            overcrowded,
            inventoryCertainty,
            topNeed,
            topNeedProven,
            project.isEmpty() ? null : project,
            colonization.isEmpty() ? null : colonization,
            List.copyOf(actions)
         ));
      }
      return list;
   }

   private static void writeDiplomacyActionList(ByteBuf buf, List<WorldStatusSnapshot.DiplomacyActionLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.DiplomacyActionLine d : list) {
         writeString(buf, d.actionId());
         writeString(buf, d.labelKey());
         writeString(buf, d.targetFactionId());
         writeString(buf, d.targetFactionName());
         buf.writeBoolean(d.enabled());
         writeString(buf, d.blockedReasonKey() == null ? "" : d.blockedReasonKey());
      }
   }

   private static List<WorldStatusSnapshot.DiplomacyActionLine> readDiplomacyActionList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.DiplomacyActionLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.DiplomacyActionLine(
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            buf.readBoolean(),
            readString(buf)
         ));
      }
      return list;
   }

   private static void writeActivityList(ByteBuf buf, List<WorldStatusSnapshot.ActivityLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list.size());
      for (WorldStatusSnapshot.ActivityLine a : list) {
         writeString(buf, a.typeKey());
         writeString(buf, a.summary());
         buf.writeLong(a.gameTime());
      }
   }

   private static List<WorldStatusSnapshot.ActivityLine> readActivityList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 128);
      List<WorldStatusSnapshot.ActivityLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.ActivityLine(readString(buf), readString(buf), buf.readLong()));
      }
      return list;
   }

   private static void writeTheaterList(ByteBuf buf, List<WorldStatusSnapshot.TheaterLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list == null ? 0 : list.size());
      if (list == null) {
         return;
      }
      for (WorldStatusSnapshot.TheaterLine t : list) {
         writeString(buf, t.type());
         writeString(buf, t.summaryKey());
         writeString(buf, t.factionA());
         writeString(buf, t.factionB() == null ? "" : t.factionB());
         ByteBufCodecs.VAR_INT.encode(buf, t.severity());
         buf.writeLong(t.gameTime());
      }
   }

   private static List<WorldStatusSnapshot.TheaterLine> readTheaterList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.TheaterLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.TheaterLine(
            readString(buf),
            readString(buf),
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readLong()
         ));
      }
      return list;
   }

   private static void writeCorridorList(ByteBuf buf, List<WorldStatusSnapshot.CorridorLine> list) {
      ByteBufCodecs.VAR_INT.encode(buf, list == null ? 0 : list.size());
      if (list == null) {
         return;
      }
      for (WorldStatusSnapshot.CorridorLine c : list) {
         writeString(buf, c.sourceFaction());
         writeString(buf, c.targetFaction());
         ByteBufCodecs.VAR_INT.encode(buf, c.deliveries());
         buf.writeBoolean(c.recognized());
         buf.writeBoolean(c.disrupted());
         writeString(buf, c.nameKey() == null ? "" : c.nameKey());
      }
   }

   private static List<WorldStatusSnapshot.CorridorLine> readCorridorList(ByteBuf buf) {
      int count = clampCount(ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<WorldStatusSnapshot.CorridorLine> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         list.add(new WorldStatusSnapshot.CorridorLine(
            readString(buf),
            readString(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readBoolean(),
            buf.readBoolean(),
            readString(buf)
         ));
      }
      return list;
   }

   private static void writeString(ByteBuf buf, String value) {
      ByteBufCodecs.STRING_UTF8.encode(buf, value == null ? "" : value);
   }

   private static String readString(ByteBuf buf) {
      String value = ByteBufCodecs.STRING_UTF8.decode(buf);
      return value == null ? "" : value;
   }

   private static int clampCount(int count, int max) {
      return Math.max(0, Math.min(count, max));
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
