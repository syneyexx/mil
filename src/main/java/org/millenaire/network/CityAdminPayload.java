package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;

/** Server → client: city admin snapshot for owned settlement. */
public record CityAdminPayload(
   String villageId,
   String villageName,
   String factionDisplay,
   String developmentKey,
   int residents,
   int housingCapacity,
   boolean overcrowded,
   String inventoryCertaintyKey,
   String projectLabel,
   String immigrationHint,
   String tradeHint,
   String colonizationHint,
   List<String> needLines,
   List<CityAdminPayload.ActionEntry> actions,
   int tabIndex,
   String feedbackKey
) implements CustomPacketPayload {
   private static final int MAX_STRING = 256;
   private static final int MAX_NEEDS = 12;
   private static final int MAX_ACTIONS = 64;
   public static final Type<CityAdminPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "city_admin"));
   public static final StreamCodec<ByteBuf, CityAdminPayload> STREAM_CODEC =
      StreamCodec.of(CityAdminPayload::encode, CityAdminPayload::decode);

   public record ActionEntry(String actionId, boolean enabled, String labelKey, String blockedReasonKey, String targetId) {
   }

   public static CityAdminPayload fromSnapshot(CivilizationVillageSnapshot snap, int tabIndex) {
      return fromSnapshot(snap, tabIndex, "");
   }

   public static CityAdminPayload fromSnapshot(CivilizationVillageSnapshot snap, int tabIndex, String feedbackKey) {
      List<String> needs = new ArrayList<>();
      if (snap.topNeeds() != null) {
         for (CivilizationVillageSnapshot.NeedLine need : snap.topNeeds()) {
            if (needs.size() >= MAX_NEEDS) {
               break;
            }
            if ("shopstats".equals(need.summaryKey())) {
               needs.add("shopstats|" + (need.detail() == null ? "0|0" : need.detail()));
            } else {
               needs.add(
                  need.summaryKey()
                     + "|"
                     + need.detail()
                     + "|"
                     + need.severityCode()
                     + "|"
                     + (need.shortageProven() ? "1" : "0")
                     + "|"
                     + need.observedShortage()
               );
            }
         }
      }
      List<ActionEntry> actions = new ArrayList<>();
      if (snap.allowedActions() != null) {
         for (CivilizationVillageSnapshot.AllowedAction action : snap.allowedActions()) {
            if (actions.size() >= MAX_ACTIONS) {
               break;
            }
            actions.add(
               new ActionEntry(
                  action.actionId().name(),
                  action.enabled(),
                  action.labelKey() == null ? "" : action.labelKey(),
                  action.blockedReasonKey() == null ? "" : action.blockedReasonKey(),
                  action.targetId() == null ? "" : action.targetId()
               )
            );
         }
      }
      String project = snap.currentProject() == null
         ? ""
         : snap.currentProject().nameKeyOrLiteral() + " (" + snap.currentProject().statusCode() + ")";
      String colonization = snap.colonization() == null
         ? ""
         : snap.colonization().statusLabelKey() + " / " + snap.colonization().nextStepKey();
      String immigrationHint = "";
      String tradeHint = "";
      for (ActionEntry action : actions) {
         if ("SET_IMMIGRATION_POLICY".equals(action.actionId()) && action.targetId() != null && !action.targetId().isBlank()) {
            String next = action.targetId();
            String current = switch (next) {
               case "RESTRICTED" -> "OPEN";
               case "CLOSED" -> "RESTRICTED";
               default -> "CLOSED";
            };
            int vacancies = Math.max(0, snap.housingCapacity() - snap.residents());
            immigrationHint = current + "|" + vacancies;
         }
         if ("SET_TRADE_POLICY".equals(action.actionId()) && action.targetId() != null && !action.targetId().isBlank()) {
            String next = action.targetId();
            tradeHint = switch (next) {
               case "INBOUND_ONLY" -> "OPEN";
               case "CLOSED" -> "INBOUND_ONLY";
               default -> "CLOSED";
            };
         }
      }
      return new CityAdminPayload(
         snap.villageId().toString(),
         snap.villageName() == null ? "" : snap.villageName(),
         snap.factionDisplayName() == null ? "" : snap.factionDisplayName(),
         snap.developmentLabelKey() == null ? "" : snap.developmentLabelKey(),
         snap.residents(),
         snap.housingCapacity(),
         snap.overcrowded(),
         snap.inventoryCertainty().labelKey(),
         project,
         immigrationHint,
         tradeHint,
         colonization,
         List.copyOf(needs),
         List.copyOf(actions),
         tabIndex,
         feedbackKey == null ? "" : feedbackKey
      );
   }

   private static void encode(ByteBuf buf, CityAdminPayload p) {
      write(buf, p.villageId);
      write(buf, p.villageName);
      write(buf, p.factionDisplay);
      write(buf, p.developmentKey);
      ByteBufCodecs.VAR_INT.encode(buf, p.residents);
      ByteBufCodecs.VAR_INT.encode(buf, p.housingCapacity);
      ByteBufCodecs.BOOL.encode(buf, p.overcrowded);
      write(buf, p.inventoryCertaintyKey);
      write(buf, p.projectLabel);
      write(buf, p.immigrationHint);
      write(buf, p.tradeHint);
      write(buf, p.colonizationHint);
      ByteBufCodecs.VAR_INT.encode(buf, p.needLines.size());
      for (String line : p.needLines) {
         write(buf, line);
      }
      ByteBufCodecs.VAR_INT.encode(buf, p.actions.size());
      for (ActionEntry a : p.actions) {
         write(buf, a.actionId);
         ByteBufCodecs.BOOL.encode(buf, a.enabled);
         write(buf, a.labelKey);
         write(buf, a.blockedReasonKey);
         write(buf, a.targetId);
      }
      ByteBufCodecs.VAR_INT.encode(buf, p.tabIndex);
      write(buf, p.feedbackKey);
   }

   private static CityAdminPayload decode(ByteBuf buf) {
      String villageId = read(buf);
      String villageName = read(buf);
      String factionDisplay = read(buf);
      String developmentKey = read(buf);
      int residents = ByteBufCodecs.VAR_INT.decode(buf);
      int housing = ByteBufCodecs.VAR_INT.decode(buf);
      boolean overcrowded = ByteBufCodecs.BOOL.decode(buf);
      String inventoryCertainty = read(buf);
      String project = read(buf);
      String imm = read(buf);
      String trade = read(buf);
      String colony = read(buf);
      int needCount = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_NEEDS);
      List<String> needs = new ArrayList<>(needCount);
      for (int i = 0; i < needCount; i++) {
         needs.add(read(buf));
      }
      int actionCount = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_ACTIONS);
      List<ActionEntry> actions = new ArrayList<>(actionCount);
      for (int i = 0; i < actionCount; i++) {
         actions.add(new ActionEntry(read(buf), ByteBufCodecs.BOOL.decode(buf), read(buf), read(buf), read(buf)));
      }
      int tab = ByteBufCodecs.VAR_INT.decode(buf);
      String feedback = read(buf);
      return new CityAdminPayload(
         villageId, villageName, factionDisplay, developmentKey, residents, housing, overcrowded,
         inventoryCertainty, project, imm, trade, colony, List.copyOf(needs), List.copyOf(actions), tab, feedback
      );
   }

   private static void write(ByteBuf buf, String value) {
      ByteBufCodecs.STRING_UTF8.encode(buf, value == null ? "" : truncate(value));
   }

   private static String read(ByteBuf buf) {
      return truncate(ByteBufCodecs.STRING_UTF8.decode(buf));
   }

   private static String truncate(String value) {
      if (value == null) {
         return "";
      }
      return value.length() > MAX_STRING ? value.substring(0, MAX_STRING) : value;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
