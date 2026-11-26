package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.publicinfo;

import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto;
import com.winnersystems.smartparking.parking.application.port.input.publicinfo.GetPublicParkingInfoUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Endpoints públicos (sin login) para la app móvil y los paneles LED.
 * Por el gateway: /api/parking-service/v1/public/parkings/...
 *
 * Solo lectura. Se permite guardar la respuesta unos segundos (Cache-Control) porque
 * la app consulta cada 10-15 s y los paneles cada 10 s.
 */
@RestController
@RequestMapping("/public/parkings")
public class PublicParkingRestAdapter {

   private static final CacheControl SHORT_CACHE = CacheControl.maxAge(Duration.ofSeconds(5)).cachePublic();

   private final GetPublicParkingInfoUseCase useCase;

   public PublicParkingRestAdapter(GetPublicParkingInfoUseCase useCase) {
      this.useCase = useCase;
   }

   @GetMapping
   public ResponseEntity<List<PublicParkingDto.ParkingCard>> list() {
      return ResponseEntity.ok().cacheControl(SHORT_CACHE).body(useCase.listParkings());
   }

   @GetMapping("/{parkingId}")
   public ResponseEntity<PublicParkingDto.ParkingDetail> detail(@PathVariable Long parkingId) {
      return ResponseEntity.ok().cacheControl(SHORT_CACHE).body(useCase.getParking(parkingId));
   }

   @GetMapping("/{parkingId}/availability")
   public ResponseEntity<PublicParkingDto.Availability> availability(@PathVariable Long parkingId) {
      return ResponseEntity.ok().cacheControl(SHORT_CACHE).body(useCase.getAvailability(parkingId));
   }
}
