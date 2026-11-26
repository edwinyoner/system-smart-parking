package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking;

import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.repository.ParkingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParkingCapacityPersistenceAdapterTest {

   @Test
   @DisplayName("disponible = libre en zona activa; ocupado cuenta en cualquier zona")
   void countsFromSpaces() {
      ParkingRepository repo = mock(ParkingRepository.class);
      when(repo.countZonesByParking(any())).thenReturn(List.<Object[]>of(
            new Object[]{1L, 2L}, new Object[]{2L, 1L}));
      when(repo.countSpacesByParking(any())).thenReturn(List.<Object[]>of(
            new Object[]{1L, "AVAILABLE", "ACTIVE", 10L},
            new Object[]{1L, "AVAILABLE", "MAINTENANCE", 4L},   // libres pero la zona no opera
            new Object[]{1L, "OCCUPIED", "ACTIVE", 5L},
            new Object[]{1L, "OCCUPIED", "INACTIVE", 1L},
            new Object[]{1L, "MAINTENANCE", "ACTIVE", 2L}));

      Map<Long, ParkingCapacity> result = new ParkingCapacityPersistenceAdapter(repo).capacityOf(List.of(1L, 2L, 3L));

      assertThat(result.get(1L)).isEqualTo(new ParkingCapacity(2, 22, 10, 6));
      assertThat(result.get(1L).unavailableSpaces()).isEqualTo(6);
      assertThat(result.get(2L)).isEqualTo(new ParkingCapacity(1, 0, 0, 0));
      assertThat(result).doesNotContainKey(3L);
   }

   @Test
   @DisplayName("sin IDs no consulta la base")
   void emptyIds() {
      ParkingRepository repo = mock(ParkingRepository.class);
      assertThat(new ParkingCapacityPersistenceAdapter(repo).capacityOf(List.of())).isEmpty();
      verifyNoInteractions(repo);
   }
}
