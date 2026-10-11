package org.millenaire.worldboss;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.millenaire.worldboss.spawn.WorldBossSpawnStrategy;

/**
 * Immutable registration record for a world-boss type. The event planner never hardcodes a single boss.
 */
public final class WorldBossDefinition {
   private final ResourceLocation id;
   private final Supplier<EntityType<? extends Mob>> entityType;
   private final int weight;
   private final Predicate<ServerLevel> availability;
   private final WorldBossSpawnStrategy spawnStrategy;
   private final String bossbarNameKey;
   private final BossBarColor bossBarColor;
   private final BossBarOverlay bossBarOverlay;
   private final int neutralDwellTicks;
   private final int combatTimeoutTicks;
   private final int announceTicks;
   private final int arriveTicks;
   private final ResourceLocation lootTable;
   private final ResourceLocation advancementId;
   private final String announceKey;
   private final String arriveKey;
   private final String departKey;
   private final String engageKey;
   private final String defeatKey;

   private WorldBossDefinition(Builder builder) {
      this.id = Objects.requireNonNull(builder.id, "id");
      this.entityType = Objects.requireNonNull(builder.entityType, "entityType");
      this.weight = Math.max(1, builder.weight);
      this.availability = builder.availability != null ? builder.availability : level -> true;
      this.spawnStrategy = Objects.requireNonNull(builder.spawnStrategy, "spawnStrategy");
      this.bossbarNameKey = builder.bossbarNameKey != null ? builder.bossbarNameKey : "entity.millenaire." + id.getPath();
      this.bossBarColor = builder.bossBarColor != null ? builder.bossBarColor : BossBarColor.YELLOW;
      this.bossBarOverlay = builder.bossBarOverlay != null ? builder.bossBarOverlay : BossBarOverlay.PROGRESS;
      this.neutralDwellTicks = builder.neutralDwellTicks;
      this.combatTimeoutTicks = builder.combatTimeoutTicks;
      this.announceTicks = builder.announceTicks;
      this.arriveTicks = builder.arriveTicks;
      this.lootTable = builder.lootTable;
      this.advancementId = builder.advancementId;
      this.announceKey = builder.announceKey;
      this.arriveKey = builder.arriveKey;
      this.departKey = builder.departKey;
      this.engageKey = builder.engageKey;
      this.defeatKey = builder.defeatKey;
   }

   public ResourceLocation id() {
      return this.id;
   }

   public EntityType<? extends Mob> entityType() {
      return this.entityType.get();
   }

   public int weight() {
      return this.weight;
   }

   public boolean isAvailable(ServerLevel level) {
      return this.availability.test(level);
   }

   public WorldBossSpawnStrategy spawnStrategy() {
      return this.spawnStrategy;
   }

   public String bossbarNameKey() {
      return this.bossbarNameKey;
   }

   public BossBarColor bossBarColor() {
      return this.bossBarColor;
   }

   public BossBarOverlay bossBarOverlay() {
      return this.bossBarOverlay;
   }

   public ServerBossEvent createBossBar() {
      return new ServerBossEvent(
         net.minecraft.network.chat.Component.translatable(this.bossbarNameKey),
         this.bossBarColor,
         this.bossBarOverlay
      );
   }

   public int neutralDwellTicks() {
      return this.neutralDwellTicks;
   }

   public int combatTimeoutTicks() {
      return this.combatTimeoutTicks;
   }

   public int announceTicks() {
      return this.announceTicks;
   }

   public int arriveTicks() {
      return this.arriveTicks;
   }

   @Nullable
   public ResourceLocation lootTable() {
      return this.lootTable;
   }

   @Nullable
   public ResourceLocation advancementId() {
      return this.advancementId;
   }

   public String announceKey() {
      return this.announceKey;
   }

   public String arriveKey() {
      return this.arriveKey;
   }

   public String departKey() {
      return this.departKey;
   }

   public String engageKey() {
      return this.engageKey;
   }

   public String defeatKey() {
      return this.defeatKey;
   }

   public boolean canStartNear(ServerLevel level, ServerPlayer player) {
      return this.spawnStrategy.canConsider(level, player);
   }

   public static Builder builder(ResourceLocation id) {
      return new Builder(id);
   }

   public static final class Builder {
      private final ResourceLocation id;
      private Supplier<EntityType<? extends Mob>> entityType;
      private int weight = 100;
      private Predicate<ServerLevel> availability;
      private WorldBossSpawnStrategy spawnStrategy;
      private String bossbarNameKey;
      private BossBarColor bossBarColor;
      private BossBarOverlay bossBarOverlay;
      private int neutralDwellTicks = 6000;
      private int combatTimeoutTicks = 12000;
      private int announceTicks = 80;
      private int arriveTicks = 100;
      private ResourceLocation lootTable;
      private ResourceLocation advancementId;
      private String announceKey = "millenaire.worldboss.announce";
      private String arriveKey = "millenaire.worldboss.arrive";
      private String departKey = "millenaire.worldboss.depart";
      private String engageKey = "millenaire.worldboss.engage";
      private String defeatKey = "millenaire.worldboss.defeat";

      private Builder(ResourceLocation id) {
         this.id = id;
      }

      public Builder entityType(Supplier<EntityType<? extends Mob>> entityType) {
         this.entityType = entityType;
         return this;
      }

      public Builder weight(int weight) {
         this.weight = weight;
         return this;
      }

      public Builder availability(Predicate<ServerLevel> availability) {
         this.availability = availability;
         return this;
      }

      public Builder spawnStrategy(WorldBossSpawnStrategy spawnStrategy) {
         this.spawnStrategy = spawnStrategy;
         return this;
      }

      public Builder bossbarNameKey(String key) {
         this.bossbarNameKey = key;
         return this;
      }

      public Builder bossBar(BossBarColor color, BossBarOverlay overlay) {
         this.bossBarColor = color;
         this.bossBarOverlay = overlay;
         return this;
      }

      public Builder neutralDwellTicks(int ticks) {
         this.neutralDwellTicks = ticks;
         return this;
      }

      public Builder combatTimeoutTicks(int ticks) {
         this.combatTimeoutTicks = ticks;
         return this;
      }

      public Builder announceTicks(int ticks) {
         this.announceTicks = ticks;
         return this;
      }

      public Builder arriveTicks(int ticks) {
         this.arriveTicks = ticks;
         return this;
      }

      public Builder lootTable(ResourceLocation lootTable) {
         this.lootTable = lootTable;
         return this;
      }

      public Builder advancementId(ResourceLocation advancementId) {
         this.advancementId = advancementId;
         return this;
      }

      public Builder messages(String announce, String arrive, String depart, String engage, String defeat) {
         this.announceKey = announce;
         this.arriveKey = arrive;
         this.departKey = depart;
         this.engageKey = engage;
         this.defeatKey = defeat;
         return this;
      }

      public WorldBossDefinition build() {
         return new WorldBossDefinition(this);
      }
   }
}
