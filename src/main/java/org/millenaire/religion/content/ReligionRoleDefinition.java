package org.millenaire.religion.content;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public record ReligionRoleDefinition(
   String roleId,
   List<String> villagerTags,
   List<String> typePathContains,
   boolean clergy,
   boolean canLeadCeremony,
   @Nullable String displayNameKey
) {
   public ReligionRoleDefinition {
      villagerTags = villagerTags == null ? List.of() : List.copyOf(villagerTags);
      typePathContains = typePathContains == null ? List.of() : List.copyOf(typePathContains);
   }

   public boolean matches(@Nullable ResourceLocation villagerTypeId, @Nullable List<String> tags, @Nullable String roleName) {
      if (typePathContains != null) {
         String path = villagerTypeId != null ? villagerTypeId.getPath().toLowerCase() : "";
         String role = roleName != null ? roleName.toLowerCase() : "";
         for (String frag : typePathContains) {
            if (frag != null && !frag.isEmpty()) {
               String f = frag.toLowerCase();
               if (path.contains(f) || role.contains(f)) {
                  return true;
               }
            }
         }
      }
      if (tags != null && villagerTags != null) {
         for (String needed : villagerTags) {
            if (needed != null && tags.stream().anyMatch(t -> t != null && t.equalsIgnoreCase(needed))) {
               return true;
            }
         }
      }
      return false;
   }
}
