package org.millenaire.labyrinth.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The twenty-five kinds of Gebondene that inhabit the Millénaire Labyrinth.
 */
public enum GebondeneKind {
   ARCHITECT("architect", "architect", 1.1F, 3.0F, 280.0, 14.0, 0.24, 12.0, Tier.LEADER, GebondeneAttitude.GASTVRIJ, 80),
   SPIJKERMEESTER("spijkermeester", "spijkermeester", 0.8F, 2.4F, 120.0, 10.0, 0.26, 8.0, Tier.LEADER, GebondeneAttitude.GASTVRIJ, 40),
   RAMMELAAR("rammelaar", "rammelaar", 0.6F, 1.8F, 22.0, 3.0, 0.30, 1.0, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 5),
   STILLE("stille", "stille", 0.55F, 1.75F, 20.0, 2.0, 0.28, 1.0, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 5),
   GIDS("gids", "gids", 0.6F, 1.9F, 24.0, 3.0, 0.29, 1.5, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 7),
   GASTHEER("gastheer", "gastheer", 0.65F, 1.95F, 26.0, 3.0, 0.27, 2.0, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 8),
   NOTARIS("notaris", "notaris", 0.6F, 1.9F, 24.0, 2.5, 0.26, 1.5, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 7),
   SCHATTER("schatter", "schatter", 0.6F, 1.85F, 24.0, 3.0, 0.27, 1.5, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 7),
   SMID("smid", "smid", 0.7F, 1.95F, 34.0, 6.0, 0.25, 5.0, Tier.RARE, GebondeneAttitude.GASTVRIJ, 12),
   SLEUTELDRAGER("sleuteldrager", "sleuteldrager", 0.6F, 1.9F, 30.0, 5.0, 0.27, 4.0, Tier.GATE, GebondeneAttitude.GASTVRIJ, 10),
   PELGRIM("pelgrim", "pelgrim", 0.6F, 1.85F, 22.0, 2.5, 0.28, 1.0, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 5),
   BLINDE("blinde", "blinde", 0.6F, 1.8F, 20.0, 2.0, 0.26, 1.0, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 5),
   GETUIGE("getuige", "getuige", 0.6F, 1.85F, 24.0, 2.5, 0.27, 1.5, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 7),
   ARCHIVARIS("archivaris", "archivaris", 0.6F, 1.9F, 24.0, 2.5, 0.26, 1.5, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 7),
   KETTINGZUSTER("kettingzuster", "kettingzuster", 0.6F, 1.9F, 32.0, 5.5, 0.28, 4.5, Tier.GATE, GebondeneAttitude.GASTVRIJ, 11),
   POORTWACHTER("poortwachter", "poortwachter", 0.7F, 2.05F, 40.0, 7.0, 0.26, 6.0, Tier.GATE, GebondeneAttitude.GASTVRIJ, 14),
   NAAISTER("naaister", "naaister", 0.55F, 1.8F, 22.0, 2.5, 0.28, 1.0, Tier.SOCIAL, GebondeneAttitude.GASTVRIJ, 6),
   FLUITENDE("fluitende", "fluitende", 0.55F, 1.75F, 20.0, 2.0, 0.30, 0.5, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 5),
   DANSERES("danseres", "danseres", 0.55F, 1.8F, 22.0, 2.5, 0.31, 1.0, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 5),
   KLOKKENLUIDER("klokkenluider", "klokkenluider", 0.75F, 2.2F, 48.0, 8.0, 0.24, 7.0, Tier.LEADER, GebondeneAttitude.GASTVRIJ, 20),
   STILTEWACHTER("stiltewachter", "stiltewachter", 0.65F, 2.0F, 36.0, 6.0, 0.27, 5.0, Tier.GATE, GebondeneAttitude.GASTVRIJ, 12),
   SPIEGELGAST("spiegelgast", "spiegelgast", 0.6F, 1.9F, 28.0, 4.0, 0.29, 2.0, Tier.RARE, GebondeneAttitude.GASTVRIJ, 13),
   TWEELING("tweeling", "tweeling", 0.55F, 1.7F, 26.0, 4.0, 0.30, 2.0, Tier.RARE, GebondeneAttitude.GASTVRIJ, 12),
   ECHO("echo", "echo", 0.55F, 1.75F, 20.0, 2.5, 0.29, 1.0, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 6),
   MARIONET("marionet", "marionet", 0.5F, 1.6F, 18.0, 3.0, 0.28, 0.5, Tier.COMMON, GebondeneAttitude.GASTVRIJ, 6);

   public enum Tier {
      COMMON,
      SOCIAL,
      GATE,
      RARE,
      LEADER
   }

   private static final Map<String, GebondeneKind> BY_ID = Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(GebondeneKind::id, Function.identity()));

   private final String id;
   private final String displayKeySuffix;
   private final float width;
   private final float height;
   private final double health;
   private final double damage;
   private final double speed;
   private final double armor;
   private final Tier tier;
   private final GebondeneAttitude defaultAttitude;
   private final int xpReward;

   GebondeneKind(
      String id,
      String displayKeySuffix,
      float width,
      float height,
      double health,
      double damage,
      double speed,
      double armor,
      Tier tier,
      GebondeneAttitude defaultAttitude,
      int xpReward
   ) {
      this.id = id;
      this.displayKeySuffix = displayKeySuffix;
      this.width = width;
      this.height = height;
      this.health = health;
      this.damage = damage;
      this.speed = speed;
      this.armor = armor;
      this.tier = tier;
      this.defaultAttitude = defaultAttitude;
      this.xpReward = xpReward;
   }

   public String id() {
      return this.id;
   }

   /** Suffix for translation keys such as {@code entity.millenaire.gebondene.<suffix>}. */
   public String displayKeySuffix() {
      return this.displayKeySuffix;
   }

   public float width() {
      return this.width;
   }

   public float height() {
      return this.height;
   }

   public double health() {
      return this.health;
   }

   public double damage() {
      return this.damage;
   }

   public double speed() {
      return this.speed;
   }

   public double armor() {
      return this.armor;
   }

   public Tier tier() {
      return this.tier;
   }

   public GebondeneAttitude defaultAttitude() {
      return this.defaultAttitude;
   }

   public int xpReward() {
      return this.xpReward;
   }

   public static Optional<GebondeneKind> byId(String id) {
      if (id == null || id.isBlank()) {
         return Optional.empty();
      }
      return Optional.ofNullable(BY_ID.get(id.trim().toLowerCase(Locale.ROOT)));
   }

   public static GebondeneKind byIdOrDefault(String id, GebondeneKind fallback) {
      return byId(id).orElse(fallback);
   }

   public static List<GebondeneKind> ofTiers(Tier... tiers) {
      List<Tier> wanted = Arrays.asList(tiers);
      return Arrays.stream(values()).filter(k -> wanted.contains(k.tier)).toList();
   }
}
