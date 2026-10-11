package org.millenaire.religion.policy;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.religion.model.ReligionPolicy;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;

public final class ReligionPolicyService {
   public ReligionPolicyService() {
   }

   public boolean allowsCommunal(VillageFaithCommunity community, ResourceLocation traditionId) {
      if (community == null || traditionId == null) {
         return false;
      }
      return switch (community.policy()) {
         case OPEN -> community.traditionIds().contains(traditionId);
         case HOST_PREFERRED, HOST_ONLY ->
            community.hostTraditionId() == null || community.hostTraditionId().equals(traditionId);
      };
   }

   public boolean allowsPrivatePractice(VillageFaithCommunity community, ResourceLocation traditionId) {
      if (community == null || traditionId == null) {
         return false;
      }
      // Private practice always allowed for present traditions, even under HOST_ONLY.
      return community.traditionIds().contains(traditionId);
   }

   public String setPolicy(ServerLevel level, Village village, ServerPlayer player, ReligionPolicy policy) {
      if (!playerMaySetPolicy(village, player)) {
         return "religion.millenaire.error.not_authorized";
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      community.setPolicy(policy);
      community.addHistory("policy:" + policy.id() + ":" + player.getUUID());
      data.setDirty();
      return "religion.millenaire.policy.updated";
   }

   private boolean playerMaySetPolicy(Village village, ServerPlayer player) {
      UUID owner = village.getOwnerUUID();
      if (owner != null && owner.equals(player.getUUID())) {
         return true;
      }
      return player.hasPermissions(2);
   }
}
