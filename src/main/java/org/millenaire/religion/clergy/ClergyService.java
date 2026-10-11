package org.millenaire.religion.clergy;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.ReligionDefinition;
import org.millenaire.religion.content.ReligionRoleDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class ClergyService {
   public ClergyService() {
   }

   public List<VillagerRecord> findClergy(ServerLevel level, Village village, ResourceLocation traditionId) {
      List<VillagerRecord> out = new ArrayList<>();
      ReligionDefinition def = ReligionContentRegistry.traditionOrNull(traditionId);
      if (def == null) {
         return out;
      }
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled()) {
            continue;
         }
         NpcFaithService.ensureInitialized(level, village, record);
         if (!matchesClergy(record, def)) {
            continue;
         }
         out.add(record);
      }
      return out;
   }

   @Nullable
   public VillagerRecord findLeader(ServerLevel level, Village village, ResourceLocation traditionId, List<String> requiredRoles) {
      ReligionDefinition def = ReligionContentRegistry.traditionOrNull(traditionId);
      if (def == null) {
         return null;
      }
      VillagerRecord best = null;
      int bestDevotion = -1;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled()) {
            continue;
         }
         NpcFaithProfile profile = NpcFaithService.ensureInitialized(level, village, record);
         if (!profile.hasTradition(traditionId)) {
            continue;
         }
         if (!canLead(record, def, requiredRoles)) {
            continue;
         }
         int devotion = profile.primaryAffiliation() != null ? profile.primaryAffiliation().devotion() : 0;
         if (devotion > bestDevotion) {
            bestDevotion = devotion;
            best = record;
         }
      }
      return best;
   }

   public boolean isClergy(ServerLevel level, Village village, VillagerRecord record) {
      NpcFaithProfile profile = NpcFaithService.ensureInitialized(level, village, record);
      if (profile.primaryAffiliation() == null) {
         return false;
      }
      ReligionDefinition def = ReligionContentRegistry.traditionOrNull(profile.primaryAffiliation().traditionId());
      return def != null && matchesClergy(record, def);
   }

   private static boolean matchesClergy(VillagerRecord record, ReligionDefinition def) {
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      List<String> tags = type != null ? type.tags() : List.of();
      for (ReligionRoleDefinition role : def.roles()) {
         if (role.clergy() && role.matches(record.getVillagerTypeId(), tags, record.getRoleName())) {
            return true;
         }
      }
      return false;
   }

   private static boolean canLead(VillagerRecord record, ReligionDefinition def, List<String> requiredRoles) {
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      List<String> tags = type != null ? type.tags() : List.of();
      for (ReligionRoleDefinition role : def.roles()) {
         if (!role.canLeadCeremony()) {
            continue;
         }
         if (!role.matches(record.getVillagerTypeId(), tags, record.getRoleName())) {
            continue;
         }
         if (requiredRoles == null || requiredRoles.isEmpty() || requiredRoles.stream().anyMatch(r -> r.equalsIgnoreCase(role.roleId()))) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   public String clergyRoleLabel(VillagerRecord record, ReligionDefinition def) {
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      List<String> tags = type != null ? type.tags() : List.of();
      for (ReligionRoleDefinition role : def.roles()) {
         if (role.clergy() && role.matches(record.getVillagerTypeId(), tags, record.getRoleName())) {
            return role.displayNameKey() != null ? role.displayNameKey() : "religion.millenaire.role." + role.roleId();
         }
      }
      return null;
   }
}
