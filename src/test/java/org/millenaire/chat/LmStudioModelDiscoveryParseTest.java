package org.millenaire.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LmStudioModelDiscoveryParseTest {
   @Test
   void parsesNativeLoadedInstances() {
      String body = """
         {
           "models": [
             {
               "type": "llm",
               "key": "dolphin",
               "display_name": "Dolphin",
               "loaded_instances": [ { "id": "dolphin-inst" } ]
             },
             {
               "type": "llm",
               "key": "other",
               "display_name": "Other",
               "loaded_instances": []
             }
           ]
         }
         """;
      List<LmStudioModelInfo> loaded = LmStudioModelDiscovery.parseLoadedLlms(body);
      assertEquals(1, loaded.size());
      assertEquals("dolphin-inst", loaded.get(0).effectiveModelId());
   }

   @Test
   void parsesOpenAiCompatModels() {
      String body = """
         {
           "data": [
             { "id": "mradermacher/Dolphin-8B", "object": "model" },
             { "id": "text-embedding-nomic", "object": "model" }
           ]
         }
         """;
      List<LmStudioModelInfo> loaded = LmStudioModelDiscovery.parseOpenAiCompatModels(body);
      assertEquals(1, loaded.size());
      assertEquals("mradermacher/Dolphin-8B", loaded.get(0).effectiveModelId());
   }

   @Test
   void buildsModelUrlsFromBase() {
      assertEquals(
         "http://192.168.1.18:1234/api/v1/models",
         LmStudioModelDiscovery.toNativeModelsUrl("http://192.168.1.18:1234/v1")
      );
      assertEquals(
         "http://192.168.1.18:1234/v1/models",
         LmStudioModelDiscovery.toOpenAiModelsUrl("http://192.168.1.18:1234/v1/")
      );
   }

   @Test
   void acceptsOnlyLocalLmStudioUrls() {
      assertTrue(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("http://127.0.0.1:1234/v1"));
      assertTrue(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("http://localhost:1234/v1"));
      assertTrue(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("http://192.168.1.18:1234/v1"));
      assertTrue(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("http://10.0.0.5:1234/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("https://api.x.ai/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("https://api.openai.com/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl("https://openrouter.ai/api/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLmStudioBaseUrl(""));
   }

   @Test
   void normalizesCompletionsBaseToV1() {
      assertEquals("http://127.0.0.1:1234/v1", NpcModelClient.normalizeCompletionsBaseUrl("http://127.0.0.1:1234"));
      assertEquals("http://127.0.0.1:1234/v1", NpcModelClient.normalizeCompletionsBaseUrl("http://127.0.0.1:1234/v1/"));
      assertEquals("http://127.0.0.1:1234/v1", NpcModelClient.normalizeCompletionsBaseUrl("http://127.0.0.1:1234/v1/chat/completions"));
   }

   @Test
   void unreachableSnapshotFailsLoud() {
      NpcModelResolver.Result result = NpcModelResolver.resolveLmStudio(
         LmStudioModelDiscovery.Snapshot.unreachable(1L, "down")
      );
      assertFalse(result.ok());
      assertEquals(NpcModelResolver.Status.LMSTUDIO_UNREACHABLE, result.status());
   }

   @Test
   void plainTextReplyIsAccepted() {
      assertTrue(NpcReplyParser.parse("I'm doing well, traveler.").isPresent());
      assertEquals("I'm doing well, traveler.", NpcReplyParser.parse("I'm doing well, traveler.").get().say());
      assertFalse(NpcReplyParser.parse("   ").isPresent());
   }
}
