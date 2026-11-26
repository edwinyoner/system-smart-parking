package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Información pública de los parqueos (sin login): la consumen la app móvil y los paneles LED.
 *
 * Solo datos para el conductor: ubicación, horario, tarifas y espacios libres.
 * Nunca incluye el responsable, placas, operadores ni datos de auditoría.
 * La disponibilidad se calcula contando los espacios reales (no contadores guardados).
 */
public final class PublicParkingDto {

   private PublicParkingDto() {
   }

   /** Turno con su horario en formato HH:mm */
   public record ShiftInfo(String name, String startTime, String endTime, boolean crossesMidnight) {
   }

   /** Tarifa y reglas de cobro (por hora o fracción) */
   public record RateInfo(
         String name,
         BigDecimal amountPerHour,
         String currency,
         int billingUnitMinutes,
         int graceMinutes,
         int toleranceMinutes,
         int minimumMinutes
   ) {
   }

   /** Un turno del horario con la tarifa que se cobra en él */
   public record ScheduleItem(ShiftInfo shift, RateInfo rate) {
   }

   /** Espacios de una zona: libres solo cuenta los que reciben vehículos ahora */
   /**
    * @param totalSpaces     todos los espacios de la zona
    * @param availableSpaces libres que reciben vehículos ahora (0 si no atiende)
    * @param occupiedSpaces  con un vehículo
    * @param usableSpaces    libres + ocupados (sin mantenimiento ni fuera de servicio): el total del panel
    */
   public record ZoneAvailability(
         Long id,
         String code,
         String name,
         boolean operational,
         int totalSpaces,
         int availableSpaces,
         int occupiedSpaces,
         int usableSpaces
   ) {
   }

   /** Tarjeta de un parqueo en el listado (mapa o lista de la app) */
   public record ParkingCard(
         Long id,
         String name,
         String code,
         String address,
         Double latitude,
         Double longitude,
         String status,
         boolean operational,
         boolean openNow,
         int totalSpaces,
         int availableSpaces,
         int occupiedSpaces,
         int usableSpaces,
         double occupancyPercentage,
         ShiftInfo currentShift,
         RateInfo currentRate
   ) {
   }

   /** Detalle de un parqueo: tarjeta + descripción, zonas y horario completo */
   public record ParkingDetail(
         ParkingCard parking,
         String description,
         List<ZoneAvailability> zones,
         List<ScheduleItem> schedule,
         LocalDateTime generatedAt
   ) {
   }

   /**
    * Lo mínimo para un panel LED: se consulta cada pocos segundos, así que no trae horario ni tarifas.
    * availableSpaces es 0 si el parqueo no atiende o no está operativo.
    */
   public record Availability(
         Long parkingId,
         String code,
         String name,
         String status,
         boolean operational,
         boolean openNow,
         int totalSpaces,
         int availableSpaces,
         int occupiedSpaces,
         int usableSpaces,
         List<ZoneAvailability> zones,
         LocalDateTime generatedAt
   ) {
   }

   // ========================= MAPA DE ESPACIOS =========================

   /** Estados públicos de un espacio: no se dice por qué no está disponible ni quién lo ocupa */
   public static final String CELL_AVAILABLE = "AVAILABLE";
   public static final String CELL_OCCUPIED = "OCCUPIED";
   public static final String CELL_UNAVAILABLE = "UNAVAILABLE";

   /** Un espacio en el mapa: sin placa, estadía ni sensor */
   public record SpaceCell(String code, String type, String status) {
   }

   public record ZoneSpaces(
         String code,
         String name,
         boolean operational,
         int available,
         int occupied,
         int unavailable,
         List<SpaceCell> spaces
   ) {
   }

   /** Mapa de espacios por zona para la app (GET /public/parkings/{id}/spaces) */
   public record SpaceMap(
         Long parkingId,
         String code,
         String name,
         boolean operational,
         boolean openNow,
         int totalSpaces,
         int availableSpaces,
         List<ZoneSpaces> zones,
         LocalDateTime generatedAt
   ) {
   }
}
