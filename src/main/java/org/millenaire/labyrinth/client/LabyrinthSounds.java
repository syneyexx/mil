package org.millenaire.labyrinth.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Labyrinth presence audio events (vanilla placeholders; resource-pack replaceable).
 * See {@code docs/LABYRINTH_AUDIO_SLOTS.md}.
 */
public final class LabyrinthSounds {
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
      DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, LabyrinthKeys.MODID);

   public static final DeferredHolder<SoundEvent, SoundEvent> DRONE = register("labyrinth.drone");
   public static final DeferredHolder<SoundEvent, SoundEvent> METAL = register("labyrinth.metal");
   public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = register("labyrinth.whisper");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_STINGER = register("labyrinth.chase_stinger");
   public static final DeferredHolder<SoundEvent, SoundEvent> RUIT_DRONE = register("labyrinth.ruit_drone");

   private LabyrinthSounds() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, path);
      return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
   }
}
