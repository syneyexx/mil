package org.millenaire.chat;

/**
 * One loaded LM Studio language-model instance suitable for chat completions.
 */
public record LmStudioModelInfo(String modelKey, String instanceId, String displayName, String type) {
   public String effectiveModelId() {
      return this.instanceId != null && !this.instanceId.isBlank() ? this.instanceId : this.modelKey;
   }

   public String label() {
      if (this.displayName != null && !this.displayName.isBlank()) {
         return this.displayName + " (" + this.effectiveModelId() + ")";
      }
      return this.effectiveModelId();
   }
}
