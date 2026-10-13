package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.item.MoneyHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageTreasuryService;

public final class BankPanelGenerator {
   private BankPanelGenerator() {
   }

   public static PanelContent generate(ServerLevel level, Village village) {
      List<PanelLine> lines = new ArrayList<>();
      boolean hasBank = VillageTreasuryService.hasDesignatedBank(village);
      BuildingInstance treasury = VillageTreasuryService.resolve(village);
      int balance = VillageTreasuryService.balance(level, village);

      if (!hasBank) {
         lines.add(PanelLine.translatable("panel.millenaire.bank.no_bank"));
         lines.add(PanelLine.translatable("panel.millenaire.bank.fallback_th"));
      } else {
         lines.add(PanelLine.translatable("panel.millenaire.bank.designated"));
      }

      lines.add(PanelLine.translatableWithArgs("panel.millenaire.bank.balance", MoneyHelper.formatPrice(balance), String.valueOf(balance)));

      if (treasury != null && treasury.getWarDamageLevel() > 0) {
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.bank.damaged", String.valueOf(treasury.getWarDamageLevel())));
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.bank.ledger_title"));
      List<VillageTreasuryService.LedgerEntry> ledger = VillageTreasuryService.ledger(village);
      if (ledger.isEmpty()) {
         lines.add(PanelLine.translatable("panel.millenaire.bank.ledger_empty"));
      } else {
         int shown = 0;
         for (VillageTreasuryService.LedgerEntry e : ledger) {
            if (shown++ >= 12) {
               break;
            }
            String sign = e.delta() >= 0 ? "+" : "";
            lines.add(PanelLine.text(sign + MoneyHelper.formatPrice(e.delta()) + "  " + e.reason()));
         }
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.bank.hint_deposit"));

      return new PanelContent(PanelType.BANK, "panel.millenaire.bank.title", lines, true, null);
   }
}
