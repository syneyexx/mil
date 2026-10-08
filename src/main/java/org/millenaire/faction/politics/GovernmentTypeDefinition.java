package org.millenaire.faction.politics;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized data-driven government type rules. Not free-form strings.
 */
public final class GovernmentTypeDefinition {
   private static final Map<GovernmentType, GovernmentTypeDefinition> BY_TYPE = new EnumMap<>(GovernmentType.class);

   static {
      register(
         new GovernmentTypeDefinition(
            GovernmentType.CHIEFDOM,
            SuccessionMechanism.HEREDITARY,
            LeadershipRole.CHIEF,
            InterimGovernmentType.REGENCY,
            10,
            5,
            15,
            -5,
            EnumSet.of(PoliticalGroupType.RULING_ELITE, PoliticalGroupType.COMMONERS, PoliticalGroupType.TRADITIONALISTS, PoliticalGroupType.MILITARY),
            PoliticalGroupType.RULING_ELITE
         )
      );
      register(
         new GovernmentTypeDefinition(
            GovernmentType.TRIBAL_COUNCIL,
            SuccessionMechanism.COUNCIL_SELECTION,
            LeadershipRole.COUNCIL_HEAD,
            InterimGovernmentType.INTERIM_COUNCIL,
            5,
            8,
            5,
            5,
            EnumSet.of(
               PoliticalGroupType.RULING_ELITE,
               PoliticalGroupType.COMMONERS,
               PoliticalGroupType.TRADITIONALISTS,
               PoliticalGroupType.FARMERS,
               PoliticalGroupType.CLERGY
            ),
            PoliticalGroupType.TRADITIONALISTS
         )
      );
      register(
         new GovernmentTypeDefinition(
            GovernmentType.MONARCHY,
            SuccessionMechanism.HEREDITARY,
            LeadershipRole.KING,
            InterimGovernmentType.REGENCY,
            15,
            0,
            10,
            -10,
            EnumSet.of(
               PoliticalGroupType.NOBILITY,
               PoliticalGroupType.RULING_ELITE,
               PoliticalGroupType.CLERGY,
               PoliticalGroupType.MILITARY,
               PoliticalGroupType.COMMONERS,
               PoliticalGroupType.MERCHANTS
            ),
            PoliticalGroupType.NOBILITY
         )
      );
      register(
         new GovernmentTypeDefinition(
            GovernmentType.COUNCIL,
            SuccessionMechanism.COUNCIL_SELECTION,
            LeadershipRole.COUNCIL_HEAD,
            InterimGovernmentType.INTERIM_COUNCIL,
            0,
            10,
            0,
            10,
            EnumSet.of(
               PoliticalGroupType.RULING_ELITE,
               PoliticalGroupType.MERCHANTS,
               PoliticalGroupType.FARMERS,
               PoliticalGroupType.REFORMERS,
               PoliticalGroupType.TRADITIONALISTS
            ),
            PoliticalGroupType.RULING_ELITE
         )
      );
      register(
         new GovernmentTypeDefinition(
            GovernmentType.REPUBLIC,
            SuccessionMechanism.ELECTIVE,
            LeadershipRole.PRESIDENT,
            InterimGovernmentType.TEMPORARY_LEADERSHIP,
            -5,
            15,
            -5,
            15,
            EnumSet.of(
               PoliticalGroupType.MERCHANTS,
               PoliticalGroupType.COMMONERS,
               PoliticalGroupType.REFORMERS,
               PoliticalGroupType.FARMERS,
               PoliticalGroupType.MILITARY,
               PoliticalGroupType.TRADITIONALISTS
            ),
            PoliticalGroupType.MERCHANTS
         )
      );
      register(
         new GovernmentTypeDefinition(
            GovernmentType.MILITARY_RULE,
            SuccessionMechanism.MILITARY_SELECTION,
            LeadershipRole.WAR_LEADER,
            InterimGovernmentType.MILITARY_COMMAND,
            -10,
            -5,
            25,
            -15,
            EnumSet.of(PoliticalGroupType.MILITARY, PoliticalGroupType.RULING_ELITE, PoliticalGroupType.COMMONERS, PoliticalGroupType.TRADITIONALISTS),
            PoliticalGroupType.MILITARY
         )
      );
   }

   private final GovernmentType type;
   private final SuccessionMechanism successionMechanism;
   private final LeadershipRole defaultLeaderRole;
   private final InterimGovernmentType interimType;
   private final int legitimacyBonus;
   private final int stabilityBonus;
   private final int militaryInfluenceBonus;
   private final int economicInfluenceBonus;
   private final Set<PoliticalGroupType> defaultGroups;
   private final PoliticalGroupType dominantDefaultGroup;

   private GovernmentTypeDefinition(
      GovernmentType type,
      SuccessionMechanism successionMechanism,
      LeadershipRole defaultLeaderRole,
      InterimGovernmentType interimType,
      int legitimacyBonus,
      int stabilityBonus,
      int militaryInfluenceBonus,
      int economicInfluenceBonus,
      Set<PoliticalGroupType> defaultGroups,
      PoliticalGroupType dominantDefaultGroup
   ) {
      this.type = type;
      this.successionMechanism = successionMechanism;
      this.defaultLeaderRole = defaultLeaderRole;
      this.interimType = interimType;
      this.legitimacyBonus = legitimacyBonus;
      this.stabilityBonus = stabilityBonus;
      this.militaryInfluenceBonus = militaryInfluenceBonus;
      this.economicInfluenceBonus = economicInfluenceBonus;
      this.defaultGroups = Collections.unmodifiableSet(EnumSet.copyOf(defaultGroups));
      this.dominantDefaultGroup = dominantDefaultGroup;
   }

   private static void register(GovernmentTypeDefinition definition) {
      BY_TYPE.put(definition.type, definition);
   }

   public static GovernmentTypeDefinition of(GovernmentType type) {
      GovernmentTypeDefinition definition = BY_TYPE.get(type == null ? GovernmentType.CHIEFDOM : type);
      return definition == null ? BY_TYPE.get(GovernmentType.CHIEFDOM) : definition;
   }

   public GovernmentType getType() {
      return this.type;
   }

   public SuccessionMechanism getSuccessionMechanism() {
      return this.successionMechanism;
   }

   public LeadershipRole getDefaultLeaderRole() {
      return this.defaultLeaderRole;
   }

   public InterimGovernmentType getInterimType() {
      return this.interimType;
   }

   public int getLegitimacyBonus() {
      return this.legitimacyBonus;
   }

   public int getStabilityBonus() {
      return this.stabilityBonus;
   }

   public int getMilitaryInfluenceBonus() {
      return this.militaryInfluenceBonus;
   }

   public int getEconomicInfluenceBonus() {
      return this.economicInfluenceBonus;
   }

   public Set<PoliticalGroupType> getDefaultGroups() {
      return this.defaultGroups;
   }

   public PoliticalGroupType getDominantDefaultGroup() {
      return this.dominantDefaultGroup;
   }

   public boolean allowsCoup() {
      return this.type != GovernmentType.TRIBAL_COUNCIL;
   }

   public int centralizationBias() {
      return switch (this.type) {
         case MONARCHY, MILITARY_RULE -> 70;
         case CHIEFDOM -> 55;
         case COUNCIL, TRIBAL_COUNCIL -> 40;
         case REPUBLIC -> 45;
      };
   }
}
