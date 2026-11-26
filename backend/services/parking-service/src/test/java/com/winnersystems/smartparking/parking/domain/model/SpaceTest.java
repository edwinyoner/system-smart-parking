package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceTest {

   private static Space sample() {
      return Space.create(1L, "a-001", "perpendicular", null, 2.5, 5.0, null, false, 4L);
   }

   @Test
   @DisplayName("crear normaliza código y tipo, nace libre; el sensor define 'tiene sensor'")
   void create() {
      Space s = sample();
      assertThat(s.getCode()).isEqualTo("A-001");
      assertThat(s.getType()).isEqualTo(Space.TYPE_PERPENDICULAR);
      assertThat(s.isAvailableForOccupation()).isTrue();
      assertThat(s.getArea()).isEqualTo(12.5);
      assertThat(s.hasSensor()).isFalse();
      s.update("PARALLEL", null, null, null, " HCSR04-01 ", true);
      assertThat(s.getSensorId()).isEqualTo("HCSR04-01");
      assertThat(s.hasSensor()).isTrue();
      assertThat(s.hasCameraMonitoring()).isTrue();
      assertThat(s.getArea()).isNull();
   }

   @Test
   @DisplayName("datos inválidos: zona, código, tipo, medidas y sensor")
   void invalid() {
      assertThatThrownBy(() -> Space.create(null, "A1", "PARALLEL", null, null, null, null, false, 1L))
            .hasMessageContaining("zona");
      assertThatThrownBy(() -> Space.create(1L, "A 1", "PARALLEL", null, null, null, null, false, 1L))
            .hasMessageContaining("Código");
      assertThatThrownBy(() -> Space.create(1L, "A1", "CIRCULAR", null, null, null, null, false, 1L))
            .hasMessageContaining("tipo");
      assertThatThrownBy(() -> Space.create(1L, "A1", "PARALLEL", null, 0.2, null, null, false, 1L))
            .hasMessageContaining("ancho");
      assertThatThrownBy(() -> Space.create(1L, "A1", "PARALLEL", null, null, 25.0, null, false, 1L))
            .hasMessageContaining("largo");
      assertThatThrownBy(() -> Space.create(1L, "A1", "PARALLEL", null, null, null, "sensor 1", false, 1L))
            .hasMessageContaining("sensor");
   }

   @Test
   @DisplayName("ocupar y liberar; no se ocupa dos veces")
   void occupancy() {
      Space s = sample();
      s.occupy();
      assertThat(s.isOccupied()).isTrue();
      assertThatThrownBy(s::occupy).isInstanceOf(ParkingDomainException.class);
      s.release();
      assertThat(s.isAvailable()).isTrue();
      s.release();
      assertThat(s.isAvailable()).isTrue();
      s.setInMaintenance();
      assertThatThrownBy(s::occupy).hasMessageContaining("no está libre");
   }

   @Test
   @DisplayName("a mano: OCCUPIED no se pone, y un espacio ocupado no cambia ni se elimina")
   void manualStatus() {
      Space s = sample();
      assertThatThrownBy(() -> s.changeStatus("OCCUPIED")).hasMessageContaining("entrada");
      s.changeStatus("maintenance");
      assertThat(s.isInMaintenance()).isTrue();
      s.enable();
      s.occupy();
      assertThatThrownBy(s::setOutOfService).hasMessageContaining("vehículo dentro");
      assertThatThrownBy(() -> s.markAsDeleted(1L)).hasMessageContaining("vehículo dentro");
   }

   @Test
   @DisplayName("eliminar y restaurar: vuelve libre")
   void trash() {
      Space s = sample();
      assertThatThrownBy(() -> s.restore(1L)).isInstanceOf(ParkingDomainException.class);
      s.markAsDeleted(2L);
      assertThat(s.isAvailableForOccupation()).isFalse();
      s.restore(3L);
      assertThat(s.isAvailableForOccupation()).isTrue();
      assertThat(s.getUpdatedBy()).isEqualTo(3L);
   }
}
