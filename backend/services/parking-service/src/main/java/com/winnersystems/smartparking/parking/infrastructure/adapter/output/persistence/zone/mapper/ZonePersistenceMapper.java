package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.mapper;

import com.winnersystems.smartparking.parking.domain.model.Zone;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.entity.ZoneEntity;
import org.springframework.stereotype.Component;

/**
 * Convierte entre Zone (dominio) y ZoneEntity (JPA).
 */
@Component
public class ZonePersistenceMapper {

   public Zone toDomain(ZoneEntity e) {
      if (e == null) return null;

      Zone zone = new Zone();
      zone.setId(e.getId());
      zone.setParkingId(e.getParkingId());
      zone.setName(e.getName());
      zone.setCode(e.getCode());
      zone.setAddress(e.getAddress());
      zone.setDescription(e.getDescription());
      zone.setLatitude(e.getLatitude());
      zone.setLongitude(e.getLongitude());
      zone.setCameraIds(e.getCameraIds());
      zone.setStatus(e.getStatus());
      zone.setCreatedAt(e.getCreatedAt());
      zone.setCreatedBy(e.getCreatedBy());
      zone.setUpdatedAt(e.getUpdatedAt());
      zone.setUpdatedBy(e.getUpdatedBy());
      zone.setDeletedAt(e.getDeletedAt());
      zone.setDeletedBy(e.getDeletedBy());
      return zone;
   }

   public ZoneEntity toEntity(Zone z) {
      if (z == null) return null;

      return ZoneEntity.builder()
            .id(z.getId())
            .parkingId(z.getParkingId())
            .name(z.getName())
            .code(z.getCode())
            .address(z.getAddress())
            .description(z.getDescription())
            .latitude(z.getLatitude())
            .longitude(z.getLongitude())
            .cameraIds(z.getCameraIds())
            .status(z.getStatus())
            .createdAt(z.getCreatedAt())
            .createdBy(z.getCreatedBy())
            .updatedAt(z.getUpdatedAt())
            .updatedBy(z.getUpdatedBy())
            .deletedAt(z.getDeletedAt())
            .deletedBy(z.getDeletedBy())
            .build();
   }

   /** El parqueo, el código y la creación no cambian después de crear */
   public void updateEntity(ZoneEntity e, Zone z) {
      if (e == null || z == null) return;

      e.setName(z.getName());
      e.setAddress(z.getAddress());
      e.setDescription(z.getDescription());
      e.setLatitude(z.getLatitude());
      e.setLongitude(z.getLongitude());
      e.setCameraIds(z.getCameraIds());
      e.setStatus(z.getStatus());
      e.setUpdatedAt(z.getUpdatedAt());
      e.setUpdatedBy(z.getUpdatedBy());
      e.setDeletedAt(z.getDeletedAt());
      e.setDeletedBy(z.getDeletedBy());
   }
}
