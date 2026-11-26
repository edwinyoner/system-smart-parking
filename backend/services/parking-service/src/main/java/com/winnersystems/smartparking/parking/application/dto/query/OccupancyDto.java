package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapa de ocupación de un parqueo: sus zonas y, en cada una, todos sus espacios con su estado.
 * Se calcula en el momento a partir del estado real de cada espacio (regla 5).
 */
public final class OccupancyDto {

   private OccupancyDto() {}

   /** Espacios contados por estado */
   public record StatusCounts(
         int total,
         int available,
         int occupied,
         int maintenance,
         int outOfService
   ) {
      public static StatusCounts empty() {
         return new StatusCounts(0, 0, 0, 0, 0);
      }

      /** Porcentaje de ocupación sobre el total, con un decimal */
      public double occupancyPercentage() {
         return total == 0 ? 0.0 : Math.round(occupied * 1000.0 / total) / 10.0;
      }
   }

   /** Resumen de un parqueo para elegir cuál ver */
   public record ParkingSummary(
         Long id,
         String name,
         String code,
         String status,
         boolean operational,
         int totalZones,
         int totalSpaces,
         int availableSpaces,     // libres en zonas activas: los que reciben un vehículo ahora
         int occupiedSpaces,
         int unavailableSpaces,   // mantenimiento, fuera de servicio o en zonas no activas
         double occupancyPercentage
   ) {}

   /**
    * Un espacio del mapa. Con un vehículo dentro, transactionId, placa y hora de entrada
    * (solo si quien consulta tiene transactions.view). Un espacio ocupado por sensor o
    * cámara sin estadía registrada queda sin placa.
    */
   public record SpaceCell(
         Long id,
         String code,
         String type,
         String status,
         boolean hasSensor,
         Long transactionId,
         String plateNumber,
         LocalDateTime occupiedSince
   ) {}

   /** Una zona con sus espacios ordenados por código */
   public record ZoneMap(
         Long id,
         String code,
         String name,
         String status,
         boolean operational,      // recibe vehículos (zona y parqueo activos)
         StatusCounts counts,
         int availableForEntry,    // libres que de verdad reciben un vehículo
         List<SpaceCell> spaces
   ) {}

   /** Mapa completo de un parqueo */
   public record ParkingMap(
         Long id,
         String name,
         String code,
         String address,
         String status,
         boolean operational,
         StatusCounts counts,
         int availableForEntry,
         boolean showsVehicles,    // false: quien consulta no tiene transactions.view (sin placas)
         LocalDateTime generatedAt,
         List<ZoneMap> zones
   ) {}
}
