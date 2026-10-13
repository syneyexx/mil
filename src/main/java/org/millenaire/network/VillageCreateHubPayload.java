package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record VillageCreateHubPayload(
   String villageUuid,
   String villageName,
   String cultureId,
   int population,
   int buildings,
   int openJobs,
   int openBeds,
   int threatLevel,
   List<CultureStandingRow> standings,
   List<LogRow> logRows
) implements CustomPacketPayload {
   public static final Type<VillageCreateHubPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "village_create_hub")
   );
   public static final StreamCodec<ByteBuf, VillageCreateHubPayload> STREAM_CODEC = StreamCodec.of(
      VillageCreateHubPayload::encode, VillageCreateHubPayload::decode
   );

   private static void encode(ByteBuf buf, VillageCreateHubPayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureId);
      ByteBufCodecs.VAR_INT.encode(buf, p.population);
      ByteBufCodecs.VAR_INT.encode(buf, p.buildings);
      ByteBufCodecs.VAR_INT.encode(buf, p.openJobs);
      ByteBufCodecs.VAR_INT.encode(buf, p.openBeds);
      ByteBufCodecs.VAR_INT.encode(buf, p.threatLevel);
      ByteBufCodecs.VAR_INT.encode(buf, p.standings.size());
      for (CultureStandingRow r : p.standings) {
         ByteBufCodecs.STRING_UTF8.encode(buf, r.cultureId());
         ByteBufCodecs.STRING_UTF8.encode(buf, r.displayName());
         ByteBufCodecs.VAR_INT.encode(buf, r.standing());
         buf.writeBoolean(r.atWar());
      }
      ByteBufCodecs.VAR_INT.encode(buf, p.logRows.size());
      for (LogRow r : p.logRows) {
         buf.writeLong(r.day());
         ByteBufCodecs.STRING_UTF8.encode(buf, r.action());
         ByteBufCodecs.STRING_UTF8.encode(buf, r.cultureA());
         ByteBufCodecs.STRING_UTF8.encode(buf, r.cultureB());
         ByteBufCodecs.VAR_INT.encode(buf, r.standingAfter());
      }
   }

   private static VillageCreateHubPayload decode(ByteBuf buf) {
      String villageUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureId = ByteBufCodecs.STRING_UTF8.decode(buf);
      int population = ByteBufCodecs.VAR_INT.decode(buf);
      int buildings = ByteBufCodecs.VAR_INT.decode(buf);
      int openJobs = ByteBufCodecs.VAR_INT.decode(buf);
      int openBeds = ByteBufCodecs.VAR_INT.decode(buf);
      int threat = ByteBufCodecs.VAR_INT.decode(buf);
      int standingCount = ByteBufCodecs.VAR_INT.decode(buf);
      List<CultureStandingRow> standings = new ArrayList<>(standingCount);
      for (int i = 0; i < standingCount; i++) {
         standings.add(
            new CultureStandingRow(
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               buf.readBoolean()
            )
         );
      }
      int logCount = ByteBufCodecs.VAR_INT.decode(buf);
      List<LogRow> logs = new ArrayList<>(logCount);
      for (int i = 0; i < logCount; i++) {
         logs.add(
            new LogRow(
               buf.readLong(),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf)
            )
         );
      }
      return new VillageCreateHubPayload(
         villageUuid, villageName, cultureId, population, buildings, openJobs, openBeds, threat, standings, logs
      );
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record CultureStandingRow(String cultureId, String displayName, int standing, boolean atWar) {
   }

   public record LogRow(long day, String action, String cultureA, String cultureB, int standingAfter) {
   }
}
