package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.command.DebugCommand;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.dialogue.SentenceLoader;
import org.millenaire.goal.VillagerTask;
import org.millenaire.network.SpeechChatPayload;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class VillagerSpeech {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SPEECH_COOLDOWN_TICKS = 600;
   private static final int GREETING_COOLDOWN_TICKS = 12000;
   private static final int GREETING_CHANCE_ON = 10;
   private static final double SPEECH_PLAYER_DISTANCE = 3.0;
   private static final int DISPLAY_DURATION_FACTOR = 2;
   private final MillVillager villager;
   private int speechCooldown;
   private int greetingCooldown;
   private int speechDisplayTimer;

   public VillagerSpeech(MillVillager villager) {
      this.villager = villager;
   }

   private static int computeDisplayTicks(int textLength) {
      return Math.min((10 + textLength / 5) * 20, 600) * 2;
   }

   public void tick(VillagerTask task, Village village) {
      if (this.greetingCooldown > 0) {
         this.greetingCooldown--;
      }

      if (this.speechDisplayTimer > 0) {
         this.speechDisplayTimer--;
         if (this.speechDisplayTimer == 0) {
            this.villager.setSpeechData("");
         }
      } else {
         if (this.speechCooldown > 0) {
            this.speechCooldown--;
         }

         if (this.greetingCooldown != 0 || !this.canGreet(village) || !this.fireSentence("greeting", 10)) {
            if (this.speechCooldown == 0 && task != null) {
               this.fireSentence(mapGoalKeyToSentenceKey(task.goalId().getPath()), 1);
            }
         }
      }
   }

   private boolean canGreet(Village village) {
      if (this.villager.getTarget() != null) {
         return false;
      } else {
         return village != null && !village.isUnderAttack() ? this.villager.level().isDay() : false;
      }
   }

   public void speakGoalChosen(VillagerTask task) {
      if (this.speechDisplayTimer <= 0 && this.speechCooldown <= 0) {
         String goalKey = mapGoalKeyToSentenceKey(task.goalId().getPath()) + ".chosen";
         this.fireSentence(goalKey, 1);
      }
   }

   private boolean fireSentence(String goalKey, int chanceOn) {
      if (chanceOn > 1 && ThreadLocalRandom.current().nextInt(chanceOn) != 0) {
         return false;
      }

      Player nearestPlayer = this.villager.level().getNearestPlayer(this.villager, 3.0);
      if (nearestPlayer == null) {
         return false;
      }

      ResourceLocation villagerTypeId = this.villager.getVillagerTypeId();
      if (villagerTypeId == null) {
         return false;
      }

      String vtPath = villagerTypeId.getPath();
      int slashIdx = vtPath.indexOf(47);
      if (slashIdx <= 0) {
         return false;
      }

      ResourceLocation cultureId = ModCultures.extractCultureId(villagerTypeId);
      String role = vtPath.substring(slashIdx + 1);
      if (ModCultures.getCulture(cultureId) == null) {
         return false;
      }

      String effectiveRole = SentenceLoader.resolveEffectiveRole(cultureId, "native", role, null, goalKey);
      if (effectiveRole == null) {
         return false;
      }

      int count = SentenceLoader.countVariants(cultureId, "native", effectiveRole, goalKey);
      if (count == 0) {
         return false;
      }

      int idx = ThreadLocalRandom.current().nextInt(count);
      String speechRef = "s:" + cultureId.getPath() + ":" + effectiveRole + ":" + goalKey + ":" + idx;
      this.villager.setSpeechData(speechRef);
      String nativeText = SentenceLoader.getSentence(cultureId, "native", effectiveRole, goalKey, idx);
      if (nativeText == null) {
         nativeText = goalKey;
      }

      nativeText = nativeText.replace("$name", nearestPlayer.getName().getString());
      this.speechDisplayTimer = computeDisplayTicks(nativeText.length());
      this.markSpoken();
      if (this.villager.level() instanceof ServerLevel serverLevel) {
         int chatRange = serverLevel.getServer().isSingleplayer()
            ? MillenaireServerConfig.SERVER.sentenceDistanceSingleplayer.getAsInt()
            : MillenaireServerConfig.SERVER.sentenceDistanceMultiplayer.getAsInt();
         if (chatRange > 0) {
            String displayName = this.villager.getVillagerDisplayName();
            String cultureKeyStr = cultureId.getPath();

            for (ServerPlayer sp : serverLevel.players()) {
               if (sp.distanceTo(this.villager) <= chatRange) {
                  int langScore = !MillenaireServerConfig.SERVER.languageLearning.get()
                     ? Integer.MAX_VALUE
                     : PlayerCultureReputation.get(serverLevel).getLanguageKnowledge(sp.getUUID(), cultureId);
                  SpeechChatPayload payload = new SpeechChatPayload(displayName != null ? displayName : "", speechRef, cultureKeyStr, langScore, "");
                  PacketDistributor.sendToPlayer(sp, payload, new CustomPacketPayload[0]);
               }
            }
         }
      }

      if (DebugCommand.isVerbose(this.villager.getUUID())) {
         LOGGER.info("[V-DEBUG] {} says ref: {} (native: {})", new Object[]{this.villager.getVillagerDisplayName(), speechRef, nativeText});
      }

      return true;
   }

   private void markSpoken() {
      this.speechCooldown = 600;
      this.greetingCooldown = 12000;
   }

   public void speakCombatCall(String goalKey) {
      ResourceLocation villagerTypeId = this.villager.getVillagerTypeId();
      if (villagerTypeId != null) {
         String vtPath = villagerTypeId.getPath();
         int slashIdx = vtPath.indexOf(47);
         if (slashIdx > 0) {
            ResourceLocation cultureId = ModCultures.extractCultureId(villagerTypeId);
            String role = vtPath.substring(slashIdx + 1);
            if (ModCultures.getCulture(cultureId) != null) {
               String effectiveRole = SentenceLoader.resolveEffectiveRole(cultureId, "native", role, null, goalKey);
               if (effectiveRole != null) {
                  int count = SentenceLoader.countVariants(cultureId, "native", effectiveRole, goalKey);
                  if (count != 0) {
                     int idx = ThreadLocalRandom.current().nextInt(count);
                     String speechRef = "s:" + cultureId.getPath() + ":" + effectiveRole + ":" + goalKey + ":" + idx;
                     this.villager.setSpeechData(speechRef);
                     String nativeText = SentenceLoader.getSentence(cultureId, "native", effectiveRole, goalKey, idx);
                     if (nativeText == null) {
                        nativeText = goalKey;
                     }

                     this.speechDisplayTimer = computeDisplayTicks(nativeText.length());
                     this.markSpoken();
                     if (this.villager.level() instanceof ServerLevel serverLevel) {
                        int chatRange = serverLevel.getServer().isSingleplayer()
                           ? MillenaireServerConfig.SERVER.sentenceDistanceSingleplayer.getAsInt()
                           : MillenaireServerConfig.SERVER.sentenceDistanceMultiplayer.getAsInt();
                        if (chatRange > 0) {
                           String displayName = this.villager.getVillagerDisplayName();
                           String cultureKeyStr = cultureId.getPath();

                           for (ServerPlayer sp : serverLevel.players()) {
                              if (sp.distanceTo(this.villager) <= chatRange) {
                                 int langScore = !MillenaireServerConfig.SERVER.languageLearning.get()
                                    ? Integer.MAX_VALUE
                                    : PlayerCultureReputation.get(serverLevel).getLanguageKnowledge(sp.getUUID(), cultureId);
                                 SpeechChatPayload payload = new SpeechChatPayload(
                                    displayName != null ? displayName : "", speechRef, cultureKeyStr, langScore, ""
                                 );
                                 PacketDistributor.sendToPlayer(sp, payload, new CustomPacketPayload[0]);
                              }
                           }
                        }
                     }

                     if (DebugCommand.isVerbose(this.villager.getUUID())) {
                        LOGGER.info("[V-DEBUG] {} shouts ref: {} (native: {})", new Object[]{this.villager.getVillagerDisplayName(), speechRef, nativeText});
                     }
                  }
               }
            }
         }
      }
   }

   public void setSpeechText(String text) {
      this.villager.setSpeechData(text);
      if (text.isEmpty()) {
         this.speechDisplayTimer = 0;
      } else {
         this.speechDisplayTimer = computeDisplayTicks(text.length());
         this.markSpoken();
      }
   }

   public String getSpeechText() {
      return this.villager.getSpeechData();
   }

   static String mapGoalKeyToSentenceKey(String goalKey) {
      return switch (goalKey) {
         case "chop_trees" -> "choptrees";
         case "plant_saplings" -> "plantsaplings";
         case "harvest_wheat_home" -> "collectcrop";
         case "plant_wheat_home" -> "plantseeds";
         case "build" -> "construction";
         case "idle" -> "gorest";
         case "rest" -> "sleep";
         case "pray", "meditate", "tend_sacrifices", "pray_male_muslim", "pray_female_muslim", "pray_muslim" -> "gopray";
         case "drink" -> "godrink";
         case "play", "play_with_friends" -> "goplay";
         case "hold_service" -> "goholdaservice";
         case "bring_back_home" -> "bringbackresourceshome";
         case "child_become_adult" -> "becomeadult";
         case "be_seller" -> "keepstall";
         case "craft_boudin" -> "makeboudin";
         case "craft_books" -> "makebooks";
         case "craft_tapestry" -> "maketapestry";
         case "craft_bread" -> "makebread";
         case "craft_cider", "craft_ciderhome" -> "makecider";
         case "craft_calvahome" -> "makecalva";
         case "craft_glassbottles" -> "makeglassbottles";
         case "craft_rasgulla" -> "makerasgulla";
         case "craft_indianstatue" -> "scult";
         case "craft_arrow" -> "makearrow";
         case "craft_bookshelves" -> "makebookshelves";
         case "craft_bow" -> "makebow";
         case "craft_brick" -> "makebrick";
         case "craft_byzantineboots" -> "makebyzantineboots";
         case "craft_byzantinechest" -> "makebyzantinechest";
         case "craft_byzantinehelmet" -> "makebyzantinehelmet";
         case "craft_byzantinelegs" -> "makebyzantinelegs";
         case "craft_byzantinemace" -> "makebyzantinemace";
         case "craft_byzantinetiles" -> "makebyzantinetiles";
         case "craft_lokum" -> "makelokum";
         case "craft_pide_beef" -> "makepidebeef";
         case "craft_pide_mutton" -> "makepidemutton";
         case "craft_steelaxe" -> "makesteelaxe";
         case "craft_steelhoe" -> "makesteelhoe";
         case "craft_steelpickaxe" -> "makesteelpickaxe";
         case "craft_steelshovel" -> "makesteelshovel";
         case "craft_wallcarpet_large" -> "makewallcarpetlarge";
         case "craft_wallcarpet_medium" -> "makewallcarpetmedium";
         case "craft_wallcarpet_small" -> "makewallcarpetsmall";
         case "craft_yogurt" -> "makeyogurt";
         case "get_goods_for_household" -> "getgoodshousehold";
         case "cook_indian_brick" -> "cookbrick";
         case "listen_to_speech_2" -> "listentospeech";
         default -> goalKey.replace("_", "");
      };
   }
}
