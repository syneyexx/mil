package org.millenaire.religion.calendar;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.religion.content.RitualDefinition;

/**
 * Minecraft-day calendar helpers for religious scheduling.
 */
public final class ReligionCalendarService {
   public ReligionCalendarService() {
   }

   public long dayNumber(ServerLevel level) {
      return level.getDayTime() / 24000L;
   }

   public long dayTimeOfDay(ServerLevel level) {
      return level.getDayTime() % 24000L;
   }

   public boolean withinHourWindow(ServerLevel level, RitualDefinition ritual) {
      if (ritual.minimumHour() < 0 && ritual.maximumHour() < 0) {
         return true;
      }
      long t = dayTimeOfDay(level);
      if (ritual.minimumHour() >= 0 && t < ritual.minimumHour()) {
         return false;
      }
      if (ritual.maximumHour() >= 0 && t > ritual.maximumHour()) {
         return false;
      }
      return true;
   }

   public boolean matchesRecurringDay(ServerLevel level, RitualDefinition ritual) {
      if (ritual.dayModulo() <= 0) {
         return true;
      }
      long day = dayNumber(level);
      return Math.floorMod(day - ritual.dayOffset(), ritual.dayModulo()) == 0;
   }

   public boolean cooldownElapsed(long lastCeremonyDay, RitualDefinition ritual, long currentDay) {
      if (ritual.cooldownDays() <= 0 || lastCeremonyDay < 0) {
         return true;
      }
      return currentDay - lastCeremonyDay >= ritual.cooldownDays();
   }
}
