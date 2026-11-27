package com.winnersystems.smartparking.payment.application.service.report;

import com.winnersystems.smartparking.payment.application.dto.query.IncomeReportDto.IncomeReport;
import com.winnersystems.smartparking.payment.application.port.output.PaymentPersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncomeReportServiceTest {

   static final ZoneId PERU = ZoneId.of("America/Lima");
   static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 11, 27, 10, 0).atZone(PERU).toInstant(), PERU);

   private PaymentPersistencePort payments;
   private IncomeReportService service;

   static Payment paid(PaymentConcept concept, String type, long parking, String amount, String refunded, int day) {
      Payment p = new Payment();
      p.setConcept(concept);
      p.setPaymentTypeCode(type);
      p.setPaymentTypeName(type.equals("QR") ? "Pago con QR" : "Efectivo");
      p.setParkingId(parking);
      p.setParkingName(parking == 1 ? "Parqueo San Martín" : "Parqueo Las Banderas");
      p.setAmount(new BigDecimal(amount));
      p.setRefundedAmount(new BigDecimal(refunded));
      p.setStatus(refunded.equals("0") ? PaymentStatus.COMPLETED : PaymentStatus.PARTIALLY_REFUNDED);
      p.setPaidAt(LocalDateTime.of(2026, 11, day, 9, 0));
      return p;
   }

   @BeforeEach
   void setUp() {
      payments = mock(PaymentPersistencePort.class);
      when(payments.findPaidBetween(any(), any(), any())).thenReturn(List.of(
            paid(PaymentConcept.PARKING_STAY, "QR", 1, "10.00", "0", 25),
            paid(PaymentConcept.PARKING_STAY, "CASH", 1, "5.00", "2.00", 27),
            paid(PaymentConcept.FINE, "QR", 2, "50.00", "0", 27)
      ));
      service = new IncomeReportService(payments, CLOCK);
   }

   @Test
   @DisplayName("totales netos, un día por fecha (con ceros) y agrupado por tipo y parqueo")
   void income() {
      IncomeReport r = service.income(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 27), null);

      assertThat(r.totals().payments()).isEqualTo(3);
      assertThat(r.totals().gross()).isEqualByComparingTo("65");
      assertThat(r.totals().refunded()).isEqualByComparingTo("2");
      assertThat(r.totals().net()).isEqualByComparingTo("63");
      assertThat(r.totals().stays()).isEqualByComparingTo("13");
      assertThat(r.totals().fines()).isEqualByComparingTo("50");

      assertThat(r.byDay()).hasSize(3);
      assertThat(r.byDay().get(1).payments()).isZero();
      assertThat(r.byDay().get(2).net()).isEqualByComparingTo("53");

      assertThat(r.byPaymentType().get(0).code()).isEqualTo("QR");
      assertThat(r.byPaymentType().get(0).net()).isEqualByComparingTo("60");
      assertThat(r.byParking().get(0).parkingName()).isEqualTo("Parqueo Las Banderas");
      assertThat(r.byParking().get(1).stays()).isEqualByComparingTo("13");

      verify(payments).findPaidBetween(LocalDateTime.of(2026, 11, 25, 0, 0), LocalDateTime.of(2026, 11, 28, 0, 0), null);
   }

   @Test
   @DisplayName("por defecto hoy; rechaza rango invertido, inicio futuro y más de un año")
   void validation() {
      assertThat(service.income(null, null, 1L).byDay()).hasSize(1);
      assertThatThrownBy(() -> service.income(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 1), null))
            .isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> service.income(LocalDate.of(2026, 11, 28), null, null))
            .isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> service.income(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 27), null))
            .isInstanceOf(PaymentDomainException.class);
   }
}
