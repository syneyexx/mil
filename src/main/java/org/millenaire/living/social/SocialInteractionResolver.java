package org.millenaire.living.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.living.knowledge.NpcKnowledgeCategory;
import org.millenaire.living.knowledge.NpcKnowledgeItem;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Applies bounded relationship and knowledge effects after a completed NPC-to-NPC chat.
 */
public final class SocialInteractionResolver {
   private static final int MAX_KNOWLEDGE_HOPS = 3;

   private SocialInteractionResolver() {
   }

   public static void onChatCompleted(ServerLevel level, Village village, VillagerRecord a, VillagerRecord b) {
      if (!enabled() || level == null || village == null || a == null || b == null) {
         return;
      }
      if (a.getUuid().equals(b.getUuid())) {
         return;
      }

      long now = level.getGameTime();
      int cooldown = MillenaireServerConfig.SERVER.npcSocialInteractionCooldown.getAsInt();
      SocialSavedData data = SocialSavedData.get(level);
      NpcSocialProfile profileA = data.getOrCreate(a.getUuid());
      NpcSocialProfile profileB = data.getOrCreate(b.getUuid());

      if (now - profileA.lastSocialInteractionGameTime() < cooldown && now - profileB.lastSocialInteractionGameTime() < cooldown) {
         return;
      }

      NpcSocialService.ensurePersonality(profileA, a);
      NpcSocialService.ensurePersonality(profileB, b);
      NpcSocialService.bootstrapFamily(profileA, a, village);
      NpcSocialService.bootstrapFamily(profileB, b, village);

      NpcRelationshipType typeA = inferType(a, b);
      NpcRelationshipType typeB = inferType(b, a);
      NpcRelationship relA = profileA.getOrCreateRelationship(b.getUuid(), typeA);
      NpcRelationship relB = profileB.getOrCreateRelationship(a.getUuid(), typeB);
      if (relA.type() == NpcRelationshipType.UNKNOWN || relA.type() == NpcRelationshipType.ACQUAINTANCE) {
         relA.setType(typeA);
      }
      if (relB.type() == NpcRelationshipType.UNKNOWN || relB.type() == NpcRelationshipType.ACQUAINTANCE) {
         relB.setType(typeB);
      }

      int famBoost = 2 + profileA.sociability() / 50;
      relA.adjust(famBoost, 1, 1, 0, 0, now);
      relB.adjust(famBoost, 1, 1, 0, 0, now);
      profileA.setLastSocialInteractionGameTime(now);
      profileB.setLastSocialInteractionGameTime(now);

      transferEligibleKnowledge(profileA, profileB, a.getUuid(), now);
      transferEligibleKnowledge(profileB, profileA, b.getUuid(), now);

      int retention = MillenaireServerConfig.SERVER.npcRelationshipRetention.getAsInt();
      int knowCap = MillenaireServerConfig.SERVER.npcKnowledgeRetentionLimit.getAsInt();
      profileA.pruneRelationships(retention, now, 24000L * 60L);
      profileB.pruneRelationships(retention, now, 24000L * 60L);
      profileA.pruneKnowledge(now, 24000L * 45L, knowCap);
      profileB.pruneKnowledge(now, 24000L * 45L, knowCap);

      data.setDirty();

      LivingEventService.emit(
         level,
         LivingEventType.SOCIAL_INTERACTION,
         LivingEventSource.SOCIAL_SERVICE,
         village,
         a.getUuid(),
         b.getUuid(),
         a.getFirstName() + " spoke with " + b.getFirstName(),
         "public",
         "social:" + orderedKey(a.getUuid(), b.getUuid()) + ":" + (now / Math.max(1, cooldown))
      );
   }

