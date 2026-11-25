package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.model.Rate.BillingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateTest {

   private static Rate rate(String amount, Integer unit, Integer minimum, Integer grace, Integer tolerance) {
      return Rate.create("Auto", null, new BigDecimal(amount), new BillingRules(unit, minimum, grace, tolerance), true, 1L);
   }

   @Nested
   @DisplayName("valores por defecto: por hora o fracción, 5 min sin costo y 5 min de tolerancia")
   class HourOrFraction {

      @ParameterizedTest(name = "{0} min a S/ 2.00 la hora = S/ {1}")
      @CsvSource({"0,0.00", "4,0.00", "5,2.00", "30,2.00", "58,2.00", "60,2.00", "65,2.00", "66,4.00",
                  "118,4.00", "125,4.00", "126,6.00", "185,6.00", "186,8.00"})
      void examples(int minutes, String expected) {
         assertThat(rate("2.00", null, null, null, null).calculateCost(minutes)).isEqualByComparingTo(expected);
      }
   }

   @Test
   @DisplayName("tiempo sin costo: si sale antes es gratis; al cumplirlo ya cobra")
   void grace() {
      Rate r = rate("2.00", 60, 0, 10, 0);
      assertThat(r.calculateCost(9)).isEqualByComparingTo("0.00");
      assertThat(r.billableFractions(9)).isZero();
      assertThat(r.calculateCost(10)).isEqualByComparingTo("2.00");
      // Con 0 siempre se cobra
      assertThat(rate("2.00", 60, 0, 0, 0).calculateCost(0)).isEqualByComparingTo("2.00");
   }

   @Test
   @DisplayName("tolerancia: con 5 min, hasta 1 h 05 cuenta como 1 h")
   void tolerance() {
      Rate r = rate("2.00", 60, 0, 0, 5);
      assertThat(r.calculateCost(65)).isEqualByComparingTo("2.00");
      assertThat(r.calculateCost(66)).isEqualByComparingTo("4.00");
      assertThat(r.calculateCost(3)).isEqualByComparingTo("2.00");
   }

   @Test
   @DisplayName("fracción de 15 min con mínimo de 1 h")
   void quarterWithMinimum() {
      Rate r = rate("2.00", 15, 60, 0, 0);
      assertThat(r.getFractionAmount()).isEqualByComparingTo("0.50");
      assertThat(r.calculateCost(10)).isEqualByComparingTo("2.00");   // mínimo 1 h
      assertThat(r.calculateCost(61)).isEqualByComparingTo("2.50");   // 5 fracciones
      assertThat(r.calculateCost(90)).isEqualByComparingTo("3.00");
   }

   @Test
   @DisplayName("al minuto: proporcional")
   void perMinute() {
      Rate r = rate("3.00", 1, 0, 0, 0);
      assertThat(r.calculateCost(20)).isEqualByComparingTo("1.00");
      assertThat(r.calculateCost(70)).isEqualByComparingTo("3.50");
   }

   @Nested
   @DisplayName("validación")
   class Validation {

      @Test
      @DisplayName("precio mayor a 0 y con 2 decimales como máximo")
      void amount() {
         assertThatThrownBy(() -> rate("0", null, null, null, null)).isInstanceOf(ParkingDomainException.class);
         assertThatThrownBy(() -> rate("-1", null, null, null, null)).isInstanceOf(ParkingDomainException.class);
         assertThatThrownBy(() -> rate("2.555", null, null, null, null)).isInstanceOf(ParkingDomainException.class);
         assertThat(rate("2.5", null, null, null, null).getAmount()).isEqualByComparingTo("2.50");
      }

      @Test
      @DisplayName("la tolerancia debe ser menor que la fracción; rangos de fracción y gracia")
      void rules() {
         assertThatThrownBy(() -> rate("2", 15, 0, 0, 15)).isInstanceOf(ParkingDomainException.class);
         assertThatThrownBy(() -> rate("2", 0, 0, 0, 0)).isInstanceOf(ParkingDomainException.class);
         assertThatThrownBy(() -> rate("2", 60, 0, 121, 0)).isInstanceOf(ParkingDomainException.class);
         assertThatThrownBy(() -> rate("2", 60, -1, 0, 0)).isInstanceOf(ParkingDomainException.class);
      }

      @Test
      @DisplayName("siempre en soles y con auditoría")
      void defaults() {
         Rate r = rate("2", null, null, null, null);
         assertThat(r.getCurrency()).isEqualTo("PEN");
         assertThat(r.getBillingUnitMinutes()).isEqualTo(60);
         assertThat(r.getGraceMinutes()).isEqualTo(5);
         assertThat(r.getToleranceMinutes()).isEqualTo(5);
         // Al minuto, la tolerancia por defecto se ajusta a 0 (debe ser menor que la fracción)
         assertThat(rate("2", 1, null, null, null).getToleranceMinutes()).isZero();
         assertThat(r.getCreatedBy()).isEqualTo(1L);
         assertThat(r.isActive()).isTrue();
      }
   }

   @Test
   @DisplayName("eliminar y restaurar")
   void deleteAndRestore() {
      Rate r = rate("2", null, null, null, null);
      assertThatThrownBy(() -> r.restore(1L)).isInstanceOf(ParkingDomainException.class);
      r.markAsDeleted(5L);
      assertThat(r.isDeleted()).isTrue();
      assertThat(r.isActive()).isFalse();
      r.restore(7L);
      assertThat(r.isDeleted()).isFalse();
      assertThat(r.isActive()).isTrue();
      assertThat(r.getUpdatedBy()).isEqualTo(7L);
   }

   @Test
   @DisplayName("dos tarifas nuevas no son iguales; con el mismo ID sí")
   void equality() {
      Rate a = rate("2", null, null, null, null);
      Rate b = rate("2", null, null, null, null);
      assertThat(a).isNotEqualTo(b);
      a.setId(3L);
      b.setId(3L);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
   }
}
