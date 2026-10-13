package org.millenaire.world.road;

/**
 * Height/biome flag access used by route planning workers.
 * Implementations must not require the Minecraft server thread.
 */
public interface TerrainView {
   int height(int x, int z);

   int flags(int x, int z);
}
