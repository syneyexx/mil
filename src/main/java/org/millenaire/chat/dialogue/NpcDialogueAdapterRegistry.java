package org.millenaire.chat.dialogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.millenaire.chat.dialogue.adapter.AllayAdapter;
import org.millenaire.chat.dialogue.adapter.GenericLivingAdapter;
import org.millenaire.chat.dialogue.adapter.GolemAdapter;
import org.millenaire.chat.dialogue.adapter.IllagerAdapter;
import org.millenaire.chat.dialogue.adapter.IntelligentHostileAdapter;
import org.millenaire.chat.dialogue.adapter.MillenaireVillagerAdapter;
import org.millenaire.chat.dialogue.adapter.NonverbalAnimalAdapter;
import org.millenaire.chat.dialogue.adapter.NonverbalMonsterAdapter;
import org.millenaire.chat.dialogue.adapter.PiglinAdapter;
import org.millenaire.chat.dialogue.adapter.VanillaVillagerAdapter;
import org.millenaire.chat.dialogue.adapter.WanderingTraderAdapter;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Resolves the best dialogue adapter for an entity. Resolution is deterministic and caches by entity type id.
 */
public final class NpcDialogueAdapterRegistry {
   private static final List<NpcDialogueAdapter> ADAPTERS = new ArrayList<>();
   private static final Map<ResourceLocation, NpcDialogueAdapter> TYPE_CACHE = new ConcurrentHashMap<>();
   private static volatile boolean initialized;

   private NpcDialogueAdapterRegistry() {
   }

   public static synchronized void bootstrap() {
      if (initialized) {
         return;
      }
      ADAPTERS.clear();
      // Priority order is also encoded in adapter.priority(); list order is a stable tie-break.
      register(new MillenaireVillagerAdapter());
      register(new VanillaVillagerAdapter());
      register(new WanderingTraderAdapter());
      register(new IllagerAdapter());
      register(new PiglinAdapter());
      register(new GolemAdapter());
      register(new AllayAdapter());
      register(new IntelligentHostileAdapter());
      register(new NonverbalAnimalAdapter());
      register(new NonverbalMonsterAdapter());
      register(new GenericLivingAdapter());
      ADAPTERS.sort(Comparator.comparingInt(NpcDialogueAdapter::priority).reversed());
      initialized = true;
   }

   public static void register(NpcDialogueAdapter adapter) {
      ADAPTERS.add(adapter);
      TYPE_CACHE.clear();
   }

   /** Public extension point for optional integrations after bootstrap. */
   public static synchronized void registerExternal(NpcDialogueAdapter adapter) {
      bootstrap();
      ADAPTERS.add(adapter);
      ADAPTERS.sort(Comparator.comparingInt(NpcDialogueAdapter::priority).reversed());
      TYPE_CACHE.clear();
   }

   public static void invalidateCache() {
      TYPE_CACHE.clear();
   }

   @Nullable
   public static NpcDialogueTarget resolve(ServerPlayer player, Entity entity) {
      bootstrap();
      if (entity == null || !(entity instanceof LivingEntity living) || !living.isAlive()) {
         return null;
      }
      if (living instanceof Player) {
         return null;
      }
      if (!categoryEnabled(living)) {
         return null;
      }
      NpcDialogueAdapter adapter = findAdapter(living);
      if (adapter == null) {
         return null;
      }
      NpcDialoguePolicy policy = adapter.policy(player, living);
      if (!policy.allowDialogue()) {
         return null;
      }
      return new NpcDialogueTarget(living, adapter, adapter.identity(living), policy);
   }

   @Nullable
   public static NpcDialogueAdapter findAdapter(LivingEntity entity) {
      bootstrap();
      ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      if (typeId != null) {
         NpcDialogueAdapter cached = TYPE_CACHE.get(typeId);
         if (cached != null && cached.supports(entity) && categoryToggle(cached.category())) {
            return cached;
         }
      }
      // Prefer the highest-priority adapter that supports the entity. If that category is
      // disabled, do not silently fall through to a lower generic spoken adapter.
      NpcDialogueAdapter bestSupport = null;
      for (NpcDialogueAdapter adapter : ADAPTERS) {
         if (!adapter.supports(entity)) {
            continue;
         }
         if (bestSupport == null) {
            bestSupport = adapter;
         }
         if (categoryToggle(adapter.category())) {
            if (typeId != null) {
               TYPE_CACHE.put(typeId, adapter);
            }
            return adapter;
         }
         // Specialized match exists but toggle is off — stop rather than using generic.
         if (adapter.category() != NpcDialogueCategory.GENERIC_LIVING) {
            return null;
         }
      }
      return null;
   }

   private static boolean categoryEnabled(LivingEntity entity) {
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         return false;
      }
      for (NpcDialogueAdapter candidate : ADAPTERS) {
         if (candidate.supports(entity) && categoryToggle(candidate.category())) {
            return true;
         }
      }
      return false;
   }

   private static boolean categoryToggle(NpcDialogueCategory category) {
      return switch (category) {
         case MILLENAIRE_VILLAGER -> (Boolean)MillenaireServerConfig.SERVER.npcChatMillenaire.get();
         case VANILLA_VILLAGER, WANDERING_TRADER, ILLAGER, PIGLIN, GOLEM, ALLAY, INTELLIGENT_HOSTILE ->
            (Boolean)MillenaireServerConfig.SERVER.npcChatVanilla.get();
         case NONVERBAL_ANIMAL, NONVERBAL_MONSTER ->
            (Boolean)MillenaireServerConfig.SERVER.npcChatVanilla.get()
               && (Boolean)MillenaireServerConfig.SERVER.npcChatNonverbal.get();
         case GENERIC_LIVING -> (Boolean)MillenaireServerConfig.SERVER.npcChatModded.get()
            || (Boolean)MillenaireServerConfig.SERVER.npcChatGenericLiving.get();
         case UNSUPPORTED -> false;
      };
   }
}
