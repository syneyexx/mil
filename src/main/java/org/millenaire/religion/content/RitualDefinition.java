package org.millenaire.religion.content;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.religion.model.RitualKind;

public record RitualDefinition(
   ResourceLocation id,
   RitualKind kind,
   String nameKey,
   String descriptionKey,
   ResourceLocation traditionId,
   List<String> locationTags,
   List<String> requiredLeaderRoles,
   boolean requiresLeader,
   List<SupplyRequirement> supplies,
   int minParticipants,
   int maxParticipants,
   int gatherTicks,
   int activeTicks,
   int cooldownDays,
   int priority,
   int dayModulo,
   int dayOffset,
   int minimumHour,
   int maximumHour,
   boolean alignWithFestival,
   @Nullable String soundEventId,
   @Nullable String particleId,
   List<String> heldItemIds,
   String effectsProfile
) {
   public RitualDefinition {
      locationTags = locationTags == null ? List.of() : List.copyOf(locationTags);
      requiredLeaderRoles = requiredLeaderRoles == null ? List.of() : List.copyOf(requiredLeaderRoles);
      supplies = supplies == null ? List.of() : List.copyOf(supplies);
      heldItemIds = heldItemIds == null ? List.of() : List.copyOf(heldItemIds);
      effectsProfile = effectsProfile == null || effectsProfile.isEmpty() ? "default_communal" : effectsProfile;
      minParticipants = Math.max(1, minParticipants);
      maxParticipants = Math.max(minParticipants, maxParticipants);
      gatherTicks = Math.max(100, gatherTicks);
      activeTicks = Math.max(100, activeTicks);
      cooldownDays = Math.max(0, cooldownDays);
   }

   public boolean matchesLocationTag(String tag) {
      if (tag == null || tag.isEmpty()) {
         return false;
      }
      for (String t : locationTags) {
         if (tag.equalsIgnoreCase(t)) {
            return true;
         }
      }
      return false;
   }
}
