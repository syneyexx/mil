package org.millenaire.chat;

public record NpcOpinion(float trust, float fear, float affection) {
   public static NpcOpinion neutral() {
      return new NpcOpinion(0.0F, 0.0F, 0.0F);
   }

   public NpcOpinion withDelta(float dTrust, float dFear, float dAffection) {
      return new NpcOpinion(clamp(this.trust + dTrust), clamp(this.fear + dFear), clamp(this.affection + dAffection));
   }

   private static float clamp(float v) {
      return Math.max(-1.0F, Math.min(1.0F, v));
   }
}
