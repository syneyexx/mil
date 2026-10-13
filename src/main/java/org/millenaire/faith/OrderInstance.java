package org.millenaire.faith;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** World-local instance of an order template. */
public final class OrderInstance {
   private final UUID id;
   private final ResourceLocation templateId;
   private boolean exposed;
   private boolean disbanded;

   public OrderInstance(UUID id, ResourceLocation templateId) {
      this.id = id;
      this.templateId = templateId;
   }

   public UUID id() {
      return this.id;
   }

   public ResourceLocation templateId() {
      return this.templateId;
   }

   public boolean exposed() {
      return this.exposed;
   }

   public void setExposed(boolean exposed) {
      this.exposed = exposed;
   }

   public boolean disbanded() {
      return this.disbanded;
   }

   public void setDisbanded(boolean disbanded) {
      this.disbanded = disbanded;
   }

   @Nullable
   public OrderDefinition definition() {
      return OrderRegistry.get(this.templateId);
   }

   public String resolveDisplayName() {
      OrderDefinition def = definition();
      return def != null ? def.displayName() : this.templateId.getPath();
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putString("template", this.templateId.toString());
      if (this.exposed) {
         tag.putBoolean("exposed", true);
      }
      if (this.disbanded) {
         tag.putBoolean("disbanded", true);
      }
      return tag;
   }

   public static OrderInstance load(CompoundTag tag) {
      OrderInstance inst = new OrderInstance(tag.getUUID("id"), ResourceLocation.parse(tag.getString("template")));
      inst.exposed = tag.getBoolean("exposed");
      inst.disbanded = tag.getBoolean("disbanded");
      return inst;
   }
}
