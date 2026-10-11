package org.millenaire.worldboss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * In-memory registry of world-boss definitions. Not hardcoded to a single boss.
 */
public final class WorldBossDefinitionRegistry {
   private static final WorldBossDefinitionRegistry INSTANCE = new WorldBossDefinitionRegistry();

   private final Map<ResourceLocation, WorldBossDefinition> definitions = new LinkedHashMap<>();
   private boolean frozen;

   private WorldBossDefinitionRegistry() {
   }

   public static WorldBossDefinitionRegistry get() {
      return INSTANCE;
   }

   public void register(WorldBossDefinition definition) {
      if (this.frozen) {
         throw new IllegalStateException("WorldBossDefinitionRegistry is frozen");
      }
      if (this.definitions.putIfAbsent(definition.id(), definition) != null) {
         throw new IllegalStateException("Duplicate world boss definition: " + definition.id());
      }
   }

   public void freeze() {
      this.frozen = true;
   }

   public boolean isFrozen() {
      return this.frozen;
   }

   public Optional<WorldBossDefinition> get(ResourceLocation id) {
      return Optional.ofNullable(this.definitions.get(id));
   }

   @Nullable
   public WorldBossDefinition getOrNull(ResourceLocation id) {
      return this.definitions.get(id);
   }

   public Collection<WorldBossDefinition> all() {
      return Collections.unmodifiableCollection(this.definitions.values());
   }

   public List<WorldBossDefinition> available(ServerLevel level) {
      List<WorldBossDefinition> out = new ArrayList<>();
      for (WorldBossDefinition def : this.definitions.values()) {
         if (def.isAvailable(level)) {
            out.add(def);
         }
      }
      return out;
   }

   /**
    * Weighted selection among available definitions. Avoids immediate repeat when possible.
    */
   public Optional<WorldBossDefinition> select(ServerLevel level, @Nullable ResourceLocation lastBossId, RandomSource random) {
      List<WorldBossDefinition> available = available(level);
      if (available.isEmpty()) {
         return Optional.empty();
      }
      if (available.size() == 1) {
         return Optional.of(available.getFirst());
      }
      WorldBossDefinition picked = weightedPick(available, random);
      if (lastBossId != null && lastBossId.equals(picked.id())) {
         List<WorldBossDefinition> others = available.stream().filter(d -> !d.id().equals(lastBossId)).toList();
         if (!others.isEmpty()) {
            picked = weightedPick(others, random);
         }
      }
      return Optional.of(picked);
   }

   public Optional<WorldBossDefinition> select(ServerLevel level, @Nullable ResourceLocation lastBossId) {
      return select(level, lastBossId, RandomSource.create(ThreadLocalRandom.current().nextLong()));
   }

   static WorldBossDefinition weightedPick(List<WorldBossDefinition> defs, RandomSource random) {
      int total = 0;
      for (WorldBossDefinition def : defs) {
         total += Math.max(1, effectiveWeight(def));
      }
      int roll = random.nextInt(Math.max(1, total));
      int cursor = 0;
      for (WorldBossDefinition def : defs) {
         cursor += Math.max(1, effectiveWeight(def));
         if (roll < cursor) {
            return def;
         }
      }
      return defs.getLast();
   }

   static int effectiveWeight(WorldBossDefinition def) {
      try {
         if ("hemelwachter".equals(def.id().getPath())) {
            return org.millenaire.config.MillenaireServerConfig.SERVER.worldBossHemelwachterWeight.getAsInt();
         }
      } catch (IllegalStateException ignored) {
      }
      return def.weight();
   }
}
