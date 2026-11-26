package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.occupancy;

import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto;
import com.winnersystems.smartparking.parking.application.port.input.occupancy.GetOccupancyUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mapa de ocupación en tiempo real (el panel lo consulta cada pocos segundos).
 * Exige occupancy.view; las placas solo se incluyen con transactions.view.
 * Las respuestas son los records de aplicación tal cual: no exponen entidades de dominio.
 *
 * Ruta pública en el gateway: /api/parking-service/v1/occupancy
 */
@RestController
@RequestMapping("/occupancy")
public class OccupancyRestAdapter {

   private final GetOccupancyUseCase occupancyUseCase;

   public OccupancyRestAdapter(GetOccupancyUseCase occupancyUseCase) {
      this.occupancyUseCase = occupancyUseCase;
   }

   @GetMapping("/parkings")
   @PreAuthorize("hasAuthority('occupancy.view')")
   public ResponseEntity<List<OccupancyDto.ParkingSummary>> listParkings() {
      return ResponseEntity.ok(occupancyUseCase.listParkings());
   }

   @GetMapping("/parkings/{parkingId}")
   @PreAuthorize("hasAuthority('occupancy.view')")
   public ResponseEntity<OccupancyDto.ParkingMap> getParkingMap(@PathVariable Long parkingId) {
      return ResponseEntity.ok(occupancyUseCase.getParkingMap(parkingId));
   }
}
