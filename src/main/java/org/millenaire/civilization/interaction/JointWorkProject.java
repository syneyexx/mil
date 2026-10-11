package org.millenaire.civilization.interaction;

public record JointWorkProject(
   String id,
   String factionA,
   String factionB,
   Kind kind,
   int progress,
   Status status
) {
   public enum Kind {
      ROAD,
      BRIDGE,
      POST
   }

   public enum Status {
      PROPOSED,
      ACTIVE,
      COMPLETE,
      CANCELLED
   }

   public JointWorkProject withProgress(int next) {
      return new JointWorkProject(id, factionA, factionB, kind, Math.max(0, Math.min(100, next)), status);
   }

   public JointWorkProject withStatus(Status next) {
      return new JointWorkProject(id, factionA, factionB, kind, progress, next);
   }
}
