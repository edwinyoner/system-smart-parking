package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parkingshiftrate.mapper;

import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parkingshiftrate.entity.ParkingShiftRateEntity;
import org.springframework.stereotype.Component;

/**
 * Convierte entre ParkingShiftRate (dominio) y ParkingShiftRateEntity (JPA).
 */
@Component
public class ParkingShiftRatePersistenceMapper {

   public ParkingShiftRate toDomain(ParkingShiftRateEntity e) {
      if (e == null) return null;

      ParkingShiftRate c = new ParkingShiftRate();
      c.setId(e.getId());
      c.setParkingId(e.getParkingId());
      c.setShiftId(e.getShiftId());
      c.setRateId(e.getRateId());
      c.setStatus(Boolean.TRUE.equals(e.getStatus()));
      c.setCreatedAt(e.getCreatedAt());
      c.setCreatedBy(e.getCreatedBy());
      c.setUpdatedAt(e.getUpdatedAt());
      c.setUpdatedBy(e.getUpdatedBy());
      return c;
   }

   public ParkingShiftRateEntity toEntity(ParkingShiftRate c) {
      if (c == null) return null;

      return ParkingShiftRateEntity.builder()
            .id(c.getId())
            .parkingId(c.getParkingId())
            .shiftId(c.getShiftId())
            .rateId(c.getRateId())
            .status(c.getStatus())
            .createdAt(c.getCreatedAt())
            .createdBy(c.getCreatedBy())
            .updatedAt(c.getUpdatedAt())
            .updatedBy(c.getUpdatedBy())
            .build();
   }

   /** El parqueo y el turno no cambian: solo la tarifa y el estado */
   public void updateEntity(ParkingShiftRateEntity e, ParkingShiftRate c) {
      if (e == null || c == null) return;

      e.setRateId(c.getRateId());
      e.setStatus(c.getStatus());
      e.setUpdatedAt(c.getUpdatedAt());
      e.setUpdatedBy(c.getUpdatedBy());
   }
}
