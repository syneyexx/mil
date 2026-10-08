package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.panel.PanelLine;

public record ControlledMilitaryPayload(
   String villageUuid,
   String villageName,
   String ownerName,
   String currentRaidTargetVillageId,
   boolean raidInProgress,
   List<ControlledMilitaryPayload.RelationEntry> relations,
   List<PanelLine> militaryLines
) implements CustomPacketPayload {
   private static final int MAX_RELATION_ENTRIES = 64;
   private static final int MAX_MILITARY_LINES = 512;
   private static final int MAX_MILITARY_LINE_ARGS = 8;
   public static final Type<ControlledMilitaryPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "controlled_military"));
   public static final StreamCodec<ByteBuf, ControlledMilitaryPayload> STREAM_CODEC = StreamCodec.of(
      ControlledMilitaryPayload::encode, ControlledMilitaryPayload::decode
   );

   private static void encode(ByteBuf buf, ControlledMilitaryPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.currentRaidTargetVillageId);
      buf.writeBoolean(payload.raidInProgress);
      int relCount = Math.min(payload.relations.size(), 64);
      ByteBufCodecs.VAR_INT.encode(buf, relCount);

      for (int i = 0; i < relCount; i++) {
         ControlledMilitaryPayload.RelationEntry re = payload.relations.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, re.villageId());
         ByteBufCodecs.STRING_UTF8.encode(buf, re.villageName());
         ByteBufCodecs.STRING_UTF8.encode(buf, re.cultureName());
         ByteBufCodecs.VAR_INT.encode(buf, re.relation());
         ByteBufCodecs.STRING_UTF8.encode(buf, re.relationLabel());
      }

      encodeMilitaryLines(buf, payload.militaryLines);
   }

   private static ControlledMilitaryPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String ownerName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String currentRaidTargetVillageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean raidInProgress = buf.readBoolean();
      int rawRelCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int relCount = Math.min(rawRelCount, 64);
      List<ControlledMilitaryPayload.RelationEntry> relations = new ArrayList<>(relCount);

      for (int i = 0; i < rawRelCount; i++) {
         String vid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String vname = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String cname = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int rel = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         String label = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         if (i < relCount) {
            relations.add(new ControlledMilitaryPayload.RelationEntry(vid, vname, cname, rel, label));
         }
      }

      List<PanelLine> militaryLines = decodeMilitaryLines(buf);
      return new ControlledMilitaryPayload(villageUuid, villageName, ownerName, currentRaidTargetVillageId, raidInProgress, relations, militaryLines);
   }

   private static void encodeMilitaryLines(ByteBuf buf, List<PanelLine> lines) {
      ByteBufCodecs.VAR_INT.encode(buf, lines.size());

      for (PanelLine line : lines) {
         ByteBufCodecs.STRING_UTF8.encode(buf, line.text());
         buf.writeBoolean(line.isSeparator());
         ByteBufCodecs.STRING_UTF8.encode(buf, line.leftColumn() != null ? line.leftColumn() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, line.rightColumn() != null ? line.rightColumn() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, line.leftIcon() != null ? line.leftIcon() : "");
         buf.writeBoolean(line.translatable());
         String[] args = line.translatableArgs();
         int argsCount = args != null ? args.length : 0;
         ByteBufCodecs.VAR_INT.encode(buf, argsCount);
         if (args != null) {
            for (String arg : args) {
               ByteBufCodecs.STRING_UTF8.encode(buf, arg != null ? arg : "");
            }
         }

         ByteBufCodecs.VAR_INT.encode(buf, line.color());
         buf.writeBoolean(line.bold());
         ByteBufCodecs.BYTE.encode(buf, (byte)line.translatableArgMask());
      }
   }

   private static List<PanelLine> decodeMilitaryLines(ByteBuf buf) {
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, 512);
      List<PanelLine> result = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         String text = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean sep = buf.readBoolean();
         String leftCol = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String rightCol = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String icon = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean translatable = buf.readBoolean();
         int rawArgs = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int argsCount = Math.min(rawArgs, 8);
         String[] args = argsCount > 0 ? new String[argsCount] : null;

         for (int a = 0; a < argsCount; a++) {
            args[a] = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         for (int a = argsCount; a < rawArgs; a++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         int color = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         boolean bold = buf.readBoolean();
         int argMask = (Byte)ByteBufCodecs.BYTE.decode(buf) & 255;
         if (i < count) {
            String leftColVal = leftCol.isEmpty() ? null : leftCol;
            String rightColVal = rightCol.isEmpty() ? null : rightCol;
            String iconVal = icon.isEmpty() ? null : icon;
            result.add(new PanelLine(text, sep, leftColVal, rightColVal, iconVal, translatable, null, args, color, bold, argMask));
         }
      }

      return result;
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record RelationEntry(String villageId, String villageName, String cultureName, int relation, String relationLabel) {
   }
}
