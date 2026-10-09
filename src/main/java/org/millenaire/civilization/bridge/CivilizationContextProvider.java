package org.millenaire.civilization.bridge;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.presentation.CivilizationFactionSnapshot;
import org.millenaire.civilization.presentation.CivilizationPresentationService;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.faction.FactionId;
import org.millenaire.living.context.DialogueGameplayContext;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Appends verified, role-safe civilization facts to NPC dialogue / panel context.
 * Uses the same server-derived presentation snapshots as UI — never invents resources,
 * treaties, colonies or wars. LLM text may describe these facts and point at server
 * action buttons; it never authorizes mutations.
 */
public final class CivilizationContextProvider {
   private CivilizationContextProvider() {
   }

   public static void appendVerified(ServerLevel level, @Nullable Village village, DialogueGameplayContext ctx) {
      appendVerified(level, village, ctx, null);
   }

   public static void appendVerified(
      ServerLevel level,
      @Nullable Village village,
      DialogueGameplayContext ctx,
      @Nullable VillagerRecord speaker
   ) {
      if (level == null || village == null || ctx == null || !enabled()) {
         return;
      }
      CivilizationVillageSnapshot snap = CivilizationPresentationService.villageSnapshot(level, village, null);
      RoleScope scope = resolveScope(speaker);

      if (snap.factionId() == null) {
         ctx.addVerified("factionAffiliation=independent");
      } else {
         String factionLabel = snap.factionDisplayName().isBlank() ? "unnamed_faction" : snap.factionDisplayName();
         ctx.addVerified("factionDisplayName=" + factionLabel);
         ctx.addVerified("settlementDevelopment=" + snap.developmentCode());
      }

      ctx.addVerified("residents=" + snap.residents());
      ctx.addVerified("housingCapacity=" + snap.housingCapacity());
      if (snap.overcrowded()) {
         ctx.addVerified("housingPressure=overcrowded");
      }
      if (!snap.inventoryKnown()) {
         ctx.addVerified("inventoryAvailability=unknown_chunks_unloaded");
      }

      if (scope.includesLocalNeeds() && !snap.topNeeds().isEmpty()) {
         CivilizationVillageSnapshot.NeedLine need = snap.topNeeds().getFirst();
         ctx.addVerified("topNeed=" + need.detail() + " shortage=" + need.shortage());
      }

      if (scope.includesProjects() && snap.currentProject() != null) {
         ctx.addVerified(
            "currentProject=" + snap.currentProject().nameKeyOrLiteral()
               + " status=" + snap.currentProject().statusCode()
         );
      }

      if (scope.includesTrade()) {
         if (snap.tradeSummary() != null) {
            ctx.addVerified(
               "activeSupportRequest=" + snap.tradeSummary().itemLabel()
                  + " status=" + snap.tradeSummary().statusCode()
            );
         }
         if (snap.connection() != null) {
            ctx.addVerified(
               "regionalConnection=" + snap.connection().endpointName()
                  + " status=" + snap.connection().statusCode()
                  + (snap.connection().reasonCode() == null || snap.connection().reasonCode().isBlank() || "none".equals(snap.connection().reasonCode())
                     ? ""
                     : " reason=" + snap.connection().reasonCode())
            );
         }
      }

      if (scope.includesColonization() && snap.colonization() != null) {
         CivilizationVillageSnapshot.ColonizationLine c = snap.colonization();
         ctx.addVerified(
            "colonizationStatus=" + c.statusCode()
               + " founders=" + c.migratedFounders() + "/" + c.founderTarget()
               + " reason=" + c.reasonCode()
         );
         if (c.refundOutstanding()) {
            ctx.addVerified("colonizationRefundOutstanding=true");
         }
      }

      if (scope.includesMigration() && snap.migration() != null) {
         ctx.addVerified(
            "migration=" + snap.migration().migrated() + "/" + snap.migration().target()
               + " to=" + snap.migration().destinationName()
               + " reason=" + snap.migration().reasonCode()
         );
      }

      if (scope.includesFactionWide() && snap.factionId() != null) {
         FactionId factionId = FactionId.tryParse(snap.factionId());
         if (factionId != null) {
            CivilizationFactionSnapshot faction = CivilizationPresentationService.factionSnapshot(level, factionId);
            if (faction.leaderName() != null) {
               ctx.addVerified("factionLeader=" + faction.leaderName());
            }
            ctx.addVerified("factionSettlements=" + faction.settlementCount());
            ctx.addVerified("factionResidents=" + faction.residents());
            if (faction.atWar()) {
               ctx.addVerified("warState=active");
            }
            if (faction.activeTreaties() > 0) {
               ctx.addVerified("activeTreaties=" + faction.activeTreaties());
            }
         }
      }

      if (scope.includesLocalHistory()) {
         int n = 0;
         for (CivilizationVillageSnapshot.HistoryLine h : snap.recentHistory()) {
            if (n >= 3) {
               break;
            }
            ctx.addVerified("recentEvent=" + h.summary());
            n++;
         }
      }

      // Point at available server-owned actions — never invent proposal ids.
      int actionHints = 0;
      for (CivilizationVillageSnapshot.AllowedAction action : snap.allowedActions()) {
         if (!action.enabled() || actionHints >= 3) {
            continue;
         }
         ctx.addVerified("availablePlayerAction=" + action.actionId().name().toLowerCase());
         actionHints++;
      }

      ctx.addVerified("promptRule=no_invented_migrants_colonies_or_trades");
      ctx.addVerified("promptRule=no_mutation_authority_from_dialogue");
   }

   private static RoleScope resolveScope(@Nullable VillagerRecord speaker) {
      if (speaker == null || speaker.getVillagerTypeId() == null) {
         return RoleScope.RESIDENT;
      }
      VillagerType type = ModCultures.getVillagerType(speaker.getVillagerTypeId());
      if (type == null || type.id() == null) {
         return RoleScope.RESIDENT;
      }
      String path = type.id().getPath();
      if (path.contains("chief") || path.contains("leader") || path.contains("headman") || path.contains("seneschal")) {
         return RoleScope.CHIEF;
      }
      if (path.contains("merchant") || path.contains("trader") || path.contains("innkeeper") || path.contains("seller")) {
         return RoleScope.MERCHANT;
      }
      if (path.contains("builder") || path.contains("mason") || path.contains("carpenter") || path.contains("architect")) {
         return RoleScope.BUILDER;
      }
      return RoleScope.RESIDENT;
   }

   private enum RoleScope {
      CHIEF,
      MERCHANT,
      BUILDER,
      RESIDENT;

      boolean includesLocalNeeds() {
         return this == CHIEF || this == MERCHANT || this == BUILDER;
      }

      boolean includesProjects() {
         return this == CHIEF || this == BUILDER;
      }

      boolean includesTrade() {
         return this == CHIEF || this == MERCHANT;
      }

      boolean includesColonization() {
         return this == CHIEF;
      }

      boolean includesMigration() {
         return this == CHIEF || this == RESIDENT;
      }

      boolean includesFactionWide() {
         return this == CHIEF;
      }

      boolean includesLocalHistory() {
         return true; // all roles get bounded local events; count capped above
      }
   }

   private static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationNpcContextEnabled.get())
            && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationEvolutionEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
