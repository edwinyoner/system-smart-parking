package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La placa se guarda siempre como ABC-123, la escriba el operador o la lea la IA */
class PlateNumberTest {

   @Test
   @DisplayName("normaliza espacios, guiones, minúsculas y el formato nuevo")
   void normalizes() {
      assertThat(PlateNumber.normalize("abc 123")).isEqualTo("ABC-123");
      assertThat(PlateNumber.normalize("ABC123")).isEqualTo("ABC-123");
      assertThat(PlateNumber.normalize(" ABC-123 ")).isEqualTo("ABC-123");
      assertThat(PlateNumber.normalize("a1b.234")).isEqualTo("A1B-234");
   }

   @Test
   @DisplayName("rechaza vacías y formatos que no son peruanos")
   void rejectsInvalid() {
      assertThatThrownBy(() -> PlateNumber.normalize(null)).isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> PlateNumber.normalize("  ")).isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> PlateNumber.normalize("AB-1234")).hasMessageContaining("formato");
      assertThatThrownBy(() -> PlateNumber.normalize("123-ABC")).hasMessageContaining("formato");
      assertThat(PlateNumber.isValid("ABCD12")).isFalse();
      assertThat(PlateNumber.isValid("ABC-123")).isTrue();
   }
}
