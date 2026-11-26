package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZoneTest {

   private static Zone sample() {
      return Zone.create(1L, "Zona Norte", "norte", null, null, null, null, null, 4L);
   }

   @Test
   @DisplayName("crear exige parqueo, normaliza el código y nace activa")
   void create() {
      Zone z = sample();
      assertThat(z.getCode()).isEqualTo("NORTE");
      assertThat(z.getParkingId()).isEqualTo(1L);
      assertThat(z.isOperational()).isTrue();
      assertThat(z.getAddress()).isNull();
      assertThat(z.getCreatedBy()).isEqualTo(4L);
      assertThat(z.belongsTo(1L)).isTrue();
      assertThat(z.belongsTo(2L)).isFalse();
      assertThatThrownBy(() -> Zone.create(null, "Zona", "N", null, null, null, null, null, 1L))
            .hasMessageContaining("parqueo");
      assertThatThrownBy(() -> Zone.create(1L, "Zona", "N O", null, null, null, null, null, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> Zone.create(1L, " ", "N", null, null, null, null, null, 1L))
            .hasMessageContaining("nombre");
   }

   @Test
   @DisplayName("cámaras: sin vacíos ni repetidos, formato válido y máximo 10")
   void cameras() {
      Zone z = sample();
      z.update("Zona Norte", null, null, null, null, Arrays.asList(" CAM-01 ", "", null, "CAM-01", "CAM-02"));
      assertThat(z.getCameraIdsList()).containsExactly("CAM-01", "CAM-02");
      assertThat(z.hasCameraMonitoring()).isTrue();

      z.update("Zona Norte", null, null, null, null, List.of());
      assertThat(z.hasCameraMonitoring()).isFalse();
      assertThat(z.getCameraIds()).isNull();

      assertThatThrownBy(() -> z.update("Zona Norte", null, null, null, null, List.of("CAM 01")))
            .hasMessageContaining("cámara");
      List<String> many = IntStream.rangeClosed(1, 11).mapToObj(i -> "CAM-" + i).toList();
      assertThatThrownBy(() -> z.update("Zona Norte", null, null, null, null, many))
            .hasMessageContaining("10");
   }

   @Test
   @DisplayName("ubicación: latitud y longitud juntas")
   void location() {
      Zone z = sample();
      assertThatThrownBy(() -> z.update("Zona", null, null, -9.5, null, null)).hasMessageContaining("juntas");
      z.update("Zona", null, null, -9.5, -77.5, null);
      assertThat(z.hasLocation()).isTrue();
   }

   @Test
   @DisplayName("estado, eliminar y restaurar")
   void statusAndTrash() {
      Zone z = sample();
      z.changeStatus("maintenance");
      assertThat(z.isOperational()).isFalse();
      assertThatThrownBy(() -> z.changeStatus("X")).isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> z.restore(1L)).isInstanceOf(ParkingDomainException.class);
      z.markAsDeleted(2L);
      assertThat(z.isDeleted()).isTrue();
      z.restore(3L);
      assertThat(z.isOperational()).isTrue();
      assertThat(z.getUpdatedBy()).isEqualTo(3L);
   }
}
