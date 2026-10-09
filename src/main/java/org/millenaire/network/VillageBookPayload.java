package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelLine;
import org.millenaire.village.panel.PanelType;
import org.slf4j.Logger;

public record VillageBookPayload(
   String villageName,
   String cultureId,
   List<PanelContent> sections,
   List<MapData.MapBuilding> mapBuildings,
   List<MapData.MapVillager> mapVillagers,
   int mapPlayerX,
   int mapPlayerZ,
   int mapCenterX,
   int mapCenterZ,
   MapData.MapTerrain mapTerrain,
   List<MapData.MapPath> mapPaths,
   boolean hasMapData,
   boolean degraded,
   String villageId,
   boolean hasFeedback,
   boolean feedbackSuccess,
   String feedbackKey,
   String feedbackDetail,
   int restoreSection,
   int restorePage,
   String selectedTargetId
) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_SECTIONS = 8;
   private static final int MAX_LINES = 200;
   private static final int MAX_MAP_ENTRIES = 256;
   private static final int MAX_ARGS = 8;
   private static final int MAX_PATHS = 8192;
   public static final Type<VillageBookPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_book"));
   public static final StreamCodec<ByteBuf, VillageBookPayload> STREAM_CODEC = StreamCodec.of(VillageBookPayload::encode, VillageBookPayload::decode);

   public static VillageBookPayload open(
      String villageName,
      String cultureId,
      List<PanelContent> sections,
      List<MapData.MapBuilding> mapBuildings,
      List<MapData.MapVillager> mapVillagers,
      int mapPlayerX,
      int mapPlayerZ,
      int mapCenterX,
      int mapCenterZ,
      MapData.MapTerrain mapTerrain,
      List<MapData.MapPath> mapPaths,
      boolean hasMapData,
      boolean degraded,
      String villageId
   ) {
      return new VillageBookPayload(
         villageName,
         cultureId,
         sections,
         mapBuildings,
         mapVillagers,
         mapPlayerX,
         mapPlayerZ,
         mapCenterX,
         mapCenterZ,
         mapTerrain,
         mapPaths,
         hasMapData,
         degraded,
         villageId == null ? "" : villageId,
         false,
         false,
         "",
         "",
         0,
         0,
         ""
      );
   }

   public VillageBookPayload withFeedback(
      boolean success, String key, String detail, int section, int page, String selectedTarget
   ) {
      return new VillageBookPayload(
         this.villageName,
         this.cultureId,
         this.sections,
         this.mapBuildings,
         this.mapVillagers,
         this.mapPlayerX,
         this.mapPlayerZ,
         this.mapCenterX,
         this.mapCenterZ,
         this.mapTerrain,
         this.mapPaths,
         this.hasMapData,
         this.degraded,
         this.villageId,
         true,
         success,
         key == null ? "" : key,
         detail == null ? "" : detail,
         section,
         page,
         selectedTarget == null ? "" : selectedTarget
      );
   }

   public VillageBookPayload withNavRestore(int section, int page, String selectedTarget) {
      return new VillageBookPayload(
         this.villageName,
         this.cultureId,
         this.sections,
         this.mapBuildings,
         this.mapVillagers,
         this.mapPlayerX,
         this.mapPlayerZ,
         this.mapCenterX,
         this.mapCenterZ,
         this.mapTerrain,
         this.mapPaths,
         this.hasMapData,
         this.degraded,
         this.villageId,
         this.hasFeedback,
         this.feedbackSuccess,
         this.feedbackKey,
         this.feedbackDetail,
         section,
         page,
         selectedTarget == null ? "" : selectedTarget
      );
   }

   private static void encode(ByteBuf buf, VillageBookPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureId);
      ByteBufCodecs.BOOL.encode(buf, payload.hasMapData);
      ByteBufCodecs.BOOL.encode(buf, payload.degraded);
      int encSectionCount = Math.min(payload.sections.size(), MAX_SECTIONS);
      if (encSectionCount < payload.sections.size()) {
         LOGGER.warn("VillageBookPayload: truncating sections list from {} to {} entries", payload.sections.size(), MAX_SECTIONS);
      }

      ByteBufCodecs.VAR_INT.encode(buf, encSectionCount);

      for (PanelContent section : payload.sections.subList(0, encSectionCount)) {
         ByteBufCodecs.STRING_UTF8.encode(buf, section.type().name());
         ByteBufCodecs.STRING_UTF8.encode(buf, section.title());
         ByteBufCodecs.BOOL.encode(buf, section.titleTranslatable());
         String[] titleArgs = section.titleArgs();
         int titleArgCount = titleArgs != null ? titleArgs.length : 0;
         ByteBufCodecs.VAR_INT.encode(buf, titleArgCount);
         if (titleArgs != null) {
            for (String tArg : titleArgs) {
               ByteBufCodecs.STRING_UTF8.encode(buf, tArg != null ? tArg : "");
            }
         }

         int encLineCount = Math.min(section.lines().size(), MAX_LINES);
         ByteBufCodecs.VAR_INT.encode(buf, encLineCount);

         for (PanelLine line : section.lines().subList(0, encLineCount)) {
            ByteBufCodecs.STRING_UTF8.encode(buf, line.leftColumn() != null ? line.leftColumn() : line.text());
            ByteBufCodecs.STRING_UTF8.encode(buf, line.rightColumn() != null ? line.rightColumn() : "");
            ByteBufCodecs.STRING_UTF8.encode(buf, line.leftIcon() != null ? line.leftIcon() : "");
            ByteBufCodecs.BOOL.encode(buf, line.isSeparator());
            ByteBufCodecs.BOOL.encode(buf, line.translatable());
            String[] args = line.translatableArgs();
            int argsCount = args != null ? args.length : 0;
            ByteBufCodecs.VAR_INT.encode(buf, argsCount);
            if (args != null) {
               for (String arg : args) {
                  ByteBufCodecs.STRING_UTF8.encode(buf, arg);
               }
            }

            ByteBufCodecs.VAR_INT.encode(buf, line.color());
            ByteBufCodecs.BOOL.encode(buf, line.bold());
            ByteBufCodecs.BYTE.encode(buf, (byte)line.translatableArgMask());
            ByteBufCodecs.STRING_UTF8.encode(buf, line.nativePrefix() != null ? line.nativePrefix() : "");
            encodeNavTarget(buf, line.navTarget());
         }
      }

      if (payload.hasMapData) {
         MapData.encodeBuildingList(buf, payload.mapBuildings);
         MapData.encodeVillagerList(buf, payload.mapVillagers);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapPlayerX);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapPlayerZ);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapCenterX);
         ByteBufCodecs.VAR_INT.encode(buf, payload.mapCenterZ);
         MapData.encodeTerrain(buf, payload.mapTerrain);
         MapData.encodePathList(buf, payload.mapPaths);
      }

      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId != null ? payload.villageId : "");
      ByteBufCodecs.BOOL.encode(buf, payload.hasFeedback);
      ByteBufCodecs.BOOL.encode(buf, payload.feedbackSuccess);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.feedbackKey != null ? payload.feedbackKey : "");
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.feedbackDetail != null ? payload.feedbackDetail : "");
      ByteBufCodecs.VAR_INT.encode(buf, payload.restoreSection);
      ByteBufCodecs.VAR_INT.encode(buf, payload.restorePage);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.selectedTargetId != null ? payload.selectedTargetId : "");
   }

   private static void encodeNavTarget(ByteBuf buf, PanelLine.PanelNavTarget nav) {
      boolean hasNav = nav != null;
      ByteBufCodecs.BOOL.encode(buf, hasNav);
      if (hasNav) {
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.targetStateName() != null ? nav.targetStateName() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.cultureKey() != null ? nav.cultureKey() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.categoryKey() != null ? nav.categoryKey() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.itemKey() != null ? nav.itemKey() : "");
      }
   }

   private static PanelLine.PanelNavTarget decodeNavTarget(ByteBuf buf) {
      boolean hasNav = ByteBufCodecs.BOOL.decode(buf);
      if (!hasNav) {
         return null;
      }
      String state = ByteBufCodecs.STRING_UTF8.decode(buf);
      String culture = ByteBufCodecs.STRING_UTF8.decode(buf);
      String category = ByteBufCodecs.STRING_UTF8.decode(buf);
      String item = ByteBufCodecs.STRING_UTF8.decode(buf);
      return new PanelLine.PanelNavTarget(state, culture, category, item);
   }

   private static void skipNavTarget(ByteBuf buf) {
      boolean hasNav = ByteBufCodecs.BOOL.decode(buf);
      if (hasNav) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }
   }

   private static void skipLine(ByteBuf buf) {
      ByteBufCodecs.STRING_UTF8.decode(buf);
      ByteBufCodecs.STRING_UTF8.decode(buf);
      ByteBufCodecs.STRING_UTF8.decode(buf);
      ByteBufCodecs.BOOL.decode(buf);
      ByteBufCodecs.BOOL.decode(buf);
      int skipArgs = ByteBufCodecs.VAR_INT.decode(buf);
      for (int a = 0; a < skipArgs; a++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }
      ByteBufCodecs.VAR_INT.decode(buf);
      ByteBufCodecs.BOOL.decode(buf);
      ByteBufCodecs.BYTE.decode(buf);
      ByteBufCodecs.STRING_UTF8.decode(buf);
      skipNavTarget(buf);
   }

   private static VillageBookPayload decode(ByteBuf buf) {
      String villageName = ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureId = ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean hasMapData = ByteBufCodecs.BOOL.decode(buf);
      boolean degraded = ByteBufCodecs.BOOL.decode(buf);
      int rawSectionCount = ByteBufCodecs.VAR_INT.decode(buf);
      int sectionCount = Math.min(rawSectionCount, MAX_SECTIONS);
      List<PanelContent> sections = new ArrayList<>(sectionCount);

      for (int s = 0; s < sectionCount; s++) {
         String typeName = ByteBufCodecs.STRING_UTF8.decode(buf);
         String title = ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean titleTranslatable = ByteBufCodecs.BOOL.decode(buf);
         int rawTitleArgCount = ByteBufCodecs.VAR_INT.decode(buf);
         int titleArgCount = Math.min(rawTitleArgCount, MAX_ARGS);
         String[] titleArgs = null;
         if (titleArgCount > 0) {
            titleArgs = new String[titleArgCount];

            for (int ta = 0; ta < titleArgCount; ta++) {
               titleArgs[ta] = ByteBufCodecs.STRING_UTF8.decode(buf);
            }
         }

         for (int ta = titleArgCount; ta < rawTitleArgCount; ta++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         int rawLineCount = ByteBufCodecs.VAR_INT.decode(buf);
         int lineCount = Math.min(rawLineCount, MAX_LINES);
         List<PanelLine> lines = new ArrayList<>(lineCount);

         for (int i = 0; i < lineCount; i++) {
            String leftText = ByteBufCodecs.STRING_UTF8.decode(buf);
            String rightText = ByteBufCodecs.STRING_UTF8.decode(buf);
            String leftIcon = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean isSeparator = ByteBufCodecs.BOOL.decode(buf);
            boolean translatable = ByteBufCodecs.BOOL.decode(buf);
            int rawArgsCount = ByteBufCodecs.VAR_INT.decode(buf);
            int argsCount = Math.min(rawArgsCount, MAX_ARGS);
            String[] transArgs = null;
            if (argsCount > 0) {
               transArgs = new String[argsCount];

               for (int a = 0; a < argsCount; a++) {
                  transArgs[a] = ByteBufCodecs.STRING_UTF8.decode(buf);
               }
            }

            for (int a = argsCount; a < rawArgsCount; a++) {
               ByteBufCodecs.STRING_UTF8.decode(buf);
            }

            int color = ByteBufCodecs.VAR_INT.decode(buf);
            boolean bold = ByteBufCodecs.BOOL.decode(buf);
            int argMask = ByteBufCodecs.BYTE.decode(buf) & 255;
            String nativePfx = ByteBufCodecs.STRING_UTF8.decode(buf);
            String nativePrefixVal = nativePfx.isEmpty() ? null : nativePfx;
            PanelLine.PanelNavTarget nav = decodeNavTarget(buf);
            if (isSeparator) {
               lines.add(PanelLine.separator());
            } else if (!leftIcon.isEmpty() && translatable) {
               String rightColVal = rightText.isEmpty() ? null : rightText;
               lines.add(new PanelLine(leftText, false, null, rightColVal, leftIcon, true, nav, transArgs, color, bold, argMask, nativePrefixVal));
            } else if (!leftIcon.isEmpty()) {
               lines.add(new PanelLine("", false, leftText, rightText, leftIcon, translatable, nav, transArgs, color, bold, argMask, nativePrefixVal));
            } else if (!rightText.isEmpty()) {
               lines.add(new PanelLine("", false, leftText, rightText, null, translatable, nav, transArgs, color, bold, argMask, nativePrefixVal));
            } else {
               lines.add(new PanelLine(leftText, false, null, null, null, translatable, nav, transArgs, color, bold, argMask, nativePrefixVal));
            }
         }

         for (int i = lineCount; i < rawLineCount; i++) {
            skipLine(buf);
         }

         PanelType type = PanelType.fromName(typeName);
         sections.add(new PanelContent(type, title, lines, titleTranslatable, titleArgs));
      }

      for (int s = sectionCount; s < rawSectionCount; s++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         int skipTitleArgs = ByteBufCodecs.VAR_INT.decode(buf);

         for (int ta = 0; ta < skipTitleArgs; ta++) {
            ByteBufCodecs.STRING_UTF8.decode(buf);
         }

         int skipLineCount = ByteBufCodecs.VAR_INT.decode(buf);

         for (int i = 0; i < skipLineCount; i++) {
            skipLine(buf);
         }
      }

      List<MapData.MapBuilding> buildings = List.of();
      List<MapData.MapVillager> villagers = List.of();
      int playerX = 0;
      int playerZ = 0;
      int centerX = 0;
      int centerZ = 0;
      MapData.MapTerrain terrain = MapData.MapTerrain.EMPTY;
      List<MapData.MapPath> paths = List.of();
      if (hasMapData) {
         buildings = MapData.decodeBuildingList(buf, MAX_MAP_ENTRIES);
         villagers = MapData.decodeVillagerList(buf, MAX_MAP_ENTRIES);
         playerX = ByteBufCodecs.VAR_INT.decode(buf);
         playerZ = ByteBufCodecs.VAR_INT.decode(buf);
         centerX = ByteBufCodecs.VAR_INT.decode(buf);
         centerZ = ByteBufCodecs.VAR_INT.decode(buf);
         terrain = MapData.decodeTerrain(buf);
         paths = MapData.decodePathList(buf, MAX_PATHS);
      }

      String villageId = ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean hasFeedback = ByteBufCodecs.BOOL.decode(buf);
      boolean feedbackSuccess = ByteBufCodecs.BOOL.decode(buf);
      String feedbackKey = ByteBufCodecs.STRING_UTF8.decode(buf);
      String feedbackDetail = ByteBufCodecs.STRING_UTF8.decode(buf);
      int restoreSection = ByteBufCodecs.VAR_INT.decode(buf);
      int restorePage = ByteBufCodecs.VAR_INT.decode(buf);
      String selectedTargetId = ByteBufCodecs.STRING_UTF8.decode(buf);

      return new VillageBookPayload(
         villageName,
         cultureId,
         sections,
         buildings,
         villagers,
         playerX,
         playerZ,
         centerX,
         centerZ,
         terrain,
         paths,
         hasMapData,
         degraded,
         villageId,
         hasFeedback,
         feedbackSuccess,
         feedbackKey,
         feedbackDetail,
         restoreSection,
         restorePage,
         selectedTargetId
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
