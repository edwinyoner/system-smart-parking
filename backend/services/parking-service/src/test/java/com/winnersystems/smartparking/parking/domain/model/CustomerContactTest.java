package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerContactTest {

   static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 10, 0);

   @Test
   @DisplayName("normaliza celular y correo; vacío borra el dato")
   void changesContact() {
      Customer c = new Customer(1L, "43567890", "Juan", "Pérez");
      c.changeContact("987 654-321", " Juan@Correo.PE ", "  Jr. San Martín 123 ", 3L, NOW);
      assertThat(c.getPhone()).isEqualTo("987654321");
      assertThat(c.getEmail()).isEqualTo("juan@correo.pe");
      assertThat(c.getAddress()).isEqualTo("Jr. San Martín 123");
      assertThat(c.getUpdatedBy()).isEqualTo(3L);

      c.changeContact("", null, " ", 3L, NOW);
      assertThat(c.getPhone()).isNull();
      assertThat(c.getEmail()).isNull();
      assertThat(c.getAddress()).isNull();
   }

   @Test
   @DisplayName("rechaza celular y correo no válidos")
   void rejects() {
      Customer c = new Customer(1L, "43567890", "Juan", "Pérez");
      assertThatThrownBy(() -> c.changeContact("123456789", null, null, 3L, NOW))
            .isInstanceOf(ParkingDomainException.class).hasMessageContaining("celular");
      assertThatThrownBy(() -> c.changeContact(null, "juan@", null, 3L, NOW))
            .hasMessageContaining("correo");
   }

   @Test
   @DisplayName("nombre completo sin 'null' cuando falta una parte")
   void fullName() {
      assertThat(new Customer(1L, "1", "Ana", null).getFullName()).isEqualTo("Ana");
      assertThat(new Customer(1L, "1", null, null).getFullName()).isNull();
   }
}
