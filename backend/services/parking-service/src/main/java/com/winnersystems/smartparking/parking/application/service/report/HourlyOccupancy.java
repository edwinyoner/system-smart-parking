package com.winnersystems.smartparking.parking.application.service.report;

import com.winnersystems.smartparking.parking.application.dto.query.ReportDto.HourSlot;
import com.winnersystems.smartparking.parking.application.dto.query.StayWindow;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Ocupación hora por hora a partir de las estadías (no de contadores): para cada hora cuenta
 * entradas, salidas, vehículos, los que estaban dentro al empezar y el máximo a la vez.
 */
final class HourlyOccupancy {

   private HourlyOccupancy() {
   }

   /**
    * @param dayStart medianoche del día
    * @param until    hasta dónde hay datos (fin del día, o ahora si es hoy)
    */
   static List<HourSlot> compute(Collection<StayWindow> windows, LocalDateTime dayStart, LocalDateTime until) {
      List<StayWindow> valid = windows.stream().filter(w -> !w.cancelled()).toList();
      List<HourSlot> slots = new ArrayList<>(24);
      for (int h = 0; h < 24; h++) {
         LocalDateTime start = dayStart.plusHours(h);
         LocalDateTime end = start.plusHours(1);
         if (!start.isBefore(until)) {
            slots.add(new HourSlot(h, 0, 0, 0, 0, 0, true));
            continue;
         }
         LocalDateTime cut = end.isAfter(until) ? until : end;
         int entries = 0;
         int exits = 0;
         int atStart = 0;
         Set<Long> vehicles = new HashSet<>();
         // Cambios dentro de la hora: +1 entrada, -1 salida (a la misma hora, primero la salida)
         List<long[]> events = new ArrayList<>();
         for (StayWindow w : valid) {
            if (inRange(w.entryTime(), start, cut)) {
               entries++;
            }
            if (w.exitTime() != null && inRange(w.exitTime(), start, cut)) {
               exits++;
            }
            if (w.overlaps(start, cut)) {
               vehicles.add(w.vehicleId());
            }
            if (w.insideAt(start)) {
               atStart++;
            } else if (w.entryTime().isAfter(start) && w.entryTime().isBefore(cut)) {
               events.add(new long[]{nanos(w.entryTime(), start), 1});
            }
            if (w.exitTime() != null && w.exitTime().isAfter(start) && w.exitTime().isBefore(cut)) {
               events.add(new long[]{nanos(w.exitTime(), start), -1});
            }
         }
         events.sort(Comparator.<long[]>comparingLong(e -> e[0]).thenComparingLong(e -> e[1]));
         int current = atStart;
         int max = atStart;
         for (long[] e : events) {
            current += (int) e[1];
            max = Math.max(max, current);
         }
         slots.add(new HourSlot(h, entries, exits, vehicles.size(), atStart, max, false));
      }
      return slots;
   }

   /** La hora con más vehículos a la vez (la primera si empatan); null si nunca hubo ninguno */
   static Integer peakHour(List<HourSlot> slots) {
      HourSlot best = null;
      for (HourSlot s : slots) {
         if (s.maxOccupied() > 0 && (best == null || s.maxOccupied() > best.maxOccupied())) {
            best = s;
         }
      }
      return best == null ? null : best.hour();
   }

   static int peakOccupied(List<HourSlot> slots) {
      return slots.stream().mapToInt(HourSlot::maxOccupied).max().orElse(0);
   }

   private static boolean inRange(LocalDateTime t, LocalDateTime from, LocalDateTime to) {
      return !t.isBefore(from) && t.isBefore(to);
   }

   private static long nanos(LocalDateTime t, LocalDateTime base) {
      return java.time.Duration.between(base, t).toNanos();
   }
}
