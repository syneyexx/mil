package org.millenaire.religion.content;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public record ReligionDefinition(
   ResourceLocation id,
   String nameKey,
   String descriptionKey,
   List<String> cultureIds,
   List<ReligionRoleDefinition> roles,
   List<String> locationTags,
   List<ResourceLocation> dailyRitualIds,
   List<ResourceLocation> serviceRitualIds,
   List<ResourceLocation> feastRitualIds,
   List<ResourceLocation> memorialRitualIds,
   List<ResourceLocation> familyRitualIds,
   List<ResourceLocation> consecrationRitualIds,
   List<ResourceLocation> pilgrimageRitualIds,
   boolean allowsMultipleAffiliations,
   int defaultDevotion,
   @Nullable String primaryClergyRole
) {
   public ReligionDefinition {
      cultureIds = cultureIds == null ? List.of() : List.copyOf(cultureIds);
      roles = roles == null ? List.of() : List.copyOf(roles);
      locationTags = locationTags == null ? List.of() : List.copyOf(locationTags);
      dailyRitualIds = dailyRitualIds == null ? List.of() : List.copyOf(dailyRitualIds);
      serviceRitualIds = serviceRitualIds == null ? List.of() : List.copyOf(serviceRitualIds);
      feastRitualIds = feastRitualIds == null ? List.of() : List.copyOf(feastRitualIds);
      memorialRitualIds = memorialRitualIds == null ? List.of() : List.copyOf(memorialRitualIds);
      familyRitualIds = familyRitualIds == null ? List.of() : List.copyOf(familyRitualIds);
      consecrationRitualIds = consecrationRitualIds == null ? List.of() : List.copyOf(consecrationRitualIds);
      pilgrimageRitualIds = pilgrimageRitualIds == null ? List.of() : List.copyOf(pilgrimageRitualIds);
      defaultDevotion = Math.max(0, Math.min(100, defaultDevotion));
   }

   public boolean supportsCulture(String cultureKey) {
      if (cultureKey == null || cultureKey.isEmpty()) {
         return false;
      }
      String key = cultureKey.contains(":") ? ResourceLocation.parse(cultureKey).getPath() : cultureKey;
      for (String c : cultureIds) {
         if (c != null && (c.equalsIgnoreCase(key) || c.equalsIgnoreCase(cultureKey))) {
            return true;
         }
      }
      return false;
   }

   public Set<ResourceLocation> allRitualIds() {
      return Set.copyOf(
         java.util.stream.Stream.of(
               dailyRitualIds, serviceRitualIds, feastRitualIds, memorialRitualIds,
               familyRitualIds, consecrationRitualIds, pilgrimageRitualIds
            )
            .flatMap(List::stream)
            .toList()
      );
   }

   @Nullable
   public ReligionRoleDefinition findRole(String roleId) {
      if (roleId == null) {
         return null;
      }
      for (ReligionRoleDefinition role : roles) {
         if (roleId.equalsIgnoreCase(role.roleId())) {
            return role;
         }
      }
      return null;
   }
}
