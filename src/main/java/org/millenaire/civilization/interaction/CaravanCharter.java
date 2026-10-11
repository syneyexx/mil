package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;

public record CaravanCharter(
   String playerFaction,
   String partnerFaction,
   @Nullable String exportItemId,
   boolean active,
   long createdAt
) {
}
