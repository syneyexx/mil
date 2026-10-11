package org.millenaire.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.millenaire.chat.NpcMind;
import org.millenaire.config.MillenaireServerConfig;

public final class CompanionReplyParser {
   private CompanionReplyParser() {
   }

   public static Optional<CompanionReply> parse(String raw) {
      if (raw == null || raw.isBlank()) {
         return Optional.empty();
      }
      String trimmed = raw.trim();
      Optional<CompanionReply> structured = parseStructured(trimmed);
      if (structured.isPresent()) {
         return structured;
      }
      String plain = sanitizeSay(trimmed);
      if (plain.isBlank()) {
         return Optional.empty();
      }
      return Optional.of(new CompanionReply("", plain, "", List.of(), 0f, 0f, 0f, null, List.of()));
   }

   private static Optional<CompanionReply> parseStructured(String raw) {
      String json = extractJsonObject(raw);
      if (json == null) {
         return Optional.empty();
      }
      try {
         JsonElement root = JsonParser.parseString(json);
         if (!root.isJsonObject()) {
            return Optional.empty();
         }
         JsonObject obj = root.getAsJsonObject();
         String say = obj.has("say") && obj.get("say").isJsonPrimitive() ? sanitizeSay(obj.get("say").getAsString()) : "";
         String thought = obj.has("thought") && obj.get("thought").isJsonPrimitive()
            ? NpcMind.truncate(obj.get("thought").getAsString(), 240)
            : "";
         String mood = obj.has("mood") && obj.get("mood").isJsonPrimitive() ? obj.get("mood").getAsString() : "";
         List<String> memories = parseStringArray(obj, "new_memories", 3, 140);
         float trust = readDelta(obj, "trust");
         float fear = readDelta(obj, "fear");
         float affection = readDelta(obj, "affection");
         CompanionStance stance = null;
         if (obj.has("stance") && obj.get("stance").isJsonPrimitive()) {
            stance = CompanionStance.parse(obj.get("stance").getAsString(), null);
         }
         List<CompanionIntent> intents = parseIntents(obj);
         if (say.isBlank() && intents.isEmpty()) {
            return Optional.empty();
         }
         return Optional.of(new CompanionReply(thought, say, mood, memories, trust, fear, affection, stance, intents));
      } catch (Exception e) {
         return Optional.empty();
      }
   }

   private static List<CompanionIntent> parseIntents(JsonObject obj) {
      List<CompanionIntent> out = new ArrayList<>();
      if (!obj.has("intents") || !obj.get("intents").isJsonArray()) {
         return out;
      }
      int max = 4;
      try {
         max = MillenaireServerConfig.SERVER.companionMaxIntentsPerReply.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      JsonArray arr = obj.getAsJsonArray("intents");
      for (int i = 0; i < arr.size() && out.size() < max; i++) {
         JsonElement el = arr.get(i);
         if (el.isJsonPrimitive()) {
            String type = el.getAsString();
            if (CompanionActionCatalog.isKnown(type)) {
               out.add(CompanionIntent.of(type));
            }
         } else if (el.isJsonObject()) {
            JsonObject io = el.getAsJsonObject();
            String type = io.has("type") && io.get("type").isJsonPrimitive() ? io.get("type").getAsString() : "";
            if (!CompanionActionCatalog.isKnown(type)) {
               continue;
            }
            String target = io.has("target") && io.get("target").isJsonPrimitive() ? io.get("target").getAsString() : null;
            String arg = io.has("arg") && io.get("arg").isJsonPrimitive() ? io.get("arg").getAsString() : null;
            out.add(new CompanionIntent(type, target, arg));
         }
      }
      return out;
   }

   private static List<String> parseStringArray(JsonObject obj, String key, int max, int len) {
      List<String> out = new ArrayList<>();
      if (!obj.has(key) || !obj.get(key).isJsonArray()) {
         return out;
      }
      for (JsonElement el : obj.getAsJsonArray(key)) {
         if (el.isJsonPrimitive() && out.size() < max) {
            String s = NpcMind.truncate(el.getAsString().replace('\n', ' ').trim(), len);
            if (!s.isBlank()) {
               out.add(s);
            }
         }
      }
      return out;
   }

   private static float readDelta(JsonObject obj, String key) {
      if (!obj.has("opinion_delta") || !obj.get("opinion_delta").isJsonObject()) {
         return 0f;
      }
      JsonObject od = obj.getAsJsonObject("opinion_delta");
      if (!od.has(key) || !od.get(key).isJsonPrimitive()) {
         return 0f;
      }
      try {
         float v = od.get(key).getAsFloat();
         return Math.max(-0.15f, Math.min(0.15f, v));
      } catch (Exception e) {
         return 0f;
      }
   }

   private static String sanitizeSay(String raw) {
      String s = raw.replace('\r', ' ').trim();
      if (s.startsWith("```")) {
         int nl = s.indexOf('\n');
         if (nl > 0) {
            s = s.substring(nl + 1);
         }
         int end = s.lastIndexOf("```");
         if (end >= 0) {
            s = s.substring(0, end);
         }
      }
      s = s.replace('\n', ' ').trim();
      return NpcMind.truncate(s, 280);
   }

   private static String extractJsonObject(String raw) {
      int start = raw.indexOf('{');
      int end = raw.lastIndexOf('}');
      if (start < 0 || end <= start) {
         return null;
      }
      return raw.substring(start, end + 1);
   }
}
