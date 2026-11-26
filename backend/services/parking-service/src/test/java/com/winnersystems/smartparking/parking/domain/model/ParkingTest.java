package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParkingTest {

   private static Parking sample() {
      return Parking.create("Parqueo San Martín", " psm ", null, "Jr. San Martín 520", -9.52, -77.53, 1L);
   }

   @Test
   @DisplayName("crear normaliza el código a mayúsculas, recorta textos y nace activo")
   void create() {
      Parking p = Parking.create("  Parqueo San Martín ", "psm-01", "  ", " Jr. San Martín ", null, null, 7L);
      assertThat(p.getCode()).isEqualTo("PSM-01");
      assertThat(p.getName()).isEqualTo("Parqueo San Martín");
      assertThat(p.getAddress()).isEqualTo("Jr. San Martín");
      assertThat(p.getDescription()).isNull();
      assertThat(p.isOperational()).isTrue();
      assertThat(p.getCreatedBy()).isEqualTo(7L);
      assertThat(p.hasLocation()).isFalse();
   }

   @Test
   @DisplayName("código inválido, nombre o dirección vacíos se rechazan")
   void invalidData() {
      assertThatThrownBy(() -> Parking.create("A", "P", null, "Dir", null, null, 1L))
            .isInstanceOf(ParkingDomainException.class).hasMessageContaining("código");
      assertThatThrownBy(() -> Parking.create("A", "P S M", null, "Dir", null, null, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> Parking.create(" ", "PSM", null, "Dir", null, null, 1L))
            .hasMessage("El nombre es obligatorio");
      assertThatThrownBy(() -> Parking.create("A", "PSM", null, null, null, null, 1L))
            .hasMessage("La dirección es obligatoria");
      assertThatThrownBy(() -> Parking.create("A", "PSM", "x".repeat(501), "Dir", null, null, 1L))
            .hasMessageContaining("500");
   }

   @Test
   @DisplayName("latitud y longitud van juntas y dentro de rango")
   void location() {
      assertThatThrownBy(() -> Parking.create("A", "PSM", null, "Dir", -9.5, null, 1L))
            .hasMessageContaining("juntas");
      assertThatThrownBy(() -> Parking.create("A", "PSM", null, "Dir", -91.0, 10.0, 1L))
            .hasMessageContaining("latitud");
      assertThatThrownBy(() -> Parking.create("A", "PSM", null, "Dir", 10.0, 181.0, 1L))
            .hasMessageContaining("longitud");
      Parking p = sample();
      p.update("Parqueo San Martín", null, "Dir", null, null);
      assertThat(p.hasLocation()).isFalse();
   }

   @Test
   @DisplayName("solo un parqueo activo y no eliminado recibe vehículos")
   void operational() {
      Parking p = sample();
      p.setInMaintenance();
      assertThat(p.isOperational()).isFalse();
      p.changeStatus("active");
      assertThat(p.isOperational()).isTrue();
      p.markAsDeleted(2L);
      assertThat(p.isOperational()).isFalse();
      assertThat(p.getStatus()).isEqualTo(Parking.STATUS_INACTIVE);
      assertThatThrownBy(() -> p.changeStatus("CERRADO")).isInstanceOf(ParkingDomainException.class);
   }

   @Test
   @DisplayName("restaurar vuelve activo; restaurar uno no eliminado es un error")
   void restore() {
      Parking p = sample();
      assertThatThrownBy(() -> p.restore(1L)).isInstanceOf(ParkingDomainException.class);
      p.markAsDeleted(2L);
      assertThat(p.getDeletedBy()).isEqualTo(2L);
      p.restore(3L);
      assertThat(p.isDeleted()).isFalse();
      assertThat(p.getDeletedBy()).isNull();
      assertThat(p.isActive()).isTrue();
      assertThat(p.getUpdatedBy()).isEqualTo(3L);
   }

   @Test
   @DisplayName("responsable: asignar con nombre y quitar con null")
   void manager() {
      Parking p = sample();
      p.assignManager(5L, " Ana Ríos ");
      assertThat(p.getManagerName()).isEqualTo("Ana Ríos");
      p.assignManager(null, "ignorado");
      assertThat(p.hasManager()).isFalse();
      assertThat(p.getManagerName()).isNull();
   }

   @Test
   @DisplayName("igualdad por ID")
   void equality() {
      Parking a = sample();
      Parking b = sample();
      assertThat(a).isNotEqualTo(b);
      a.setId(1L);
      b.setId(1L);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
   }

   @Test
   @DisplayName("documento del conductor: apagado por defecto; encendido, la entrada lo exige")
   void driverDocumentPolicy() {
      Parking p = sample();
      assertThat(p.isDriverDocumentRequired()).isFalse();
      p.ensureEntryDocument(false);

      p.changeDriverDocumentPolicy(true);
      assertThat(p.isDriverDocumentRequired()).isTrue();
      p.ensureEntryDocument(true);
      assertThatThrownBy(() -> p.ensureEntryDocument(false)).hasMessageContaining("exige el documento");
   }
}
