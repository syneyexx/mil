package org.millenaire.labyrinth;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.millenaire.labyrinth.client.LabyrinthSounds;
import org.millenaire.labyrinth.gen.LabyrinthChunkGenerator;

/**
 * Registers labyrinth worldgen codecs (chunk generator) and presence sound events.
 */
public final class LabyrinthBootstrap {
   public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
      DeferredRegister.create(Registries.CHUNK_GENERATOR, LabyrinthKeys.MODID);

   public static final Supplier<MapCodec<? extends ChunkGenerator>> LABYRINTH_CHUNK_GENERATOR =
      CHUNK_GENERATORS.register("labyrinth", () -> LabyrinthChunkGenerator.CODEC);

   public static void register(IEventBus modEventBus) {
      CHUNK_GENERATORS.register(modEventBus);
      LabyrinthSounds.SOUND_EVENTS.register(modEventBus);
   }

   private LabyrinthBootstrap() {
   }
}
