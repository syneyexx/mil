package org.millenaire.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.network.VillageCreateHubPayload;
import org.millenaire.network.VillageCreateWizardPayload;
import org.millenaire.village.CityStats;
import org.millenaire.village.CityStatsService;
import org.millenaire.village.CultureDiplomacyLogEntry;
import org.millenaire.village.CultureDiplomacySimulator;
import org.millenaire.village.CultureStandingSavedData;
import org.millenaire.village.CultureWarService;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.diplomacy.DiplomacyEventService;

public class VillageCreateWandItem extends Item {
   /** Civilization key for Cenobites (De Orde) — always available when founding a city. */
   public static final String CENOBITES_CULTURE_KEY = "orde";

   public VillageCreateWandItem(Properties properties) {
      super(properties);
   }

   public static boolean isCenobitesCultureKey(@Nullable String cultureKey) {
      if (cultureKey == null || cultureKey.isBlank()) {
         return false;
      }
      String key = cultureKey.trim().toLowerCase(Locale.ROOT);
      return CENOBITES_CULTURE_KEY.equals(key) || "cenobites".equals(key) || "cenobite".equals(key);
   }

   public static ResourceLocation cenobitesCultureId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", CENOBITES_CULTURE_KEY);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (!(context.getPlayer() instanceof ServerPlayer player)) {
         return InteractionResult.FAIL;
      }
      if (!level.dimension().equals(Level.OVERWORLD)) {
         player.sendSystemMessage(Component.translatable("ui.village_create_wand.overworld_only"));
         return InteractionResult.FAIL;
      }
      ServerLevel serverLevel = (ServerLevel)level;
      BlockPos spawnPos = context.getClickedPos().above();
      return openForPlayer(serverLevel, player, spawnPos);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide()) {
         return InteractionResultHolder.success(stack);
      }
      if (!(player instanceof ServerPlayer serverPlayer)) {
         return InteractionResultHolder.fail(stack);
      }
      if (!level.dimension().equals(Level.OVERWORLD)) {
         serverPlayer.sendSystemMessage(Component.translatable("ui.village_create_wand.overworld_only"));
         return InteractionResultHolder.fail(stack);
      }
      openForPlayer(serverPlayer.serverLevel(), serverPlayer, serverPlayer.blockPosition());
      return InteractionResultHolder.success(stack);
   }

   private static InteractionResult openForPlayer(ServerLevel level, ServerPlayer player, BlockPos pos) {
      CultureDiplomacySimulator.ensureUpToDate(level);
      Village owned = findOwnedVillage(level, player.getUUID());
      if (owned != null) {
         DiplomacyEventService.pushPendingToPlayer(player, level);
         PacketDistributor.sendToPlayer(player, buildHubPayload(level, player, owned), new CustomPacketPayload[0]);
         return InteractionResult.SUCCESS;
      }
      PacketDistributor.sendToPlayer(player, buildWizardPayload(level, player, pos), new CustomPacketPayload[0]);
      return InteractionResult.SUCCESS;
   }

   @Nullable
   public static Village findOwnedVillage(ServerLevel level, UUID playerId) {
      for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (v.isPlayerControlled() && playerId.equals(v.getOwnerUUID())) {
            return v;
         }
      }
      return null;
   }

   /**
    * Picks the default player-controlled village type for a culture.
    * Prefers a plain "controlled" layout over walled/small variants.
    */
   @Nullable
   public static VillageType resolveDefaultControlledType(@Nullable ResourceLocation cultureId) {
      if (cultureId == null) {
         return null;
      }
      VillageType best = null;
      int bestScore = Integer.MIN_VALUE;
      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (!vt.playerControlled() || !vt.spawnable() || vt.isHamlet()) {
            continue;
         }
         if (!cultureId.equals(vt.culture())) {
            continue;
         }
         int score = scoreControlledType(vt);
         if (score > bestScore) {
            bestScore = score;
            best = vt;
         }
      }
      return best;
   }

   private static int scoreControlledType(VillageType vt) {
      String path = vt.id().getPath().toLowerCase(Locale.ROOT);
      int score = 0;
      if (path.contains("controlled")) {
         score += 10;
      }
      if (path.endsWith("/controlled") || path.equals("controlled")) {
         score += 5;
      }
      if (path.contains("walled")) {
         score -= 5;
      }
      if (path.contains("small")) {
         score -= 5;
      }
      return score;
   }

   public static final int MAX_VILLAGE_NAME_LENGTH = 40;

   /** Trim, strip control chars, clamp length. Empty if nothing usable remains. */
   public static String sanitizeVillageName(@Nullable String raw) {
      if (raw == null) {
         return "";
      }
      StringBuilder sb = new StringBuilder(raw.length());
      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= 32 && c != 127) {
            sb.append(c);
         }
      }
      String trimmed = sb.toString().trim().replaceAll("\\s+", " ");
      if (trimmed.length() > MAX_VILLAGE_NAME_LENGTH) {
         trimmed = trimmed.substring(0, MAX_VILLAGE_NAME_LENGTH).trim();
      }
      return trimmed;
   }

   public static VillageCreateWizardPayload buildWizardPayload(ServerLevel level, ServerPlayer player, BlockPos pos) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      int warDistance = MillenaireServerConfig.SERVER.playerFoundTerritoryWarDistance.getAsInt();
      List<VillageCreateWizardPayload.NearbyVillage> nearby = new ArrayList<>();
      boolean territoryConflict = false;
      double closest = Double.MAX_VALUE;

      for (Village v : vm.getAllVillages()) {
         if (v.isLoneBuilding()) {
            continue;
         }
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         int radius = vt != null ? vt.radius() : 80;
         double dist = Math.sqrt(v.getCenter().distSqr(pos));
         closest = Math.min(closest, dist);
         boolean conflict = dist < warDistance;
         if (conflict) {
            territoryConflict = true;
         }
         if (dist < warDistance * 2.0 || nearby.size() < 5) {
            String name = v.getVillageName() != null ? v.getVillageName() : v.getCultureId().getPath();
            nearby.add(new VillageCreateWizardPayload.NearbyVillage(name, v.getCultureId().getPath(), (int)Math.round(dist), radius, conflict));
         }
      }
      nearby.sort(Comparator.comparingInt(VillageCreateWizardPayload.NearbyVillage::distance));
      if (nearby.size() > 8) {
         nearby = new ArrayList<>(nearby.subList(0, 8));
      }

      PlayerCultureReputation cultureRep = PlayerCultureReputation.get(level);
      List<VillageCreateWizardPayload.CultureOption> cultures = new ArrayList<>();
      List<VillageCreateWizardPayload.TypeOption> types = new ArrayList<>();

      final String customCultureKey = "player_custom";
      String customDisplay = Component.translatable("gui.millenaire.village_create.culture.player_custom").getString();
      Set<ResourceLocation> eligibleCultures = new LinkedHashSet<>();
      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (!vt.playerControlled() || !vt.spawnable() || vt.isHamlet()) {
            continue;
         }
         if (!cultureRep.hasCultureControl(player.getUUID(), vt.culture())) {
            continue;
         }
         eligibleCultures.add(vt.culture());
      }
      for (ResourceLocation cultureId : eligibleCultures) {
         VillageType def = resolveDefaultControlledType(cultureId);
         if (def == null) {
            continue;
         }
         Culture culture = ModCultures.getCulture(cultureId);
         String cultureName = culture != null ? culture.displayName() : cultureId.getPath();
         String cultureKey = cultureId.getPath();
         cultures.add(new VillageCreateWizardPayload.CultureOption(cultureKey, cultureName));
         types.add(new VillageCreateWizardPayload.TypeOption(cultureKey, def.id().getPath(), cultureName));
         types.add(new VillageCreateWizardPayload.TypeOption(customCultureKey, def.id().getPath(), cultureName));
      }
      cultures.sort(Comparator.comparing(VillageCreateWizardPayload.CultureOption::displayName, String.CASE_INSENSITIVE_ORDER));
      cultures.add(0, new VillageCreateWizardPayload.CultureOption(customCultureKey, customDisplay));
      appendCenobitesCultureOption(cultures, types);
      types.sort(Comparator.comparing(VillageCreateWizardPayload.TypeOption::displayName, String.CASE_INSENSITIVE_ORDER));

      boolean overlaps = false;
      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (vt.playerControlled()) {
            overlaps = vm.overlapsExistingVillage(pos, vt.radius());
            break;
         }
      }

      return new VillageCreateWizardPayload(
         pos,
         warDistance,
         closest == Double.MAX_VALUE ? -1 : (int)Math.round(closest),
         territoryConflict,
         overlaps,
         nearby,
         cultures,
         types
      );
   }

   /**
    * Cenobites (dimension villagers / De Orde) are always offered as a civilization type when
    * founding a player city — no culture-control unlock required.
    */
   private static void appendCenobitesCultureOption(
      List<VillageCreateWizardPayload.CultureOption> cultures, List<VillageCreateWizardPayload.TypeOption> types
   ) {
      ResourceLocation cenobitesId = cenobitesCultureId();
      for (VillageCreateWizardPayload.CultureOption existing : cultures) {
         if (isCenobitesCultureKey(existing.cultureKey())) {
            return;
         }
      }
      VillageType def = resolveDefaultControlledType(cenobitesId);
      if (def == null) {
         return;
      }
      Culture culture = ModCultures.getCulture(cenobitesId);
      String display = culture != null
         ? culture.displayName()
         : Component.translatable("gui.millenaire.village_create.culture.cenobites").getString();
      // Keep Eigen cultuur first; insert Cenobites next.
      cultures.add(1, new VillageCreateWizardPayload.CultureOption(CENOBITES_CULTURE_KEY, display));
      types.add(new VillageCreateWizardPayload.TypeOption(CENOBITES_CULTURE_KEY, def.id().getPath(), display));
   }

   public static VillageCreateHubPayload buildHubPayload(ServerLevel level, ServerPlayer player, Village owned) {
      CityStats stats = CityStatsService.getOrRefresh(level, owned);
      CultureStandingSavedData standing = CultureStandingSavedData.get(level);
      ResourceLocation ownCulture = owned.getCultureId();
      List<VillageCreateHubPayload.CultureStandingRow> standings = new ArrayList<>();
      for (ResourceLocation other : CultureDiplomacySimulator.collectNonLoneCultures(VillageSavedData.get(level).getVillageManager())) {
         if (other.equals(ownCulture)) {
            continue;
         }
         Culture c = ModCultures.getCulture(other);
         standings.add(
            new VillageCreateHubPayload.CultureStandingRow(
               other.toString(),
               c != null ? c.displayName() : other.getPath(),
               standing.getStanding(ownCulture, other),
               standing.isAtWar(ownCulture, other)
            )
         );
      }
      standings.sort(Comparator.comparing(VillageCreateHubPayload.CultureStandingRow::displayName));

      List<VillageCreateHubPayload.LogRow> log = new ArrayList<>();
      for (CultureDiplomacyLogEntry e : standing.getDiplomacyLog()) {
         log.add(new VillageCreateHubPayload.LogRow(e.day(), e.action(), e.cultureA().getPath(), e.cultureB().getPath(), e.standingAfter()));
      }
      if (log.size() > 20) {
         log = new ArrayList<>(log.subList(log.size() - 20, log.size()));
      }

      String villageName = owned.getVillageName() != null ? owned.getVillageName() : "";
      return new VillageCreateHubPayload(
         owned.getId().uuid().toString(),
         villageName,
         ownCulture.toString(),
         stats.population(),
         stats.buildings(),
         stats.openJobSlots(),
         stats.openBeds(),
         stats.threatLevel(),
         standings,
         log
      );
   }
}
