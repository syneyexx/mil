package org.millenaire.chat;

public record NpcTurn(String role, String text, long gameTime) {
   public static final String ROLE_PLAYER = "player";
   public static final String ROLE_NPC = "npc";
}
