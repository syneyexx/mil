package org.millenaire.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NpcChatConfigMigrationTest {
   @Test
   void rewritesCloudBaseUrlToLocalDefault() {
      Optional<String> next = NpcChatConfigMigration.cloudBaseUrlReplacement("https://api.x.ai/v1");
      assertTrue(next.isPresent());
      assertEquals(NpcChatConfigMigration.DEFAULT_LOCAL_BASE_URL, next.get());
      assertEquals("http://127.0.0.1:1234/v1", next.get());
   }

   @Test
   void rewritesOpenAiCloudBaseUrl() {
      Optional<String> next = NpcChatConfigMigration.cloudBaseUrlReplacement("https://api.openai.com/v1");
      assertTrue(next.isPresent());
      assertEquals(NpcChatConfigMigration.DEFAULT_LOCAL_BASE_URL, next.get());
   }

   @Test
   void leavesLocalLmStudioAndOllamaAlone() {
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement("http://127.0.0.1:1234/v1").isEmpty());
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement("http://localhost:1234/v1").isEmpty());
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement("http://127.0.0.1:11434/v1").isEmpty());
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement("http://192.168.1.18:1234/v1").isEmpty());
   }

   @Test
   void ignoresBlankAndUnknownRejectedHosts() {
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement(null).isEmpty());
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement("").isEmpty());
      // Public non-cloud host is rejected by the gate but not auto-migrated.
      assertTrue(NpcChatConfigMigration.cloudBaseUrlReplacement("https://example.com/v1").isEmpty());
   }

   @Test
   void cloudDetectorMatchesKnownHosts() {
      assertTrue(LmStudioModelDiscovery.isRemoteCloudApiUrl("https://api.x.ai/v1"));
      assertTrue(LmStudioModelDiscovery.isRemoteCloudApiUrl("https://api.openai.com/v1"));
      assertFalse(LmStudioModelDiscovery.isRemoteCloudApiUrl("http://127.0.0.1:1234/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl("https://api.x.ai/v1"));
      assertTrue(LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl(NpcChatConfigMigration.DEFAULT_LOCAL_BASE_URL));
   }
}
