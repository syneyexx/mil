package org.millenaire.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NpcReplyParser {
   private NpcReplyParser() {
   }

   public static Optional<NpcModelReply> parse(String raw) {
      if (raw == null || raw.isBlank()) {
         return Optional.empty();
      }
      String trimmed = raw.trim();
      Optional<NpcModelReply> structured = parseStructured(trimmed);
      if (structured.isPresent()) {
         return structured;
      }
      // Small local models often ignore the JSON schema and reply in plain prose.
      String plain = sanitizeSay(trimmed);
      if (plain.isBlank()) {
         return Optional.empty();
      }
      return Optional.of(new NpcModelReply("", plain, "", List.of(), 0.0F, 0.0F, 0.0F, false, 0, ""));
   }

   private static Optional<NpcModelReply> parseStructured(String raw) {
      String json = extractJsonObject(raw);
      if (json == null) {
         json = extractJsonObjectLoose(raw);
      }
      if (json == null) {
         return Optional.empty();
      }
      try {
         JsonElement root = JsonParser.parseString(json);
         if (!root.isJsonObject()) {
            return Optional.empty();
         }
         JsonObject obj = root.getAsJsonObject();
         if (!obj.has("say") || !obj.get("say").isJsonPrimitive()) {
            return Optional.empty();
         }
         String say = sanitizeSay(obj.get("say").getAsString());
         if (say.isBlank()) {
            return Optional.empty();
         }
         String thought = obj.has("thought") && obj.get("thought").isJsonPrimitive()
            ? NpcMind.truncate(obj.get("thought").getAsString(), 240)
            : "";
         String mood = obj.has("mood") && obj.get("mood").isJsonPrimitive() ? obj.get("mood").getAsString() : "";
         List<String> memories = parseMemories(obj);
         float trust = readDelta(obj, "trust");
         float fear = readDelta(obj, "fear");
         float affection = readDelta(obj, "affection");
         boolean keepSecret = obj.has("keep_secret") && obj.get("keep_secret").isJsonPrimitive() && obj.get("keep_secret").getAsBoolean();
         int gossipWorthy = 0;
         if (obj.has("gossip_worthy") && obj.get("gossip_worthy").isJsonPrimitive()) {
            try {
               int value = obj.get("gossip_worthy").getAsInt();
               if (value == 0 || value == 1 || value == 2) {
                  gossipWorthy = value;
               }
            } catch (Exception ignored) {
               gossipWorthy = 0;
            }
         }
         String gossipLine = "";
         if (obj.has("gossip_line") && obj.get("gossip_line").isJsonPrimitive()) {
            gossipLine = NpcMind.truncate(obj.get("gossip_line").getAsString().replace('\n', ' ').replace('\r', ' ').trim(), 160);
         }
         return Optional.of(
            new NpcModelReply(thought, say, mood, memories, trust, fear, affection, keepSecret, gossipWorthy, gossipLine)
         );
      } catch (Exception e) {
         return Optional.empty();
      }
   }

   private static List<String> parseMemories(JsonObject obj) {
      List<String> memories = new ArrayList<>();
      if (!obj.has("new_memories") || !obj.get("new_memories").isJsonArray()) {
         return memories;
      }
      JsonArray arr = obj.getAsJsonArray("new_memories");
      for (JsonElement el : arr) {
         if (memories.size() >= 3) {
            break;
         }
         if (el.isJsonPrimitive()) {
            String text = NpcMind.truncate(el.getAsString().trim(), 140);
            if (!text.isBlank()) {
               memories.add(text);
            }
         }
      }
      return memories;
   }

   private static float readDelta(JsonObject obj, String key) {
      if (!obj.has("opinion_delta") || !obj.get("opinion_delta").isJsonObject()) {
         return 0.0F;
      }
      JsonObject delta = obj.getAsJsonObject("opinion_delta");
      if (!delta.has(key) || !delta.get(key).isJsonPrimitive()) {
         return 0.0F;
      }
      try {
         return NpcMind.clampDelta(delta.get(key).getAsFloat());
      } catch (Exception e) {
         return 0.0F;
      }
   }

   private static String sanitizeSay(String say) {
      if (say == null) {
         return "";
      }
      return NpcMind.truncate(say.replace('\n', ' ').replace('\r', ' ').trim(), 400);
   }

   public static String extractJsonObject(String raw) {
      String text = stripCodeFence(raw);
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
         return null;
      }
      String candidate = text.substring(start, end + 1);
      String before = text.substring(0, start).trim();
      String after = text.substring(end + 1).trim();
      if (!before.isEmpty() || !after.isEmpty()) {
         return null;
      }
      return candidate;
   }

   /** Like {@link #extractJsonObject} but tolerates prose wrapping the JSON object. */
   public static String extractJsonObjectLoose(String raw) {
      String text = stripCodeFence(raw);
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
         return null;
      }
      return text.substring(start, end + 1);
   }

   private static String stripCodeFence(String raw) {
      String text = raw.trim();
      if (!text.startsWith("```")) {
         return text;
      }
      int firstNl = text.indexOf('\n');
      if (firstNl > 0) {
         text = text.substring(firstNl + 1);
      }
      int fence = text.lastIndexOf("```");
      if (fence >= 0) {
         text = text.substring(0, fence);
      }
      return text.trim();
   }
}