   private static void transferEligibleKnowledge(NpcSocialProfile sender, NpcSocialProfile receiver, UUID senderId, long now) {
      List<NpcKnowledgeItem> copy = new ArrayList<>(sender.knowledge());
      int transferred = 0;
      for (NpcKnowledgeItem item : copy) {
         if (transferred >= 2) {
            break;
         }
         if (item.category() == NpcKnowledgeCategory.PRIVATE) {
            continue;
         }
         if (item.isExpired(now)) {
            continue;
         }
         if (item.hopCount() >= MAX_KNOWLEDGE_HOPS) {
            continue;
         }
         // Require some familiarity/trust before sharing non-public needs casually.
         NpcRelationship rel = receiver.getRelationship(senderId);
         if (item.category() == NpcKnowledgeCategory.PUBLIC_NEED || item.category() == NpcKnowledgeCategory.SECURITY_THREAT) {
            // Public village facts transfer more freely.
         } else if (rel == null || rel.familiarity() < 10 || rel.trust() < -20) {
            continue;
         }
         NpcKnowledgeItem shared = item.transferTo(senderId, now, MAX_KNOWLEDGE_HOPS);
         if (shared != null) {
            receiver.addKnowledge(shared, MillenaireServerConfig.SERVER.npcKnowledgeRetentionLimit.getAsInt());
            transferred++;
         }
      }
   }

   private static NpcRelationshipType inferType(VillagerRecord self, VillagerRecord other) {
      String selfName = (self.getFirstName() + " " + self.getFamilyName()).trim().toLowerCase(Locale.ROOT);
      String otherName = (other.getFirstName() + " " + other.getFamilyName()).trim().toLowerCase(Locale.ROOT);
      String spouse = nullToEmpty(self.getIdentity().getSpousesName()).toLowerCase(Locale.ROOT);
      String father = nullToEmpty(self.getIdentity().getFathersName()).toLowerCase(Locale.ROOT);
      String mother = nullToEmpty(self.getIdentity().getMothersName()).toLowerCase(Locale.ROOT);
      String otherSpouse = nullToEmpty(other.getIdentity().getSpousesName()).toLowerCase(Locale.ROOT);
      String otherFather = nullToEmpty(other.getIdentity().getFathersName()).toLowerCase(Locale.ROOT);
      String otherMother = nullToEmpty(other.getIdentity().getMothersName()).toLowerCase(Locale.ROOT);

      if (!spouse.isBlank() && (spouse.equals(otherName) || spouse.equals(other.getFirstName().toLowerCase(Locale.ROOT)))) {
         return NpcRelationshipType.FAMILY_SPOUSE;
      }
      if (!otherSpouse.isBlank() && (otherSpouse.equals(selfName) || otherSpouse.equals(self.getFirstName().toLowerCase(Locale.ROOT)))) {
         return NpcRelationshipType.FAMILY_SPOUSE;
      }
      if (!father.isBlank() && fatherEquals(father, other) || !mother.isBlank() && motherEquals(mother, other)) {
         return NpcRelationshipType.FAMILY_PARENT;
      }
      if (!otherFather.isBlank() && fatherEquals(otherFather, self) || !otherMother.isBlank() && motherEquals(otherMother, self)) {
         return NpcRelationshipType.FAMILY_CHILD;
      }
      if (self.getHomeBuilding() != null && self.getHomeBuilding().equals(other.getHomeBuilding())) {
         return NpcRelationshipType.HOUSEHOLD;
      }
      if (self.getFamilyName() != null && self.getFamilyName().equalsIgnoreCase(other.getFamilyName()) && !self.getFamilyName().isBlank()) {
         return NpcRelationshipType.HOUSEHOLD;
      }
      return NpcRelationshipType.ACQUAINTANCE;
   }

   private static boolean fatherEquals(String father, VillagerRecord other) {
      String full = (other.getFirstName() + " " + other.getFamilyName()).trim().toLowerCase(Locale.ROOT);
      return father.equals(full) || father.equals(other.getFirstName().toLowerCase(Locale.ROOT));
   }

   private static boolean motherEquals(String mother, VillagerRecord other) {
      return fatherEquals(mother, other);
   }

   private static String nullToEmpty(@Nullable String s) {
      return s == null ? "" : s;
   }

   private static String orderedKey(UUID a, UUID b) {
      return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcSocialEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
