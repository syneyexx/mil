package org.millenaire.encyclopedia;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public record VillageData(
   String type,
   int radius,
   int weight,
   boolean playerControlled,
   boolean carriesRaid,
   VillageData.Population population,
   List<VillageData.RoleGroup> composition,
   @Nullable VillageData.Walls walls,
   @Nullable VillageData.Economy economy,
   @Nullable List<String> qualifiers,
   @Nullable Map<String, String> terrainQualifiers,
   @Nullable List<String> hamlets,
   @Nullable List<String> biomes
) {
   public record Building(String itemKey, int count, int residents) {
   }

   public record Economy(@Nullable Map<String, Integer> sellingOverrides, @Nullable Map<String, Integer> buyingOverrides) {
   }

   public record Population(int total, int male, int female) {
   }

   public record RoleGroup(String role, List<VillageData.Building> buildings) {
   }

   public record Walls(@Nullable String inner, @Nullable String outer, int innerRadius) {
   }
}
