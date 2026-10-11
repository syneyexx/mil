package org.millenaire.perf.compute;

import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/**
 * Pure worker work unit. Must not touch ServerLevel, entities, BlockEntities, or SavedData.
 */
@FunctionalInterface
public interface ComputeJob extends Callable<ComputeResult> {
   @Override
   @Nullable
   ComputeResult call() throws Exception;
}
