package org.millenaire.living.context;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.living.social.NpcRelationship;
import org.millenaire.living.social.NpcSocialProfile;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.village.VillagerRecord;

public final class NpcSocialContextProvider {
   private NpcSocialContextProvider() {
   }

   public static void appendSocial(ServerLevel level, VillagerRecord record, ServerPlayer player, DialogueGameplayContext ctx) {
      if (record == null || ctx == null) {
         return;
      }
      NpcSocialProfile profile = NpcSocialService.profile(level, record.getUuid());
      NpcSocialService.ensurePersonality(profile, record);
      ctx.addVerified(
         "personality sociability="
            + profile.sociability()
            + " caution="
            + profile.caution()
            + " generosity="
            + profile.generosity()
            + " ambition="
            + profile.ambition()
            + " patience="
            + profile.patience()
            + " loyalty="
            + profile.loyalty()
      );
      ctx.addVerified("mood=" + profile.mood());
      if (player != null) {
         // Player relationship uses existing NpcMind opinions; social profile is NPC-NPC.
         ctx.addVerified("playerUuid=" + player.getUUID());
      }
      int shown = 0;
      for (NpcRelationship rel : profile.relationships().values()) {
         if (shown >= 4) {
            break;
         }
         if (rel.familiarity() < 15 && rel.type().name().startsWith("FAMILY") == false) {
            continue;
         }
         ctx.addFirsthand(
            "relationTo="
               + rel.toId()
               + " type="
               + rel.type().name()
               + " familiarity="
               + rel.familiarity()
               + " trust="
               + rel.trust()
         );
         shown++;
      }
   }
}
