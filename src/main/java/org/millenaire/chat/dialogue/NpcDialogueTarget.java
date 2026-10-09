package org.millenaire.chat.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Validated non-player dialogue participant resolved on the server.
 */
public final class NpcDialogueTarget {
   private final LivingEntity entity;
   private final NpcDialogueAdapter adapter;
   private final NpcDialogueIdentity identity;
   private final NpcDialoguePolicy policy;
   private final NpcDialogueCategory category;

   public NpcDialogueTarget(LivingEntity entity, NpcDialogueAdapter adapter, NpcDialogueIdentity identity, NpcDialoguePolicy policy) {
      this.entity = entity;
      this.adapter = adapter;
      this.identity = identity;
      this.policy = policy;
      this.category = adapter.category();
   }

   public LivingEntity entity() {
      return this.entity;
   }

   public NpcDialogueAdapter adapter() {
      return this.adapter;
   }

   public NpcDialogueIdentity identity() {
      return this.identity;
   }

   public NpcDialoguePolicy policy() {
      return this.policy;
   }

   public NpcDialogueCategory category() {
      return this.category;
   }

   public int entityId() {
      return this.entity.getId();
   }

   public boolean isAlive() {
      return this.entity.isAlive();
   }

   public boolean sameLevelAs(ServerPlayer player) {
      return this.entity.level() == player.level();
   }
}
