package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfractionTest {

   static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 10, 0);
   static final LocalDate TODAY = NOW.toLocalDate();

   static Infraction.RegisterData data(BigDecimal fine, LocalDate due) {
      return new Infraction.RegisterData(1L, 10L, 100L, null, 5L, "abc123", null,
            "wrong_space", "moderate", "  Estacionado en espacio reservado  ",
            List.of("https://fotos.example.com/a.jpg", "https://fotos.example.com/a.jpg"),
            fine, due, null, " ", 9L);
   }

   static Infraction withFine(String amount) {
      Infraction i = Infraction.register(data(new BigDecimal(amount), null), NOW, 7);
      i.setId(7L);
      return i;
   }

   @Nested
   @DisplayName("registro")
   class Register {

      @Test
      @DisplayName("normaliza placa, tipo, gravedad y texto; quita evidencia repetida; vence en 7 días")
      void normalizes() {
         Infraction i = Infraction.register(data(new BigDecimal("50"), null), NOW, 7);
         assertThat(i.getPlateNumber()).isEqualTo("ABC-123");
         assertThat(i.getInfractionType()).isEqualTo(Infraction.TYPE_WRONG_SPACE);
         assertThat(i.getSeverity()).isEqualTo(Infraction.SEVERITY_MODERATE);
         assertThat(i.getDescription()).isEqualTo("Estacionado en espacio reservado");
         assertThat(i.getEvidenceList()).containsExactly("https://fotos.example.com/a.jpg");
         assertThat(i.getNotes()).isNull();
         assertThat(i.getFineAmount()).isEqualByComparingTo("50.00");
         assertThat(i.getFineDueDate()).isEqualTo(TODAY.plusDays(7));
         assertThat(i.getDetectedAt()).isEqualTo(NOW);
         assertThat(i.displayStatus(TODAY)).isEqualTo(Infraction.STATUS_PENDING);
         assertThat(i.isFinePayable()).isTrue();
      }

      @Test
      @DisplayName("sin multa no hay nada que cobrar")
      void withoutFine() {
         Infraction i = Infraction.register(data(null, null), NOW, 7);
         assertThat(i.hasFine()).isFalse();
         assertThat(i.isFinePayable()).isFalse();
         assertThat(i.getFineAmountDue()).isEqualByComparingTo("0");
      }

      @Test
      @DisplayName("rechaza tipo, gravedad, multa, fecha y evidencia no válidos")
      void rejectsInvalid() {
         var base = data(null, null);
         assertThatThrownBy(() -> Infraction.register(copy(base, "SPEEDING", "MINOR", null, null, null), NOW, 7))
               .isInstanceOf(ParkingDomainException.class).hasMessageContaining("Tipo");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "HIGH", null, null, null), NOW, 7))
               .hasMessageContaining("Gravedad");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "MINOR", new BigDecimal("0"), null, null), NOW, 7))
               .hasMessageContaining("mayor que cero");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "MINOR", new BigDecimal("10.555"), null, null), NOW, 7))
               .hasMessageContaining("2 decimales");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "MINOR", new BigDecimal("20000"), null, null), NOW, 7))
               .hasMessageContaining("superar");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "MINOR", new BigDecimal("20"), TODAY.minusDays(1), null), NOW, 7))
               .hasMessageContaining("pasado");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "MINOR", null, null, NOW.plusHours(1)), NOW, 7))
               .hasMessageContaining("futuro");
         assertThatThrownBy(() -> Infraction.register(copy(base, "OTHER", "MINOR", null, null, NOW.minusDays(31)), NOW, 7))
               .hasMessageContaining("30 días");
         assertThatThrownBy(() -> Infraction.register(new Infraction.RegisterData(1L, 10L, null, null, 5L, "ABC123",
               null, "OTHER", "MINOR", "Descripción", List.of("javascript:alert(1)"), null, null, null, null, 9L), NOW, 7))
               .hasMessageContaining("evidencia");
         assertThatThrownBy(() -> Infraction.register(new Infraction.RegisterData(1L, null, 100L, null, 5L, "ABC123",
               null, "OTHER", "MINOR", "Descripción", null, null, null, null, null, 9L), NOW, 7))
               .hasMessageContaining("zona");
      }

      @Test
      @DisplayName("el código se genera con el año de la infracción y el ID")
      void code() {
         Infraction i = withFine("10");
         i.generateInfractionCode();
         assertThat(i.getInfractionCode()).isEqualTo("INF-2026-000007");
      }

      private Infraction.RegisterData copy(Infraction.RegisterData b, String type, String severity,
                                           BigDecimal fine, LocalDate due, LocalDateTime detectedAt) {
         return new Infraction.RegisterData(b.parkingId(), b.zoneId(), b.spaceId(), b.transactionId(),
               b.vehicleId(), b.plateNumber(), b.customerId(), type, severity, b.description(), null,
               fine, due, detectedAt, null, b.registeredBy());
      }
   }

   @Nested
   @DisplayName("pago de la multa")
   class Payment {

      @Test
      @DisplayName("parcial: falta la diferencia; completo: se resuelve como PAID y ya no se cobra")
      void paid() {
         Infraction i = withFine("50.00");
         i.applyFineSettlement(new BigDecimal("20.00"), NOW, 1L);
         assertThat(i.isFinePaid()).isFalse();
         assertThat(i.getFineAmountDue()).isEqualByComparingTo("30.00");
         assertThat(i.isEditable()).isFalse();

         i.applyFineSettlement(new BigDecimal("50.00"), NOW, 2L);
         assertThat(i.isResolved()).isTrue();
         assertThat(i.displayStatus(TODAY)).isEqualTo(Infraction.RESOLUTION_PAID);
         assertThat(i.getFinePaymentReference()).isEqualTo("PAGO-2");
         assertThat(i.getResolvedBy()).isNull();
         assertThat(i.isFinePayable()).isFalse();
      }

      @Test
      @DisplayName("si se devuelve el pago vuelve a estar pendiente y se puede cobrar")
      void refunded() {
         Infraction i = withFine("50.00");
         i.applyFineSettlement(new BigDecimal("50.00"), NOW, 2L);
         i.applyFineSettlement(BigDecimal.ZERO, null, null);
         assertThat(i.isPending()).isTrue();
         assertThat(i.getResolutionType()).isNull();
         assertThat(i.isFinePayable()).isTrue();
         assertThat(i.isEditable()).isTrue();
      }

      @Test
      @DisplayName("vencida: pendiente, con saldo y pasada la fecha límite")
      void overdue() {
         Infraction i = withFine("50.00");
         assertThat(i.displayStatus(TODAY.plusDays(7))).isEqualTo(Infraction.STATUS_PENDING);
         assertThat(i.displayStatus(TODAY.plusDays(8))).isEqualTo(Infraction.DISPLAY_OVERDUE);
      }
   }

   @Nested
   @DisplayName("edición y resolución")
   class Resolution {

      @Test
      @DisplayName("se edita y se cambia o quita la multa mientras no tenga pagos")
      void editAndChangeFine() {
         Infraction i = withFine("50.00");
         i.edit(new Infraction.EditData("other", "severe", "Otro motivo", List.of(), "nota"), 3L, NOW);
         assertThat(i.getInfractionType()).isEqualTo(Infraction.TYPE_OTHER);
         assertThat(i.getEvidence()).isNull();
         assertThat(i.getUpdatedBy()).isEqualTo(3L);

         i.changeFine(new BigDecimal("80"), TODAY.plusDays(3), 3L, NOW, 7);
         assertThat(i.getFineAmount()).isEqualByComparingTo("80.00");
         assertThat(i.getFineDueDate()).isEqualTo(TODAY.plusDays(3));

         i.changeFine(null, null, 3L, NOW, 7);
         assertThat(i.hasFine()).isFalse();
         assertThat(i.getFineDueDate()).isNull();

         i.changeFine(new BigDecimal("30"), null, 3L, NOW, 7);
         i.applyFineSettlement(new BigDecimal("10"), NOW, 4L);
         assertThatThrownBy(() -> i.changeFine(new BigDecimal("5"), null, 3L, NOW, 7))
               .isInstanceOf(ResourceConflictException.class);
         assertThatThrownBy(() -> i.edit(new Infraction.EditData("OTHER", "MINOR", "Descripción", null, null), 3L, NOW))
               .isInstanceOf(ResourceConflictException.class);
      }

      @Test
      @DisplayName("anular exige motivo y no se permite con pagos")
      void dismiss() {
         Infraction i = withFine("50.00");
         assertThatThrownBy(() -> i.dismiss("no", 3L, NOW)).hasMessageContaining("motivo");
         i.dismiss("Placa mal registrada", 3L, NOW);
         assertThat(i.displayStatus(TODAY)).isEqualTo(Infraction.RESOLUTION_DISMISSED);
         assertThat(i.isFinePayable()).isFalse();
         assertThat(i.getResolvedBy()).isEqualTo(3L);
         assertThatThrownBy(() -> i.dismiss("Otra vez", 3L, NOW)).isInstanceOf(ResourceConflictException.class);

         Infraction paidPart = withFine("50.00");
         paidPart.applyFineSettlement(new BigDecimal("10"), NOW, 1L);
         assertThatThrownBy(() -> paidPart.dismiss("No corresponde", 3L, NOW))
               .isInstanceOf(ResourceConflictException.class).hasMessageContaining("devuélvelos");
      }

      @Test
      @DisplayName("amonestación solo para infracciones sin multa")
      void warning() {
         Infraction withFine = withFine("50.00");
         assertThatThrownBy(() -> withFine.closeWithWarning(null, 3L, NOW)).isInstanceOf(ResourceConflictException.class);

         Infraction noFine = Infraction.register(data(null, null), NOW, 7);
         noFine.closeWithWarning("  ", 3L, NOW);
         assertThat(noFine.displayStatus(TODAY)).isEqualTo(Infraction.RESOLUTION_WARNING);
         assertThat(noFine.getResolution()).isNull();
      }
   }
}
