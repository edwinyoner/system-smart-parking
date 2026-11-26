package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Placas dudosas (confianza < 0.85) que confirma o descarta un operador */
class PlateReviewTest {

   private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 9, 0);

   private static PlateReview entry(String detected) {
      return PlateReview.open("cap-10", "entry", 1L, 2L, "CAM-ENT-01", detected, 0.62,
            "https://storage/cap-10.jpg", NOW);
   }

   @Test
   @DisplayName("se abre pendiente, con la placa leída tal cual y una sugerencia normalizada")
   void opensPending() {
      PlateReview r = entry("abc 123");
      assertThat(r.isPending()).isTrue();
      assertThat(r.isEntry()).isTrue();
      assertThat(r.getDirection()).isEqualTo(PlateReview.DIRECTION_ENTRY);
      assertThat(r.getDetectedPlate()).isEqualTo("ABC 123");
      assertThat(r.getSuggestedPlate()).isEqualTo("ABC-123");
   }

   @Test
   @DisplayName("si la IA leyó algo sin formato válido, no hay sugerencia pero sí se abre")
   void opensWithUnreadablePlate() {
      assertThat(entry("A8C-12").getSuggestedPlate()).isNull();
      assertThat(entry(null).getDetectedPlate()).isNull();
   }

   @Test
   @DisplayName("valida captura, dirección, parqueo, zona de entrada y confianza")
   void validatesOpening() {
      assertThatThrownBy(() -> PlateReview.open(" ", "ENTRY", 1L, 2L, null, null, 0.5, null, NOW))
            .isInstanceOf(ParkingDomainException.class).hasMessageContaining("captura");
      assertThatThrownBy(() -> PlateReview.open("c", "SIDE", 1L, 2L, null, null, 0.5, null, NOW))
            .hasMessageContaining("Dirección");
      assertThatThrownBy(() -> PlateReview.open("c", "ENTRY", null, 2L, null, null, 0.5, null, NOW))
            .hasMessageContaining("parqueo");
      assertThatThrownBy(() -> PlateReview.open("c", "ENTRY", 1L, null, null, null, 0.5, null, NOW))
            .hasMessageContaining("zona");
      assertThatThrownBy(() -> PlateReview.open("c", "ENTRY", 1L, 2L, null, null, null, null, NOW))
            .hasMessageContaining("confianza");
      // La salida no necesita zona
      assertThat(PlateReview.open("c", "EXIT", 1L, null, null, null, 0.5, null, NOW).isPending()).isTrue();
   }

   @Test
   @DisplayName("confirmar normaliza la placa corregida, registra quién y se enlaza a la estadía")
   void confirms() {
      PlateReview r = entry("A8C-123");
      assertThat(r.confirm("abc123", 7L, NOW.plusMinutes(1))).isEqualTo("ABC-123");
      assertThat(r.isConfirmed()).isTrue();
      assertThat(r.getResolvedBy()).isEqualTo(7L);
      assertThat(r.getResolvedAt()).isEqualTo(NOW.plusMinutes(1));
      r.linkTransaction(55L);
      assertThat(r.getTransactionId()).isEqualTo(55L);
   }

   @Test
   @DisplayName("confirmar con una placa inválida falla y la revisión sigue pendiente")
   void confirmRejectsInvalidPlate() {
      PlateReview r = entry("ABC-123");
      assertThatThrownBy(() -> r.confirm("XX", 7L, NOW)).hasMessageContaining("formato");
      assertThat(r.isPending()).isTrue();
   }

   @Test
   @DisplayName("descartar exige motivo; una revisión resuelta ya no cambia")
   void discardAndImmutable() {
      PlateReview r = entry("ABC-123");
      assertThatThrownBy(() -> r.discard(" ", 7L, NOW)).hasMessageContaining("motivo");
      r.discard("No era un vehículo", 7L, NOW);
      assertThat(r.isDiscarded()).isTrue();
      assertThatThrownBy(() -> r.confirm("ABC-123", 7L, NOW)).hasMessageContaining("ya fue resuelta");
      assertThatThrownBy(() -> r.linkTransaction(1L)).hasMessageContaining("confirmada");
   }
}
