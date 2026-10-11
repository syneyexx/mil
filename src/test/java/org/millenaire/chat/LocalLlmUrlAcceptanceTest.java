package org.millenaire.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalLlmUrlAcceptanceTest {
   @Test
   void acceptsLmStudioAndOllamaRejectsCloud() {
      assertTrue(LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl("http://127.0.0.1:1234/v1"));
      assertTrue(LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl("http://localhost:11434/v1"));
      assertTrue(LmStudioModelDiscovery.isOllamaBaseUrl("http://127.0.0.1:11434/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl("https://api.openai.com/v1"));
      assertFalse(LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl("https://api.x.ai/v1"));
      assertTrue(LmStudioModelDiscovery.isRemoteCloudApiUrl("https://api.x.ai/v1"));
      assertEquals(
         NpcChatConfigMigration.DEFAULT_LOCAL_BASE_URL,
         NpcChatConfigMigration.cloudBaseUrlReplacement("https://api.x.ai/v1").orElseThrow()
      );
   }
}
