package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Respuestas de los reportes de operación (sin dinero: los ingresos los da payment-service) */
public final class ReportDto {

   private ReportDto() {
   }

   /**
    * Lo que pasó en un parqueo o una zona durante el día.
    *
    * @param entries        vehículos que entraron ese día
    * @param exits          vehículos que salieron ese día (pudieron entrar antes)
    * @param vehicles       vehículos distintos que estuvieron dentro en algún momento del día
    *                       (incluye los que ya estaban de días anteriores)
    * @param stays          estadías que tocaron el día (un vehículo puede entrar dos veces)
    * @param insideAtClose  dentro al terminar el día (o ahora, si es hoy)
    * @param cancelled      estadías canceladas que habían entrado ese día (no cuentan en lo demás)
    * @param averageStayMinutes tiempo promedio de las que salieron ese día; null si ninguna salió
    * @param infractions    infracciones registradas ese día
    */
   public record DaySummary(
         int entries,
         int exits,
         int vehicles,
         int stays,
         int insideAtClose,
         int cancelled,
         Integer averageStayMinutes,
         int infractions
   ) {
   }

   public record ZoneDay(
         Long zoneId,
         String zoneName,
         String zoneCode,
         int totalSpaces,
         DaySummary summary,
         Integer peakHour,
         int peakOccupied
   ) {
   }

   /**
    * @param totalSpaces espacios de hoy (la capacidad no tiene historial)
    * @param peakHour    hora con más vehículos dentro a la vez; null si no hubo ninguno
    */
   public record ParkingDay(
         Long parkingId,
         String parkingName,
         String parkingCode,
         int totalSpaces,
         DaySummary summary,
         Integer peakHour,
         int peakOccupied,
         List<ZoneDay> zones
   ) {
   }

   /** @param until hasta qué hora hay datos: el fin del día, o ahora si el día es hoy */
   public record DailyReport(
         LocalDate date,
         LocalDateTime until,
         DaySummary totals,
         List<ParkingDay> parkings
   ) {
   }

   /**
    * Una hora del día.
    *
    * @param vehicles        vehículos distintos que estuvieron dentro en algún momento de la hora
    * @param occupiedAtStart dentro al empezar la hora
    * @param maxOccupied     el máximo dentro a la vez durante la hora
    * @param future          la hora aún no empieza (todo en cero)
    */
   public record HourSlot(
         int hour,
         int entries,
         int exits,
         int vehicles,
         int occupiedAtStart,
         int maxOccupied,
         boolean future
   ) {
   }

   public record ZoneOption(Long id, String name, String code) {
   }

   /**
    * Ocupación por hora de un parqueo (o de una de sus zonas).
    *
    * @param capacity espacios de hoy del parqueo o de la zona, para el porcentaje
    */
   public record OccupancyReport(
         LocalDate date,
         LocalDateTime until,
         Long parkingId,
         String parkingName,
         Long zoneId,
         String zoneName,
         int capacity,
         List<HourSlot> hours,
         Integer peakHour,
         int peakOccupied,
         int entries,
         int exits,
         int vehicles,
         List<ZoneOption> zones
   ) {
   }
}
