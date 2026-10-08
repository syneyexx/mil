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
import org.millenaire.village.panel.PanelLine;
import org.slf4j.Logger;

public record VillageChiefPayload(VillageChiefPayload.ChiefIdentity identity, VillageChiefPayload.ChiefDynamic dynamic) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int CROP_REPUTATION = 8192;
   public static final int CROP_PRICE = 512;
   public static final int CULTURE_CONTROL_REPUTATION = 131072;
   private static final int MAX_RELATION_ENTRIES = 64;
   private static final int MAX_LIST_ENTRIES = 32;
   private static final int MAX_MILITARY_LINES = 512;
   private static final int MAX_MILITARY_LINE_ARGS = 8;
   public static final Type<VillageChiefPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_chief"));
   public static final StreamCodec<ByteBuf, VillageChiefPayload> STREAM_CODEC = StreamCodec.of(VillageChiefPayload::encode, VillageChiefPayload::decode);

   public int entityId() {
      return this.identity.entityId();
   }

   public String chiefName() {
      return this.identity.chiefName();
   }

   public String roleName() {
      return this.identity.roleName();
   }

   public String chiefTypeKey() {
      return this.identity.chiefTypeKey();
   }

   public String villageName() {
      return this.dynamic.villageName();
   }

   public String cultureName() {
      return this.dynamic.cultureName();
   }

   public int reputation() {
      return this.dynamic.reputation();
   }

   public String reputationLabel() {
      return this.dynamic.reputationLabel();
   }

   public int totalBuildings() {
      return this.dynamic.totalBuildings();
   }

   public int completeBuildings() {
      return this.dynamic.completeBuildings();
   }

   public int underConstructionBuildings() {
      return this.dynamic.underConstructionBuildings();
   }

   public int totalVillagers() {
      return this.dynamic.totalVillagers();
   }

   public String villageId() {
      return this.dynamic.villageId();
   }

   public int playerMoney() {
      return this.dynamic.playerMoney();
   }

   public int playerReputation() {
      return this.dynamic.playerReputation();
   }

   public List<VillageChiefPayload.PlayerBuildingEntry> playerBuildings() {
      return this.dynamic.playerBuildings();
   }

   public List<VillageChiefPayload.RelationEntry> relationEntries() {
      return this.dynamic.relationEntries();
   }

   public int diplomacyPoints() {
      return this.dynamic.diplomacyPoints();
   }

   public List<VillageChiefPayload.LearningOffer> cropOffers() {
      return this.dynamic.cropOffers();
   }

   public List<VillageChiefPayload.LearningOffer> huntingOffers() {
      return this.dynamic.huntingOffers();
   }

   public boolean cultureControlAvailable() {
      return this.dynamic.cultureControlAvailable();
   }

   public boolean hasCultureControl() {
      return this.dynamic.hasCultureControl();
   }

   public String cultureId() {
      return this.dynamic.cultureId();
   }

   public boolean controlledByPlayer() {
      return this.dynamic.controlledByPlayer();
   }

   public String currentRaidTargetVillageId() {
      return this.dynamic.currentRaidTargetVillageId();
   }

   public boolean raidInProgress() {
      return this.dynamic.raidInProgress();
   }

   public List<PanelLine> militaryLines() {
      return this.dynamic.militaryLines();
   }

   private static void encode(ByteBuf buf, VillageChiefPayload payload) {
      encodeIdentity(buf, payload.identity);
      encodeDynamic(buf, payload.dynamic);
   }

   private static VillageChiefPayload decode(ByteBuf buf) {
      VillageChiefPayload.ChiefIdentity identity = decodeIdentity(buf);
      VillageChiefPayload.ChiefDynamic dynamic = decodeDynamic(buf);
      return new VillageChiefPayload(identity, dynamic);
   }

   static void encodeIdentity(ByteBuf buf, VillageChiefPayload.ChiefIdentity identity) {
      ByteBufCodecs.VAR_INT.encode(buf, identity.entityId());
      ByteBufCodecs.STRING_UTF8.encode(buf, identity.chiefName());
      ByteBufCodecs.STRING_UTF8.encode(buf, identity.roleName());
      ByteBufCodecs.STRING_UTF8.encode(buf, identity.chiefTypeKey());
   }

   static VillageChiefPayload.ChiefIdentity decodeIdentity(ByteBuf buf) {
      int entityId = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String chiefName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String roleName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String chiefTypeKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      return new VillageChiefPayload.ChiefIdentity(entityId, chiefName, roleName, chiefTypeKey);
   }

   static void encodeDynamic(ByteBuf buf, VillageChiefPayload.ChiefDynamic d) {
      ByteBufCodecs.STRING_UTF8.encode(buf, d.villageName());
      ByteBufCodecs.STRING_UTF8.encode(buf, d.cultureName());
      ByteBufCodecs.VAR_INT.encode(buf, d.reputation());
      ByteBufCodecs.STRING_UTF8.encode(buf, d.reputationLabel());
      ByteBufCodecs.VAR_INT.encode(buf, d.totalBuildings());
      ByteBufCodecs.VAR_INT.encode(buf, d.completeBuildings());
      ByteBufCodecs.VAR_INT.encode(buf, d.underConstructionBuildings());
      ByteBufCodecs.VAR_INT.encode(buf, d.totalVillagers());
      ByteBufCodecs.STRING_UTF8.encode(buf, d.villageId());
      ByteBufCodecs.VAR_INT.encode(buf, d.playerMoney());
      ByteBufCodecs.VAR_INT.encode(buf, d.playerReputation());
      int pbCount = Math.min(d.playerBuildings().size(), 32);
      if (pbCount < d.playerBuildings().size()) {
         LOGGER.warn("VillageChiefPayload: truncating playerBuildings list from {} to {} entries", d.playerBuildings().size(), 32);
      }

      ByteBufCodecs.VAR_INT.encode(buf, pbCount);

      for (VillageChiefPayload.PlayerBuildingEntry entry : d.playerBuildings().subList(0, pbCount)) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.planSetId());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.nativeName());
         ByteBufCodecs.VAR_INT.encode(buf, entry.price());
         ByteBufCodecs.VAR_INT.encode(buf, entry.reputation());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.status());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.translationKey());
      }

      ByteBufCodecs.VAR_INT.encode(buf, d.relationEntries().size());

      for (VillageChiefPayload.RelationEntry re : d.relationEntries()) {
         ByteBufCodecs.STRING_UTF8.encode(buf, re.villageId());
         ByteBufCodecs.STRING_UTF8.encode(buf, re.villageName());
         ByteBufCodecs.STRING_UTF8.encode(buf, re.cultureName());
         ByteBufCodecs.VAR_INT.encode(buf, re.relation());
         ByteBufCodecs.STRING_UTF8.encode(buf, re.relationLabel());
      }

      ByteBufCodecs.VAR_INT.encode(buf, d.diplomacyPoints());
      ByteBufCodecs.VAR_INT.encode(buf, d.cropOffers().size());

      for (VillageChiefPayload.LearningOffer offer : d.cropOffers()) {
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.key());
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.itemName());
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.status());
      }

      ByteBufCodecs.VAR_INT.encode(buf, d.huntingOffers().size());

      for (VillageChiefPayload.LearningOffer offer : d.huntingOffers()) {
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.key());
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.itemName());
         ByteBufCodecs.STRING_UTF8.encode(buf, offer.status());
      }

      ByteBufCodecs.BOOL.encode(buf, d.cultureControlAvailable());
      ByteBufCodecs.BOOL.encode(buf, d.hasCultureControl());
      ByteBufCodecs.STRING_UTF8.encode(buf, d.cultureId());
      ByteBufCodecs.BOOL.encode(buf, d.controlledByPlayer());
      ByteBufCodecs.STRING_UTF8.encode(buf, d.currentRaidTargetVillageId());
      ByteBufCodecs.BOOL.encode(buf, d.raidInProgress());
      encodeMilitaryLines(buf, d.militaryLines());
   }

   static VillageChiefPayload.ChiefDynamic decodeDynamic(ByteBuf buf) {
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String cultureName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int reputation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String reputationLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int totalBuildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int completeBuildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int underConstructionBuildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int totalVillagers = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int playerMoney = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int playerReputation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rawPbCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int pbCount = Math.min(rawPbCount, 32);
      List<VillageChiefPayload.PlayerBuildingEntry> playerBuildings = new ArrayList<>(pbCount);

      for (int i = 0; i < pbCount; i++) {
         playerBuildings.add(
            new VillageChiefPayload.PlayerBuildingEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = pbCount; i < rawPbCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.VAR_INT.decode(buf);
         ByteBufCodecs.VAR_INT.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      int rawReCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int reCount = Math.min(rawReCount, 64);
      List<VillageChiefPayload.RelationEntry> relationEntries = new ArrayList<>(reCount);

      for (int i = 0; i < reCount; i++) {
         relationEntries.add(
            new VillageChiefPayload.RelationEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = reCount; i < rawReCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.VAR_INT.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      int diplomacyPoints = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rawCropCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int cropCount = Math.min(rawCropCount, 32);
      List<VillageChiefPayload.LearningOffer> cropOffers = new ArrayList<>(cropCount);

      for (int i = 0; i < cropCount; i++) {
         cropOffers.add(
            new VillageChiefPayload.LearningOffer(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = cropCount; i < rawCropCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      int rawHuntCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int huntCount = Math.min(rawHuntCount, 32);
      List<VillageChiefPayload.LearningOffer> huntingOffers = new ArrayList<>(huntCount);

      for (int i = 0; i < huntCount; i++) {
         huntingOffers.add(
            new VillageChiefPayload.LearningOffer(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }

      for (int i = huntCount; i < rawHuntCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
      }

      boolean cultureControlAvailable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean hasCultureControl = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      String cultureId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean controlledByPlayer = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      String currentRaidTargetVillageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean raidInProgress = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      List<PanelLine> militaryLines = decodeMilitaryLines(buf);
      return new VillageChiefPayload.ChiefDynamic(
         villageName,
         cultureName,
         reputation,
         reputationLabel,
         totalBuildings,
         completeBuildings,
         underConstructionBuildings,
         totalVillagers,
         villageId,
         playerMoney,
         playerReputation,
         playerBuildings,
         relationEntries,
         diplomacyPoints,
         cropOffers,
         huntingOffers,
         cultureControlAvailable,
         hasCultureControl,
         cultureId,
         controlledByPlayer,
         currentRaidTargetVillageId,
         raidInProgress,
         militaryLines
      );
   }

   private static void encodeMilitaryLines(ByteBuf buf, List<PanelLine> lines) {
      ByteBufCodecs.VAR_INT.encode(buf, lines.size());

      for (PanelLine line : lines) {
         ByteBufCodecs.STRING_UTF8.encode(buf, line.text());
         ByteBufCodecs.BOOL.encode(buf, line.isSeparator());
         ByteBufCodecs.STRING_UTF8.encode(buf, line.leftColumn() != null ? line.leftColumn() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, line.rightColumn() != null ? line.rightColumn() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, line.leftIcon() != null ? line.leftIcon() : "");
         ByteBufCodecs.BOOL.encode(buf, line.translatable());
         String[] args = line.translatableArgs();
         int argsCount = args != null ? args.length : 0;
         ByteBufCodecs.VAR_INT.encode(buf, argsCount);
         if (args != null) {
            for (String arg : args) {
               ByteBufCodecs.STRING_UTF8.encode(buf, arg != null ? arg : "");
            }
         }

         ByteBufCodecs.VAR_INT.encode(buf, line.color());
         ByteBufCodecs.BOOL.encode(buf, line.bold());
         ByteBufCodecs.BYTE.encode(buf, (byte)line.translatableArgMask());
      }
   }

   private static List<PanelLine> decodeMilitaryLines(ByteBuf buf) {
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, 512);
      List<PanelLine> result = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         String text = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean sep = (Boolean)ByteBufCodecs.BOOL.decode(buf);
         String leftCol = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String rightCol = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String icon = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         boolean translatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
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
         boolean bold = (Boolean)ByteBufCodecs.BOOL.decode(buf);
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

   public record ChiefDynamic(
      String villageName,
      String cultureName,
      int reputation,
      String reputationLabel,
      int totalBuildings,
      int completeBuildings,
      int underConstructionBuildings,
      int totalVillagers,
      String villageId,
      int playerMoney,
      int playerReputation,
      List<VillageChiefPayload.PlayerBuildingEntry> playerBuildings,
      List<VillageChiefPayload.RelationEntry> relationEntries,
      int diplomacyPoints,
      List<VillageChiefPayload.LearningOffer> cropOffers,
      List<VillageChiefPayload.LearningOffer> huntingOffers,
      boolean cultureControlAvailable,
      boolean hasCultureControl,
      String cultureId,
      boolean controlledByPlayer,
      String currentRaidTargetVillageId,
      boolean raidInProgress,
      List<PanelLine> militaryLines
   ) {
   }

   public record ChiefIdentity(int entityId, String chiefName, String roleName, String chiefTypeKey) {
   }

   public record LearningOffer(String key, String itemName, String status) {
   }

   public record PlayerBuildingEntry(String planSetId, String nativeName, int price, int reputation, String status, String translationKey) {
   }

   public record RelationEntry(String villageId, String villageName, String cultureName, int relation, String relationLabel) {
   }
}
