package org.millenaire.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.network.ControlledMilitaryPayload;
import org.millenaire.village.panel.MilitaryPanelGenerator;
import org.millenaire.village.panel.PanelLine;

public final class ControlledMilitaryService {
   private ControlledMilitaryService() {
   }

   public static ControlledMilitaryPayload buildPayload(Village village, ServerLevel level) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      List<ControlledMilitaryPayload.RelationEntry> relations = new ArrayList<>();

      for (Entry<VillageId, Integer> relEntry : village.getRelations().entrySet()) {
         Village other = vm.getVillage(relEntry.getKey());
         if (other != null
            && (village.getParentVillageId() == null || !village.getParentVillageId().equals(other.getId()))
            && (other.getParentVillageId() == null || !other.getParentVillageId().equals(village.getId()))) {
            String otherName = other.getVillageName() != null ? other.getVillageName() : other.getVillageTypeId().getPath();
            Culture otherCulture = ModCultures.getCulture(other.getCultureId());
            String otherCultureName = otherCulture != null ? otherCulture.displayName() : "?";
            int rel = relEntry.getValue();
            String relLabel = VillageRelations.getRelationKey(rel);
            relations.add(new ControlledMilitaryPayload.RelationEntry(relEntry.getKey().uuid().toString(), otherName, otherCultureName, rel, relLabel));
         }
      }

      relations.sort(Comparator.comparing(ControlledMilitaryPayload.RelationEntry::villageName));
      String currentRaidTargetVillageId = village.getRaidTarget() != null ? village.getRaidTarget().uuid().toString() : "";
      boolean raidInProgress = village.getRaidStart() > 0L;
      List<PanelLine> militaryLines = new ArrayList<>(MilitaryPanelGenerator.generateMilitary(village, level).lines());
      String villageName = village.getVillageName() != null ? village.getVillageName() : "";
      String ownerName = village.getOwnerName() != null ? village.getOwnerName() : "";
      return new ControlledMilitaryPayload(
         village.getId().uuid().toString(), villageName, ownerName, currentRaidTargetVillageId, raidInProgress, relations, militaryLines
      );
   }

   public static void sendRefresh(ServerPlayer player, ServerLevel level, Village village) {
      PacketDistributor.sendToPlayer(player, buildPayload(village, level), new CustomPacketPayload[0]);
   }
}
