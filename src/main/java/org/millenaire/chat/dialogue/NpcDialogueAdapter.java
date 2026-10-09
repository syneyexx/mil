package org.millenaire.chat.dialogue;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.NpcModelReply;

/**
 * Extracts NPC-specific dialogue behaviour. One adapter per category; registry picks the best match.
 * Adapters must not load optional third-party classes unless presence is verified.
 */
public interface NpcDialogueAdapter {
   /** Higher values win. Millénaire-specific should outrank vanilla specializations. */
   int priority();

   boolean supports(LivingEntity entity);

   NpcDialogueCategory category();

   NpcDialoguePolicy policy(ServerPlayer player, LivingEntity entity);

   /** Soft availability check after range/visibility; may return a translation key to refuse. */
   @Nullable
   String refusalKey(ServerPlayer player, LivingEntity entity);

   NpcDialogueIdentity identity(LivingEntity entity);

   NpcDialogueContext buildContext(ServerPlayer player, LivingEntity entity, NpcMind mind, NpcMindSavedData data, String speech, String language);

   /** Optional speech bubble / broadcast; no-op for entities without Millénaire speech. */
   default void applySpokenLine(ServerPlayer player, LivingEntity entity, String say) {
   }

   /** Apply server-validated soft effects from model output (mood/opinion already handled centrally). */
   default void onModelReplyAccepted(ServerPlayer player, LivingEntity entity, NpcModelReply reply) {
   }

   default String defaultSystemPrompt() {
      return "";
   }
}
