package org.millenaire.companion.capability;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.companion.CompanionIntent;
import org.millenaire.entity.MillVillager;

public interface CompanionCapability {
   String id();

   boolean isAvailable();

   /** Short verified lines for the LM prompt. */
   List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion);

   /** Execute a capability intent; null if this module does not handle the type. */
   @Nullable
   String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent);
}
