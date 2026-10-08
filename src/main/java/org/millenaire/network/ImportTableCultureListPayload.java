package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.ImportTablePlanResolver;
import org.slf4j.Logger;

public record ImportTableCultureListPayload(String cultureKey, List<ImportTableCultureListPayload.Entry> entries) implements CustomPacketPayload {
   static final int MAX_ENTRIES = 4096;
   static final int MAX_VARIANTS = 64;
   static final int MAX_LEVELS = 64;
   static final int MAX_SUBS = 256;
   static final int MAX_SHADOWED = 16;
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<ImportTableCultureListPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_culture_list"));
   public static final StreamCodec<ByteBuf, ImportTableCultureListPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableCultureListPayload::encode, ImportTableCultureListPayload::decode
   );

   public static ImportTableCultureListPayload of(String cultureKey, List<ImportTablePlanResolver.CultureListingEntry> src) {
      if (src.size() > 4096) {
         LOGGER.warn("ImportTableCultureListPayload: culture {} has {} buildings, truncating to {}", new Object[]{cultureKey, src.size(), 4096});
      }

      List<ImportTableCultureListPayload.Entry> wire = new ArrayList<>(Math.min(src.size(), 4096));

      for (ImportTablePlanResolver.CultureListingEntry e : src) {
         if (wire.size() >= 4096) {
            break;
         }

         wire.add(entryOf(cultureKey, e));
      }

      return new ImportTableCultureListPayload(cultureKey, wire);
   }

   static ImportTableCultureListPayload.Entry entryOf(String cultureKey, ImportTablePlanResolver.CultureListingEntry e) {
      Map<String, List<Integer>> variants = capMap(e.variantLevels(), 64, 64, cultureKey, e.buildingId());
      List<String> subs = e.referencedSubBuildingIds();
      if (subs.size() > 256) {
         LOGGER.warn("ImportTableCultureListPayload: {}/{} has {} sub refs, truncating to {}", new Object[]{cultureKey, e.buildingId(), subs.size(), 256});
         subs = List.copyOf(subs.subList(0, 256));
      }

      List<String> shadowed = e.shadowedPaths();
      if (shadowed.size() > 16) {
         shadowed = List.copyOf(shadowed.subList(0, 16));
      }

      return new ImportTableCultureListPayload.Entry(
         e.buildingId(),
         e.category(),
         e.isSubBuilding(),
         e.registered(),
         variants,
         subs,
         e.parentBuildingId(),
         e.parentVariant(),
         e.parentTriggerLevel(),
         e.provenance(),
         shadowed
      );
   }

   private static Map<String, List<Integer>> capMap(
      Map<String, List<Integer>> variantLevels, int maxVariants, int maxLevels, String cultureKey, String buildingId
   ) {
      boolean over = variantLevels.size() > maxVariants || variantLevels.values().stream().anyMatch(l -> l.size() > maxLevels);
      if (!over) {
         return variantLevels;
      }

      LOGGER.warn("ImportTableCultureListPayload: {}/{} exceeds variant/level caps, truncating", cultureKey, buildingId);
      Map<String, List<Integer>> capped = new LinkedHashMap<>();

      for (Map.Entry<String, List<Integer>> ve : variantLevels.entrySet()) {
         if (capped.size() >= maxVariants) {
            break;
         }

         List<Integer> levels = ve.getValue();
         capped.put(ve.getKey(), levels.size() > maxLevels ? List.copyOf(levels.subList(0, maxLevels)) : levels);
      }

      return capped;
   }

   private static void encode(ByteBuf buf, ImportTableCultureListPayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureKey);
      int entryCount = Math.min(p.entries.size(), 4096);
      ByteBufCodecs.VAR_INT.encode(buf, entryCount);

      for (int i = 0; i < entryCount; i++) {
         encodeEntry(buf, p.entries.get(i));
      }
   }

   static void encodeEntry(ByteBuf buf, ImportTableCultureListPayload.Entry e) {
      ByteBufCodecs.STRING_UTF8.encode(buf, e.buildingId());
      ByteBufCodecs.STRING_UTF8.encode(buf, e.category());
      ByteBufCodecs.BOOL.encode(buf, e.isSubBuilding());
      ByteBufCodecs.BOOL.encode(buf, e.registered());
      int variantCount = Math.min(e.variantLevels().size(), 64);
      ByteBufCodecs.VAR_INT.encode(buf, variantCount);
      int variantsWritten = 0;

      for (Map.Entry<String, List<Integer>> ve : e.variantLevels().entrySet()) {
         if (variantsWritten++ >= variantCount) {
            break;
         }

         ByteBufCodecs.STRING_UTF8.encode(buf, ve.getKey());
         int levelCount = Math.min(ve.getValue().size(), 64);
         ByteBufCodecs.VAR_INT.encode(buf, levelCount);

         for (int l = 0; l < levelCount; l++) {
            ByteBufCodecs.VAR_INT.encode(buf, ve.getValue().get(l));
         }
      }

      int subCount = Math.min(e.referencedSubBuildingIds().size(), 256);
      ByteBufCodecs.VAR_INT.encode(buf, subCount);

      for (int sub = 0; sub < subCount; sub++) {
         ByteBufCodecs.STRING_UTF8.encode(buf, e.referencedSubBuildingIds().get(sub));
      }

      ByteBufCodecs.STRING_UTF8.encode(buf, e.parentBuildingId());
      ByteBufCodecs.STRING_UTF8.encode(buf, e.parentVariant());
      ByteBufCodecs.VAR_INT.encode(buf, e.parentTriggerLevel());
      ByteBufCodecs.STRING_UTF8.encode(buf, e.provenance());
      int shadowCount = Math.min(e.shadowedPaths().size(), 16);
      ByteBufCodecs.VAR_INT.encode(buf, shadowCount);

      for (int sh = 0; sh < shadowCount; sh++) {
         ByteBufCodecs.STRING_UTF8.encode(buf, e.shadowedPaths().get(sh));
      }
   }

   private static int readCount(ByteBuf buf, int max, String what) {
      int count = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      if (count >= 0 && count <= max) {
         return count;
      } else {
         throw new DecoderException("ImportTableCultureListPayload: " + what + " count " + count + " outside [0, " + max + "]");
      }
   }

   private static ImportTableCultureListPayload decode(ByteBuf buf) {
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int entryCount = readCount(buf, 4096, "entry");
      List<ImportTableCultureListPayload.Entry> entries = new ArrayList<>(entryCount);

      for (int i = 0; i < entryCount; i++) {
         entries.add(decodeEntry(buf));
      }

      return new ImportTableCultureListPayload(cultureKey, entries);
   }

   static ImportTableCultureListPayload.Entry decodeEntry(ByteBuf buf) {
      String buildingId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String category = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean isSub = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean registered = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      int variantCount = readCount(buf, 64, "variant");
      Map<String, List<Integer>> variantLevels = new LinkedHashMap<>();

      for (int v = 0; v < variantCount; v++) {
         String variant = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int levelCount = readCount(buf, 64, "level");
         List<Integer> levels = new ArrayList<>(levelCount);

         for (int l = 0; l < levelCount; l++) {
            levels.add((Integer)ByteBufCodecs.VAR_INT.decode(buf));
         }

         variantLevels.put(variant, List.copyOf(levels));
      }

      int subCount = readCount(buf, 256, "sub-building");
      List<String> subs = new ArrayList<>(subCount);

      for (int sub = 0; sub < subCount; sub++) {
         subs.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      }

      String parentBuildingId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String parentVariant = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int parentTriggerLevel = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String provenance = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int shadowCount = readCount(buf, 16, "shadowed");
      List<String> shadowed = new ArrayList<>(shadowCount);

      for (int sh = 0; sh < shadowCount; sh++) {
         shadowed.add((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      }

      return new ImportTableCultureListPayload.Entry(
         buildingId,
         category,
         isSub,
         registered,
         Map.copyOf(variantLevels),
         List.copyOf(subs),
         parentBuildingId,
         parentVariant,
         parentTriggerLevel,
         provenance,
         List.copyOf(shadowed)
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record Entry(
      String buildingId,
      String category,
      boolean isSubBuilding,
      boolean registered,
      Map<String, List<Integer>> variantLevels,
      List<String> referencedSubBuildingIds,
      String parentBuildingId,
      String parentVariant,
      int parentTriggerLevel,
      String provenance,
      List<String> shadowedPaths
   ) {
   }
}
