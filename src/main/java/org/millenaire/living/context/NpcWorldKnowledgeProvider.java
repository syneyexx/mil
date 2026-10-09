package org.millenaire.living.context;

import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.bridge.CivilizationContextProvider;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.behavior.VillageActivityCoordinator;
import org.millenaire.living.knowledge.NpcKnowledgeCategory;
import org.millenaire.living.knowledge.NpcKnowledgeItem;
import org.millenaire.living.needs.VillageNeedType;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.social.NpcSocialProfile;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Role-scoped knowledge visibility. NPCs are not omniscient.
 */
public final class NpcWorldKnowledgeProvider {
   private NpcWorldKnowledgeProvider() {
   }

   public static void appendRoleKnowledge(ServerLevel level, Village village, VillagerRecord record, DialogueGameplayContext ctx) {
      if (village == null || record == null || ctx == null) {
         return;
      }
      String role = record.getRoleName() == null ? "" : record.getRoleName().toLowerCase(Locale.ROOT);
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      boolean chief = role.contains("chief") || role.contains("leader");
      boolean builder = role.contains("builder") || role.contains("lumberman") || role.contains("carpenter");
      boolean farmer = role.contains("farmer") || role.contains("grower");
      boolean trader = role.contains("merchant") || role.contains("seller") || role.contains("trader");
      boolean guard = role.contains("guard") || (type != null && (type.isHelpInAttacks() || type.isDefensive()));
      boolean child = record.getIdentity().getChildSize() >= 0 || (type != null && type.isChild());

      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(level, village, false);

      if (chief) {
         VillageContextProvider.appendVerifiedVillageState(level, village, ctx);
         CivilizationContextProvider.appendVerified(level, village, ctx, record);
         String focus = VillageActivityCoordinator.chiefFocusLine(snap, village.isUnderAttack());
         if (focus != null) {
            ctx.addFirsthand("asChiefYouKnow=" + focus);
         }
         ctx.addActionHint("You may mention verified village needs and that the player can offer assistance through the game UI.");
         ctx.addActionHint("You may point to server action buttons for colonization status or housing priority when available.");
      } else if (trader) {
         CivilizationContextProvider.appendVerified(level, village, ctx, record);
         snap.firstOfType(VillageNeedType.TRADE_DISRUPTION).ifPresent(n -> ctx.addFirsthand("tradeIssue=" + n.summaryDetail()));
         snap.firstOfType(VillageNeedType.MERCHANT_OR_SUPPLY_UNAVAILABLE).ifPresent(n -> ctx.addFirsthand("supplyIssue=" + n.summaryDetail()));
         ctx.addUnknown("construction resource tallies unless told");
      } else if (builder) {
         CivilizationContextProvider.appendVerified(level, village, ctx, record);
         String line = VillageActivityCoordinator.builderFocusLine(snap);
         if (line != null) {
            ctx.addFirsthand(line);
         } else {
            ctx.addUnknown("current construction materials if you are not assigned to the project");
         }
         snap.firstOfType(VillageNeedType.CONSTRUCTION_STALLED).ifPresent(n -> ctx.addFirsthand("constructionStalled=" + n.summaryDetail()));
      } else if (guard) {
         String line = VillageActivityCoordinator.guardFocusLine(village.isUnderAttack(), snap);
         if (line != null) {
            ctx.addFirsthand(line);
         }
         ctx.addVerified("underAttack=" + village.isUnderAttack());
      } else if (farmer) {
         snap.firstOfType(VillageNeedType.VILLAGE_FOOD_SHORTAGE).ifPresent(n -> ctx.addFirsthand("foodPressure=" + n.severity().name()));
         ctx.addUnknown("distant construction details");
      } else if (child) {
         ctx.addFirsthand("familyFocus=true");
         ctx.addUnknown("village administration and exact resource counts");
         if (village.isUnderAttack()) {
            ctx.addFirsthand("youSenseDanger=true");
         }
      } else {
         // Ordinary resident: public needs at low detail + personal knowledge.
         CivilizationContextProvider.appendVerified(level, village, ctx, record);
         snap.mostSevere().ifPresent(n -> ctx.addSecondhand("heardVillageNeed=" + n.type().name() + ":" + n.summaryDetail()));
         ctx.addUnknown("exact missing material quantities");
      }

      NpcSocialProfile profile = NpcSocialService.profile(level, record.getUuid());
      for (NpcKnowledgeItem item : profile.knowledge()) {
         if (item.category() == NpcKnowledgeCategory.PRIVATE) {
            continue;
         }
         String line = item.category().name() + ":" + item.summary() + (item.firsthand() ? " [firsthand]" : " [hearsay hops=" + item.hopCount() + "]");
         if (item.firsthand()) {
            ctx.addFirsthand(line);
         } else if (item.category() == NpcKnowledgeCategory.RUMOR) {
            ctx.addRumor(line);
         } else {
            ctx.addSecondhand(line);
         }
      }
   }
}
