package org.millenaire.knowledge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Event-driven knowledge progress. No per-tick passive XP.
 * Validates dependency graph at content load for missing refs / cycles.
 */
public final class KnowledgeService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final KnowledgeService INSTANCE = new KnowledgeService();

   private final Map<String, KnowledgeNode> nodes = new HashMap<>();
   private boolean contentValid = true;

   private KnowledgeService() {
      // Built-in starter nodes (content-driven expansion later).
      register(
         new KnowledgeNode(
            "efficient_craft",
            List.of(),
            KnowledgeNode.KnowledgeScope.LOCAL,
            "PRODUCTION_EVENT",
            "Practice crafting with a mentor",
            0.15f,
            "craft"
         )
      );
      register(
         new KnowledgeNode(
            "master_builder",
            List.of("efficient_craft"),
            KnowledgeNode.KnowledgeScope.LOCAL,
            "PUBLIC_PROJECT",
            "Complete buildings with experienced builders",
            0.20f,
            "build"
         )
      );
      register(
         new KnowledgeNode(
            "regional_trade_lore",
            List.of(),
            KnowledgeNode.KnowledgeScope.REGIONAL,
            "TRADE_CULTURE",
            "Trade with other cultures",
            0.10f,
            "trade"
         )
      );
      validateGraph();
   }

   public static KnowledgeService get() {
      return INSTANCE;
   }

   public void register(KnowledgeNode node) {
      this.nodes.put(node.id(), node);
   }

   public void validateGraph() {
      this.contentValid = true;
      for (KnowledgeNode node : this.nodes.values()) {
         for (String req : node.requires()) {
            if (!this.nodes.containsKey(req)) {
               LOGGER.error("[Knowledge] Missing dependency {} for {}", req, node.id());
               this.contentValid = false;
            }
         }
      }
      // Cycle detection (DFS)
      Set<String> visiting = new HashSet<>();
      Set<String> visited = new HashSet<>();
      for (String id : this.nodes.keySet()) {
         if (hasCycle(id, visiting, visited)) {
            LOGGER.error("[Knowledge] Cycle detected at {}", id);
            this.contentValid = false;
            break;
         }
      }
   }

   private boolean hasCycle(String id, Set<String> visiting, Set<String> visited) {
      if (visited.contains(id)) {
         return false;
      }
      if (!visiting.add(id)) {
         return true;
      }
      KnowledgeNode node = this.nodes.get(id);
      if (node != null) {
         for (String req : node.requires()) {
            if (hasCycle(req, visiting, visited)) {
               return true;
            }
         }
      }
      visiting.remove(id);
      visited.add(id);
      return false;
   }

   public boolean isContentValid() {
      return this.contentValid;
   }

   public List<KnowledgeNode> nodes() {
      return List.copyOf(this.nodes.values());
   }

   /** Award progress from a real completed task (capped). */
   public void onSkillEvent(ServerLevel level, Village village, String skillKey, int amount) {
      if (!this.contentValid || village == null || skillKey == null || amount <= 0) {
         return;
      }
      KnowledgeSavedData data = KnowledgeSavedData.get(level);
      for (KnowledgeNode node : this.nodes.values()) {
         if (!skillKey.equals(node.skillKey())) {
            continue;
         }
         if (!prereqsMet(data, village.getId().uuid(), node)) {
            continue;
         }
         float progress = data.addProgress(village.getId().uuid(), node.id(), amount * 0.01f, node.bonusCap());
         if (progress >= node.bonusCap()) {
            data.unlock(village.getId().uuid(), node.id(), node.scope());
         }
      }
      data.setDirty();
   }

   public float bonusFor(ServerLevel level, Village village, String skillKey) {
      KnowledgeSavedData data = KnowledgeSavedData.get(level);
      float bonus = 0f;
      for (KnowledgeNode node : this.nodes.values()) {
         if (!skillKey.equals(node.skillKey())) {
            continue;
         }
         if (data.isUnlocked(village.getId().uuid(), node.id())) {
            bonus += Math.min(node.bonusCap(), data.getProgress(village.getId().uuid(), node.id()));
         }
      }
      return Math.min(0.5f, bonus);
   }

   private boolean prereqsMet(KnowledgeSavedData data, UUID villageId, KnowledgeNode node) {
      for (String req : node.requires()) {
         if (!data.isUnlocked(villageId, req)) {
            return false;
         }
      }
      return true;
   }

   /** Decay local knowledge slightly when a master with that skill is lost. */
   public void onMasterLost(ServerLevel level, Village village, VillagerRecord lost) {
      if (lost == null || lost.getCraftXp().isEmpty()) {
         return;
      }
      KnowledgeSavedData data = KnowledgeSavedData.get(level);
      for (String skill : lost.getCraftXp().keySet()) {
         for (KnowledgeNode node : this.nodes.values()) {
            if (skill.equals(node.skillKey()) && node.scope() == KnowledgeNode.KnowledgeScope.LOCAL) {
               data.decayProgress(village.getId().uuid(), node.id(), 0.05f);
            }
         }
      }
      data.setDirty();
   }
}
