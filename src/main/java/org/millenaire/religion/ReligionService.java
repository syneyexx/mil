package org.millenaire.religion;

import org.millenaire.religion.actions.ReligionActionService;
import org.millenaire.religion.calendar.ReligionCalendarService;
import org.millenaire.religion.ceremony.CeremonyCoordinator;
import org.millenaire.religion.clergy.ClergyService;
import org.millenaire.religion.daily.DailyPracticeService;
import org.millenaire.religion.diplomacy.ReligionIncidentService;
import org.millenaire.religion.pilgrimage.PilgrimageService;
import org.millenaire.religion.policy.ReligionPolicyService;
import org.millenaire.religion.presentation.ReligionPresentationService;
import org.millenaire.religion.supply.RitualSupplyService;

/**
 * Facade for religion subsystems.
 */
public final class ReligionService {
   private static final ReligionService INSTANCE = new ReligionService();

   private final CeremonyCoordinator ceremonyCoordinator = new CeremonyCoordinator();
   private final DailyPracticeService dailyPracticeService = new DailyPracticeService();
   private final ClergyService clergyService = new ClergyService();
   private final RitualSupplyService supplyService = new RitualSupplyService();
   private final ReligionCalendarService calendarService = new ReligionCalendarService();
   private final PilgrimageService pilgrimageService = new PilgrimageService();
   private final ReligionPolicyService policyService = new ReligionPolicyService();
   private final ReligionIncidentService incidentService = new ReligionIncidentService();
   private final ReligionPresentationService presentationService = new ReligionPresentationService();
   private final ReligionActionService actionService = new ReligionActionService();

   private ReligionService() {
   }

   public static ReligionService get() {
      return INSTANCE;
   }

   public CeremonyCoordinator ceremonies() {
      return this.ceremonyCoordinator;
   }

   public DailyPracticeService daily() {
      return this.dailyPracticeService;
   }

   public ClergyService clergy() {
      return this.clergyService;
   }

   public RitualSupplyService supplies() {
      return this.supplyService;
   }

   public ReligionCalendarService calendar() {
      return this.calendarService;
   }

   public PilgrimageService pilgrimages() {
      return this.pilgrimageService;
   }

   public ReligionPolicyService policy() {
      return this.policyService;
   }

   public ReligionIncidentService incidents() {
      return this.incidentService;
   }

   public ReligionPresentationService presentation() {
      return this.presentationService;
   }

   public ReligionActionService actions() {
      return this.actionService;
   }
}
