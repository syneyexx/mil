package org.millenaire.faith;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A concrete faith in a world. Content templates ({@link FaithDefinition}) are shared;
 * custom player faiths each get their own UUID so equal names are not the same religion.
 */
public final class FaithInstance {
   private final UUID id;
   private final ResourceLocation templateId;
   @Nullable
   private String displayName;
   private boolean orphanTemplate;

   public FaithInstance(UUID id, ResourceLocation templateId, @Nullable String displayName) {
      this.id = id;
      this.templateId = templateId;
      this.displayName = displayName;
   }

   public UUID id() {
      return this.id;
   }

   public ResourceLocation templateId() {
      return this.templateId;
   }

   @Nullable
   public String displayName() {
      return this.displayName;
   }

   public void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   public boolean orphanTemplate() {
      return this.orphanTemplate;
   }

   public void setOrphanTemplate(boolean orphanTemplate) {
      this.orphanTemplate = orphanTemplate;
   }

   public String resolveDisplayName() {
      if (this.displayName != null && !this.displayName.isBlank()) {
         return this.displayName.trim();
      }
      FaithDefinition def = FaithRegistry.get(this.templateId);
      if (def != null) {
         return def.displayName();
      }
      return this.templateId.getPath();
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putString("template", this.templateId.toString());
      if (this.displayName != null && !this.displayName.isBlank()) {
         tag.putString("name", this.displayName);
      }
      if (this.orphanTemplate) {
         tag.putBoolean("orphan", true);
      }
      return tag;
   }

   public static FaithInstance load(CompoundTag tag) {
      UUID id = tag.getUUID("id");
      ResourceLocation template = ResourceLocation.parse(tag.getString("template"));
      String name = tag.contains("name") ? tag.getString("name") : null;
      FaithInstance inst = new FaithInstance(id, template, name);
      inst.orphanTemplate = tag.getBoolean("orphan");
      if (FaithRegistry.get(template) == null) {
         inst.orphanTemplate = true;
      }
      return inst;
   }
}
