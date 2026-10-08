package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.AgreementType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticManager;

/**
 * Centralized war-declaration legality checks.
 */
public final class WarDeclarationValidator {
   private WarDeclarationValidator() {
   }

   public record Result(boolean allowed, @Nullable String denialReason) {
      public static Result ok() {
         return new Result(true, null);
      }

      public static Result deny(String reason) {
         return new Result(false, reason);
      }
   }

   public static Result validate(ServerLevel level, FactionId attacker, FactionId defender, WarManager warManager) {
      Objects.requireNonNull(level, "level");
      if (attacker == null || defender == null) {
         return Result.deny("missing_faction");
      }

      if (attacker.equals(defender)) {
         return Result.deny("self_war");
      }

      var factions = FactionSavedData.get(level).getFactionManager();
      if (!factions.exists(attacker) || !factions.exists(defender)) {
         return Result.deny("unknown_faction");
      }

      if (warManager.hasActiveWarBetween(attacker, defender)) {
         return Result.deny("existing_active_war");
      }

      DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      if (diplomacy.isAllied(attacker, defender)) {
         return Result.deny("allied");
      }

      if (diplomacy.hasDefensivePact(attacker, defender) && !diplomacy.hasActiveAgreement(AgreementType.EMBARGO, attacker, defender)) {
         // Defensive pact between the pair blocks mutual war declaration.
         return Result.deny("defensive_pact");
      }

      if (diplomacy.hasNonAggressionPact(attacker, defender)) {
         // NAP must be broken first or will be terminated explicitly by declaration flow after validation override.
         // Declaration is still legal but requires NAP termination — allowed with note.
         return Result.ok();
      }

      return Result.ok();
   }
}
