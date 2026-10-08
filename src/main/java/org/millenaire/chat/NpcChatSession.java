package org.millenaire.chat;

import java.util.UUID;

public final class NpcChatSession {
   private final UUID playerId;
   private final UUID villagerUuid;
   private final int entityId;
   private boolean pendingRequest;

   public NpcChatSession(UUID playerId, UUID villagerUuid, int entityId) {
      this.playerId = playerId;
      this.villagerUuid = villagerUuid;
      this.entityId = entityId;
   }

   public UUID playerId() {
      return this.playerId;
   }

   public UUID villagerUuid() {
      return this.villagerUuid;
   }

   public int entityId() {
      return this.entityId;
   }

   public boolean pendingRequest() {
      return this.pendingRequest;
   }

   public void setPendingRequest(boolean pendingRequest) {
      this.pendingRequest = pendingRequest;
   }
}
