package org.millenaire.civilization.bridge;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.diplomacy.AgreementStatus;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticAgreement;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.politics.PoliticalLeader;
import org.millenaire.faction.politics.PoliticsQueries;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.faction.war.WarState;
import org.millenaire.living.context.DialogueGameplayContext;
import org.millenaire.village.Village;

/**
 * Appends verified, role-safe civilization facts to NPC dialogue / panel context.
 * Never invents resources, treaties or wars — only reads authoritative managers.
 */
public final class CivilizationContextProvider {
   private CivilizationContextProvider() {
   }

   public static void appendVerified(ServerLevel level, @Nullable Village village, DialogueGameplayContext ctx) {
      if (level == null || village == null || ctx == null || !enabled()) {
         return;
      }
      FactionId factionId = village.getFactionId();
      if (factionId == null) {
         ctx.addVerified("factionAffiliation=independent");
         return;
      }
      ctx.addVerified("factionId=" + factionId.asString());

      PoliticalLeader leader = PoliticsQueries.leader(level, factionId);
      if (leader != null && leader.getDisplayName() != null && !leader.getDisplayName().isBlank()) {
         ctx.addVerified("factionLeader=" + leader.getDisplayName());
      }
      if (PoliticsQueries.successionPending(level, factionId)) {
         ctx.addVerified("successionPending=true");
      }

      FactionEconomicProfile profile = EconomySavedData.get(level).getTradeManager().getProfile(factionId);
      if (profile != null) {
         ctx.addVerified("economyBalance=" + profile.getBalanceLevel().name());
      }

      String development = CivilizationSavedData.get(level).getDevelopmentLabel(village.getId().uuid());
      if (development != null) {
         ctx.addVerified("settlementDevelopment=" + development);
      }

      CompoundTag popTag = CivilizationSavedData.get(level).getPopulationSnapshot(factionId.asString());
      if (popTag != null) {
         PopulationSnapshot pop = PopulationSnapshot.load(popTag);
         ctx.addVerified("factionResidents=" + pop.residents());
      }

      int activeTreaties = 0;
      for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getAllAgreements()) {
         if (agreement.getStatus() != AgreementStatus.ACTIVE) {
            continue;
         }
         if (factionId.equals(agreement.getPrimaryFactionId()) || factionId.equals(agreement.getSecondaryFactionId())) {
            activeTreaties++;
            if (activeTreaties <= 3) {
               ctx.addVerified("treaty=" + agreement.getType().name() + ":" + agreement.getStatus().name());
            }
         }
      }
      if (activeTreaties > 3) {
         ctx.addVerified("additionalTreaties=" + (activeTreaties - 3));
      }

      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (war.getState() == WarState.ENDED) {
            continue;
         }
         if (factionId.equals(war.getInitiatorFactionId()) || factionId.equals(war.getPrimaryTargetFactionId())) {
            ctx.addVerified("warState=" + war.getState().name());
            break;
         }
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
