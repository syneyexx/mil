package org.millenaire.perf.compute;

import net.minecraft.server.level.ServerLevel;

/**
 * Immutable outcome of a worker job. Applied only on the Minecraft server thread.
 */
@FunctionalInterface
public interface ComputeResult {
   /**
    * Apply side effects (SavedData, village queues, etc.). Must not block on workers.
    *
    * @return true if the result was consumed; false if stale/ignored
    */
   boolean apply(ServerLevel level);
}
