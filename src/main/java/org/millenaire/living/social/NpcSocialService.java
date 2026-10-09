package org.millenaire.living.social;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.living.knowledge.NpcKnowledgeCategory;
import org.millenaire.living.knowledge.NpcKnowledgeItem;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class NpcSocialService {
   private NpcSocialService() {
   }

   public static NpcSocialProfile profile(ServerLevel level, UUID npcId) {
      return SocialSavedData.get(level).getOrCreate(npcId);
   }

   public static void ensurePersonality(NpcSocialProfile profile, VillagerRecord record) {
      if (profile.personalityInitialized() || !personalityEnabled()) {
         return;
      }
      long seed = record.getUuid().getMostSignificantBits() ^ record.getUuid().getLeastSignificantBits();
      String role = record.getRoleName() == null ? "" : record.getRoleName().toLowerCase(Locale.ROOT);
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      int sociability = 40 + (int)(Math.floorMod(seed, 41));
      int caution = 35 + (int)(Math.floorMod(seed >> 8, 41));
      int generosity = 40 + (int)(Math.floorMod(seed >> 16, 31));
      int ambition = 30 + (int)(Math.floorMod(seed >> 24, 51));
      int patience = 40 + (int)(Math.floorMod(seed >> 32, 41));
      int loyalty = 45 + (int)(Math.floorMod(seed >> 40, 41));
      if (role.contains("guard") || type != null && (type.hasTag("helpInAttacks") || type.isDefensive())) {
         caution += 15;
         loyalty += 10;
         sociability -= 5;
      }
      if (role.contains("chief") || role.contains("leader")) {
         ambition += 15;
         respectBoost(profile);
         loyalty += 5;
      }
      if (role.contains("merchant") || role.contains("seller") || role.contains("trader")) {
         sociability += 10;
         generosity += 5;
      }
      if (record.getIdentity().getChildSize() >= 0 || (type != null && type.hasTag("child"))) {
         sociability += 10;
         caution -= 10;
         ambition -= 10;
      }
      profile.initializePersonality(sociability, caution, generosity, ambition, patience, loyalty);
   }

   private static void respectBoost(NpcSocialProfile profile) {
      // no-op placeholder kept for clarity; traits set via initializePersonality args
   }

   public static void bootstrapFamily(NpcSocialProfile profile, VillagerRecord record, Village village) {
      if (village == null) {
         return;
      }
      String spouse = record.getIdentity().getSpousesName();
      String father = record.getIdentity().getFathersName();
      String mother = record.getIdentity().getMothersName();
      for (VillagerRecord other : village.getVillagerRecords().values()) {
         if (other.getUuid().equals(record.getUuid())) {
            continue;
         }
         String full = (other.getFirstName() + " " + other.getFamilyName()).trim();
         if (spouse != null && !spouse.isBlank() && (spouse.equalsIgnoreCase(full) || spouse.equalsIgnoreCase(other.getFirstName()))) {
            NpcRelationship rel = profile.getOrCreateRelationship(other.getUuid(), NpcRelationshipType.FAMILY_SPOUSE);
            rel.setType(NpcRelationshipType.FAMILY_SPOUSE);
            if (rel.familiarity() < 60) {
               rel.adjust(60 - rel.familiarity(), 20, 30, 10, 0, 0L);
            }
         }
         if (father != null && !father.isBlank() && (father.equalsIgnoreCase(full) || father.equalsIgnoreCase(other.getFirstName()))) {
            profile.getOrCreateRelationship(other.getUuid(), NpcRelationshipType.FAMILY_PARENT).setType(NpcRelationshipType.FAMILY_PARENT);
         }
         if (mother != null && !mother.isBlank() && (mother.equalsIgnoreCase(full) || mother.equalsIgnoreCase(other.getFirstName()))) {
            profile.getOrCreateRelationship(other.getUuid(), NpcRelationshipType.FAMILY_PARENT).setType(NpcRelationshipType.FAMILY_PARENT);
         }
         String otherFather = other.getIdentity().getFathersName();
         String otherMother = other.getIdentity().getMothersName();
         String selfFull = (record.getFirstName() + " " + record.getFamilyName()).trim();
         if (otherFather != null && (otherFather.equalsIgnoreCase(selfFull) || otherFather.equalsIgnoreCase(record.getFirstName()))
            || otherMother != null && (otherMother.equalsIgnoreCase(selfFull) || otherMother.equalsIgnoreCase(record.getFirstName()))) {
            profile.getOrCreateRelationship(other.getUuid(), NpcRelationshipType.FAMILY_CHILD).setType(NpcRelationshipType.FAMILY_CHILD);
         }
      }
   }

   public static void learnFromEvent(ServerLevel level, Village village, LivingEvent event, @Nullable UUID focusNpc) {
      if (!enabled() || event == null || village == null) {
         return;
      }
      NpcKnowledgeCategory category = categoryFor(event.eventType());
      if (category == null) {
         return;
      }
      long now = event.gameTime();
      long expiry = now + 24000L * 30L;
      SocialSavedData data = SocialSavedData.get(level);
      int knowCap = MillenaireServerConfig.SERVER.npcKnowledgeRetentionLimit.getAsInt();

      if (focusNpc != null) {
         NpcSocialProfile profile = data.getOrCreate(focusNpc);
         VillagerRecord record = village.getVillagerRecords().get(focusNpc);
         if (record != null) {
            ensurePersonality(profile, record);
         }
         profile.addKnowledge(
            new NpcKnowledgeItem(
               event.eventId(),
               category,
               event.details(),
               focusNpc,
               focusNpc,
               event.eventId(),
               now,
               now,
               true,
               0,
               expiry,
               100
            ),
            knowCap
         );
         applyMood(profile, event, now);
         data.setDirty();
         return;
      }

      // Village-wide public facts: seed to a bounded subset of residents (chiefs/guards/builders first).
      int seeded = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (seeded >= 8) {
            break;
         }
         if (record.isKilled()) {
            continue;
         }
         if (!shouldKnowPublicEvent(record, event.eventType())) {
            continue;
         }
         NpcSocialProfile profile = data.getOrCreate(record.getUuid());
         ensurePersonality(profile, record);
         profile.addKnowledge(
            new NpcKnowledgeItem(
               event.eventId(),
               category,
               event.details(),
               event.actorId(),
               record.getUuid(),
               event.eventId(),
               now,
               now,
               event.actorId() != null && event.actorId().equals(record.getUuid()),
               0,
               expiry,
               80
            ),
            knowCap
         );
         applyMood(profile, event, now);
         seeded++;
      }
      data.setDirty();
   }

   private static boolean shouldKnowPublicEvent(VillagerRecord record, LivingEventType type) {
      String role = record.getRoleName() == null ? "" : record.getRoleName().toLowerCase(Locale.ROOT);
      VillagerType vType = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      boolean chief = role.contains("chief") || role.contains("leader");
      boolean guard = role.contains("guard") || (vType != null && (vType.hasTag("helpInAttacks") || vType.isDefensive()));
      boolean builder = role.contains("builder") || role.contains("lumberman") || role.contains("carpenter");
      return switch (type) {
         case ATTACK_STARTED, ATTACK_ENDED -> true;
         case MATERIAL_SHORTAGE_DETECTED, MATERIAL_SHORTAGE_RESOLVED, ASSISTANCE_REQUEST_COMPLETED, PLAYER_DELIVERY_VERIFIED ->
            chief || builder;
         case RESIDENT_DIED -> true;
         default -> chief || guard;
      };
   }

   @Nullable
   private static NpcKnowledgeCategory categoryFor(LivingEventType type) {
      return switch (type) {
         case MATERIAL_SHORTAGE_DETECTED, MATERIAL_SHORTAGE_RESOLVED -> NpcKnowledgeCategory.PUBLIC_NEED;
         case ASSISTANCE_REQUEST_COMPLETED, PLAYER_DELIVERY_VERIFIED, ASSISTANCE_REQUEST_ACCEPTED -> NpcKnowledgeCategory.PLAYER_ASSISTANCE;
         case RESIDENT_DIED -> NpcKnowledgeCategory.RESIDENT_DEATH;
         case ATTACK_STARTED, ATTACK_ENDED -> NpcKnowledgeCategory.SECURITY_THREAT;
         case BUILDING_STARTED, BUILDING_COMPLETED, BUILDING_UPGRADED -> NpcKnowledgeCategory.ACTIVE_CONSTRUCTION;
         case SOCIAL_INTERACTION, KNOWLEDGE_SHARED, MOOD_REACTION -> null;
         default -> NpcKnowledgeCategory.VILLAGE_EVENT;
      };
   }

   private static void applyMood(NpcSocialProfile profile, LivingEvent event, long now) {
      if (!personalityEnabled()) {
         return;
      }
      String mood = switch (event.eventType()) {
         case ATTACK_STARTED -> "afraid";
         case RESIDENT_DIED -> "sad";
         case ASSISTANCE_REQUEST_COMPLETED, PLAYER_DELIVERY_VERIFIED, MATERIAL_SHORTAGE_RESOLVED, BUILDING_COMPLETED -> "happy";
         case MATERIAL_SHORTAGE_DETECTED -> "worried";
         default -> null;
      };
      if (mood != null) {
         profile.setMood(mood, now);
      }
   }

   public static void tickMoodDecay(ServerLevel level) {
      if (!personalityEnabled()) {
         return;
      }
      long now = level.getGameTime();
      long interval = MillenaireServerConfig.SERVER.npcMoodDecayInterval.getAsInt();
      SocialSavedData data = SocialSavedData.get(level);
      boolean dirty = false;
      for (NpcSocialProfile profile : data.profiles().values()) {
         String before = profile.mood();
         profile.decayMood(now, interval);
         if (!before.equals(profile.mood())) {
            dirty = true;
         }
      }
      if (dirty) {
         data.setDirty();
      }
   }

   public static void collectLivingIds(Village village, Set<UUID> out) {
      for (UUID id : village.getVillagerRecords().keySet()) {
         out.add(id);
      }
   }

   public static void periodicCleanup(ServerLevel level, Iterable<Village> villages) {
      Set<UUID> living = new HashSet<>();
      for (Village village : villages) {
         collectLivingIds(village, living);
      }
      SocialSavedData.get(level).pruneOrphans(living, Math.max(200, MillenaireServerConfig.SERVER.npcRelationshipRetention.getAsInt() * 20));
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcSocialEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static boolean personalityEnabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcPersonalityEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
