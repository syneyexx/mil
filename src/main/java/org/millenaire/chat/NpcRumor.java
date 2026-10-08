package org.millenaire.chat;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.VillageId;

public record NpcRumor(
   long id,
   ResourceLocation cultureId,
   UUID originVillager,
   VillageId originVillage,
   UUID aboutPlayer,
   String playerName,
   String canonical,
   long createdGameTime,
   long createdDay,
   int importance,
   long supersededBy,
   boolean expired
) {
   public NpcRumor withSupersededBy(long newId) {
      return new NpcRumor(
         this.id,
         this.cultureId,
         this.originVillager,
         this.originVillage,
         this.aboutPlayer,
         this.playerName,
         this.canonical,
         this.createdGameTime,
         this.createdDay,
         this.importance,
         newId,
         this.expired
      );
   }

   public NpcRumor withExpired(boolean value) {
      return new NpcRumor(
         this.id,
         this.cultureId,
         this.originVillager,
         this.originVillage,
         this.aboutPlayer,
         this.playerName,
         this.canonical,
         this.createdGameTime,
         this.createdDay,
         this.importance,
         this.supersededBy,
         value
      );
   }

   public boolean isActive() {
      return !this.expired && this.supersededBy == 0L;
   }
}
