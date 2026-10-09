package org.millenaire.chat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.millenaire.chat.dialogue.NpcDialogueCategory;

/**
 * Server-authoritative open dialogue. Sessions are created only by authorized open handlers.
 */
public final class NpcChatSession {
   private static final AtomicLong NEXT_SESSION_ID = new AtomicLong(1L);

   private final long sessionId;
   private final UUID playerId;
   private final UUID targetUuid;
   private final int entityId;
   private final NpcDialogueCategory category;
   private final boolean persistMind;
   private final boolean canGossip;
   private final boolean stopNavigation;
   private final boolean lookAtPlayer;
   private final boolean bossNonIntrusive;
   private volatile boolean pendingRequest;
   private volatile long requestGeneration;
   private volatile long openedGameTime;

   public NpcChatSession(
      UUID playerId,
      UUID targetUuid,
      int entityId,
      NpcDialogueCategory category,
      boolean persistMind,
      boolean canGossip,
      boolean stopNavigation,
      boolean lookAtPlayer,
      boolean bossNonIntrusive,
      long openedGameTime
   ) {
      this.sessionId = NEXT_SESSION_ID.getAndIncrement();
      this.playerId = playerId;
      this.targetUuid = targetUuid;
      this.entityId = entityId;
      this.category = category;
      this.persistMind = persistMind;
      this.canGossip = canGossip;
      this.stopNavigation = stopNavigation;
      this.lookAtPlayer = lookAtPlayer;
      this.bossNonIntrusive = bossNonIntrusive;
      this.openedGameTime = openedGameTime;
   }

   public long sessionId() {
      return this.sessionId;
   }

   public UUID playerId() {
      return this.playerId;
   }

   /** @deprecated use {@link #targetUuid()} */
   @Deprecated
   public UUID villagerUuid() {
      return this.targetUuid;
   }

   public UUID targetUuid() {
      return this.targetUuid;
   }

   public int entityId() {
      return this.entityId;
   }

   public NpcDialogueCategory category() {
      return this.category;
   }

   public boolean persistMind() {
      return this.persistMind;
   }

   public boolean canGossip() {
      return this.canGossip;
   }

   public boolean stopNavigation() {
      return this.stopNavigation;
   }

   public boolean lookAtPlayer() {
      return this.lookAtPlayer;
   }

   public boolean bossNonIntrusive() {
      return this.bossNonIntrusive;
   }

   public boolean pendingRequest() {
      return this.pendingRequest;
   }

   public void setPendingRequest(boolean pendingRequest) {
      this.pendingRequest = pendingRequest;
   }

   public long requestGeneration() {
      return this.requestGeneration;
   }

   public long beginRequest() {
      this.pendingRequest = true;
      return ++this.requestGeneration;
   }

   public void endRequest(long generation) {
      if (this.requestGeneration == generation) {
         this.pendingRequest = false;
      }
   }

   public void cancelPending() {
      this.requestGeneration++;
      this.pendingRequest = false;
   }

   public long openedGameTime() {
      return this.openedGameTime;
   }

   public boolean matches(UUID playerId, UUID targetUuid, int entityId) {
      return this.playerId.equals(playerId) && this.targetUuid.equals(targetUuid) && this.entityId == entityId;
   }
}
