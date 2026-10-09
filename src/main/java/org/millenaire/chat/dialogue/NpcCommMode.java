package org.millenaire.chat.dialogue;

/**
 * How an NPC participates in dialogue. Adapters choose deliberately; generics never invent fluent speech for nonverbal mobs.
 */
public enum NpcCommMode {
   /** Natural spoken conversation (humans, villagers, traders, etc.). */
   SPOKEN,
   /** Limited or broken language (piglins, some hostiles). */
   LIMITED,
   /** Body language, sounds, short atmospheric descriptions — no fluent human speech. */
   NONVERBAL,
   /** Sparse atmospheric reaction (undead, creepers) without conversational IQ. */
   ATMOSPHERIC
}
