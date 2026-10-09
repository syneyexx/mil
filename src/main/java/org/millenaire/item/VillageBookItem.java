package org.millenaire.item;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.millenaire.village.Village;
import org.millenaire.village.VillageBookService;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

public class VillageBookItem extends Item {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String TAG_VILLAGE_ID = "village_id";
   private static final String TAG_VILLAGE_NAME = "village_name";

   public VillageBookItem(Properties properties) {
      super(properties);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide()) {
         return InteractionResultHolder.success(stack);
      }

      if (player instanceof ServerPlayer serverPlayer) {
         UUID villageUuid = getVillageId(stack);
         if (villageUuid == null) {
            serverPlayer.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
            return InteractionResultHolder.fail(stack);
         }

         ServerLevel serverLevel = (ServerLevel)level;
         Village village = Village.resolve(serverLevel, new VillageId(villageUuid));
         if (village == null) {
            serverPlayer.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
            return InteractionResultHolder.fail(stack);
         }

         VillageBookService.openOrRefreshBook(serverPlayer, village, 0, 0, "");
         LOGGER.debug("Scroll used by {} for village {}", player.getName().getString(), village.getVillageName());
         return InteractionResultHolder.success(stack);
      } else {
         return InteractionResultHolder.fail(stack);
      }
   }

   public Component getName(ItemStack stack) {
      String villageName = getVillageName(stack);
      return (Component)(villageName != null ? Component.translatable("item.millenaire.village_scroll.named").append(villageName) : super.getName(stack));
   }

   @Nullable
   public static UUID getVillageId(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return null;
      }

      CompoundTag tag = customData.copyTag();
      if (!tag.contains("village_id")) {
         return null;
      }

      try {
         return UUID.fromString(tag.getString("village_id"));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   @Nullable
   public static String getVillageName(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return null;
      }

      CompoundTag tag = customData.copyTag();
      if (!tag.contains("village_name")) {
         return null;
      }

      String name = tag.getString("village_name");
      return name.isEmpty() ? null : name;
   }
}
