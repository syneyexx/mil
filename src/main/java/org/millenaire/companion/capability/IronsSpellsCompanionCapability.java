package org.millenaire.companion.capability;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.millenaire.companion.CompanionIntent;
import org.millenaire.companion.CompanionWorldActor;
import org.millenaire.entity.MillVillager;

public final class IronsSpellsCompanionCapability implements CompanionCapability {
   @Override
   public String id() {
      return "irons_spellbooks";
   }

   @Override
   public boolean isAvailable() {
      return ModList.get().isLoaded("irons_spellbooks") || ModList.get().isLoaded("spell_engine");
   }

   @Override
   public List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion) {
      boolean spellItem = looksLikeSpellItem(companion.getMainHandItem());
      return List.of("cap.spells=loaded", "cap.spells.holdingSpellItem=" + spellItem);
   }

   @Override
   @Nullable
   public String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent) {
      if (!"CAST_SPELL".equals(intent.type())) {
         return null;
      }
      if (!looksLikeSpellItem(companion.getMainHandItem())) {
         for (var entry : companion.getInventory().getAll().entrySet()) {
            if (entry.getValue() > 0) {
               ItemStack stack = new ItemStack(entry.getKey());
               if (looksLikeSpellItem(stack)) {
                  companion.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                  break;
               }
            }
         }
      }
      if (!looksLikeSpellItem(companion.getMainHandItem())) {
         return "CAST_SPELL failed: no spell item/book in inventory";
      }
      // Soft cast: use item as player would; dedicated spell APIs vary by mod version.
      return CompanionWorldActor.useItem(level, companion) + " (spell soft-use)";
   }

   private static boolean looksLikeSpellItem(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (id == null) {
         return false;
      }
      String ns = id.getNamespace();
      String path = id.getPath();
      return ns.contains("iron") && path.contains("spell")
         || ns.contains("spell")
         || path.contains("spellbook")
         || path.contains("scroll");
   }
}
