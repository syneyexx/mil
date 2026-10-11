package org.millenaire.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionReplyParserTest {
   @Test
   void parsesStructuredReplyWithIntents() {
      String raw = """
         {"thought":"ok","say":"I will follow.","mood":"warm","new_memories":["likes exploring"],"opinion_delta":{"trust":0.1,"fear":0,"affection":0.05},"stance":"STICK_CLOSE","intents":[{"type":"FOLLOW_PLAYER"},{"type":"PICK_UP_NEARBY_ITEMS"}]}
         """;
      var reply = CompanionReplyParser.parse(raw);
      assertTrue(reply.isPresent());
      assertEquals("I will follow.", reply.get().say());
      assertEquals(CompanionStance.STICK_CLOSE, reply.get().stance());
      assertEquals(2, reply.get().intents().size());
      assertEquals("FOLLOW_PLAYER", reply.get().intents().get(0).type());
   }

   @Test
   void fallsBackToPlainProse() {
      var reply = CompanionReplyParser.parse("Sure, I am with you.");
      assertTrue(reply.isPresent());
      assertEquals("Sure, I am with you.", reply.get().say());
      assertTrue(reply.get().intents().isEmpty());
   }

   @Test
   void dropsUnknownIntents() {
      String raw = "{\"say\":\"Hi\",\"intents\":[{\"type\":\"HACK_THE_PLANET\"},{\"type\":\"SAY_ALOUD\"}]}";
      var reply = CompanionReplyParser.parse(raw);
      assertTrue(reply.isPresent());
      assertEquals(1, reply.get().intents().size());
      assertEquals("SAY_ALOUD", reply.get().intents().get(0).type());
   }

   @Test
   void catalogKnowsCoreIntents() {
      assertTrue(CompanionActionCatalog.isKnown("FOLLOW_PLAYER"));
      assertTrue(CompanionActionCatalog.isKnown("CREATE_INTERACT"));
      assertFalse(CompanionActionCatalog.isKnown("NOT_A_THING"));
   }
}
