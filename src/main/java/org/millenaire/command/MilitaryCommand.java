package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

public final class MilitaryCommand {
   private static final Gson GSON = new GsonBuilder().create();
   private static final int SEARCH_RADIUS = 5000;

   private MilitaryCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("military").executes(MilitaryCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.translatable("command.millenaire.error.not_in_overworld"));
         return 0;
      } else {
         BlockPos searchPos = BlockPos.containing(source.getPosition());
         VillageSavedData savedData = VillageSavedData.get(level);
         VillageManager villageManager = savedData.getVillageManager();
         Village village = villageManager.findNearestVillage(searchPos, 5000.0);
         if (village == null) {
            source.sendSuccess(() -> Component.translatable("command.millenaire.status.no_village_5000"), false);
            return 1;
         } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("village", villageHeader(village));
            payload.put("strengths", strengths(village));
            payload.put("fighters", fighters(village, level));
            source.sendSuccess(() -> Component.literal(GSON.toJson(payload)), false);
            return 1;
         }
      }
   }

   private static Map<String, Object> villageHeader(Village village) {
      Map<String, Object> header = new LinkedHashMap<>();
      header.put("id", village.getId().uuid().toString().substring(0, 8));
      header.put("type", village.getVillageTypeId().getPath());
      header.put("center", village.getCenter().toShortString());
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      header.put("carries_raid", vt != null && vt.carriesRaid());
      return header;
   }

   private static Map<String, Integer> strengths(Village village) {
      Map<String, Integer> s = new LinkedHashMap<>();
      s.put("raid", village.getVillageRaidingStrength());
      s.put("defense", village.getVillageDefendingStrength());
      s.put("attacker", village.getVillageAttackerStrength());
      return s;
   }

   private static List<Map<String, Object>> fighters(Village village, ServerLevel level) {
      List<Map<String, Object>> out = new ArrayList<>();

      for (VillagerRecord record : village.getVillagerRecords().values()) {
         ResourceLocation typeId = record.getVillagerTypeId();
         VillagerType type = ModCultures.getVillagerType(typeId);
         if (type != null && (type.isRaider() || type.isHelpInAttacks())) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", typeId.getPath());
            f.put("uuid", record.getUuid().toString().substring(0, 8));
            f.put("military_strength", record.getMilitaryStrength());
            List<String> tags = new ArrayList<>();
            if (type.isRaider()) {
               tags.add("raider");
            }

            if (type.isHelpInAttacks()) {
               tags.add("defender");
            }

            f.put("tags", tags);
            String state;
            if (record.isKilled()) {
               state = "killed";
            } else if (record.isRaidingVillage()) {
               state = "raiding";
            } else {
               state = "alive";
            }

            f.put("state", state);
            UUID uuid = record.getUuid();
            if (level.getEntity(uuid) instanceof MillVillager mv) {
               f.put("loaded", true);
               f.put("pos", mv.blockPosition().toShortString());
               f.put("attack_cooldown", mv.getAttackCooldown());
               LivingEntity target = mv.getAttackTarget();
               f.put("attack_target", target == null ? null : describeTarget(target));
               GoalScheduler scheduler = mv.getGoalScheduler();
               String goalStr = "?";
               if (scheduler != null) {
                  VillagerGoal goal = scheduler.getCurrentGoal();
                  goalStr = goal != null ? goal.id().getPath() : "idle";
               } else {
                  goalStr = "no-sched";
               }

               f.put("goal", goalStr);
            } else {
               f.put("loaded", false);
            }

            out.add(f);
         }
      }

      return out;
   }

   private static String describeTarget(LivingEntity target) {
      String type = target.getType().getDescriptionId();
      String shortUuid = target.getUUID().toString().substring(0, 8);
      return type + ":" + shortUuid;
   }
}
