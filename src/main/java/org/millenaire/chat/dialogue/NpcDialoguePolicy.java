package org.millenaire.chat.dialogue;

/**
 * Adapter-specific rules for how dialogue overlays entity behaviour.
 * Dialogue never globally rewrites vanilla AI; policies only affect optional soft cues.
 */
public record NpcDialoguePolicy(
   boolean allowDialogue,
   boolean stopNavigationWhileTalking,
   boolean lookAtPlayer,
   boolean persistMind,
   boolean canGossip,
   boolean bossNonIntrusive,
   NpcCommMode commMode,
   String refusalTranslationKey
) {
   public static NpcDialoguePolicy spoken(boolean stopNav, boolean persist, boolean gossip) {
      return new NpcDialoguePolicy(true, stopNav, true, persist, gossip, false, NpcCommMode.SPOKEN, null);
   }

   public static NpcDialoguePolicy limited(boolean persist) {
      return new NpcDialoguePolicy(true, false, true, persist, false, false, NpcCommMode.LIMITED, null);
   }

   public static NpcDialoguePolicy nonverbal(boolean persist) {
      return new NpcDialoguePolicy(true, false, true, persist, false, false, NpcCommMode.NONVERBAL, null);
   }

   public static NpcDialoguePolicy atmospheric() {
      return new NpcDialoguePolicy(true, false, false, false, false, false, NpcCommMode.ATMOSPHERIC, null);
   }

   public static NpcDialoguePolicy refused(String key) {
      return new NpcDialoguePolicy(false, false, false, false, false, false, NpcCommMode.SPOKEN, key);
   }

   public static NpcDialoguePolicy unsupported() {
      return new NpcDialoguePolicy(false, false, false, false, false, false, NpcCommMode.SPOKEN, "gui.millenaire.npcchat.unsupported");
   }
}
