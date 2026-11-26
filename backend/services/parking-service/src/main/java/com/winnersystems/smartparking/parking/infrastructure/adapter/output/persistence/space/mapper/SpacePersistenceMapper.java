package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.mapper;

import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.entity.SpaceEntity;
import org.springframework.stereotype.Component;

/**
 * Convierte entre Space (dominio) y SpaceEntity (JPA).
 */
@Component
public class SpacePersistenceMapper {

   public Space toDomain(SpaceEntity e) {
      if (e == null) return null;

      Space s = new Space();
      s.setId(e.getId());
      s.setZoneId(e.getZoneId());
      s.setType(e.getType());
      s.setCode(e.getCode());
      s.setDescription(e.getDescription());
      s.setWidth(e.getWidth());
      s.setLength(e.getLength());
      s.setSensorId(e.getSensorId());
      s.setHasCameraCoverage(Boolean.TRUE.equals(e.getHasCameraCoverage()));
      s.setStatus(e.getStatus());
      s.setSensorOccupied(e.getSensorOccupied());
      s.setSensorReadAt(e.getSensorReadAt());
      s.setCreatedAt(e.getCreatedAt());
      s.setCreatedBy(e.getCreatedBy());
      s.setUpdatedAt(e.getUpdatedAt());
      s.setUpdatedBy(e.getUpdatedBy());
      s.setDeletedAt(e.getDeletedAt());
      s.setDeletedBy(e.getDeletedBy());
      return s;
   }

   public SpaceEntity toEntity(Space s) {
      if (s == null) return null;

      return SpaceEntity.builder()
            .id(s.getId())
            .zoneId(s.getZoneId())
            .type(s.getType())
            .code(s.getCode())
            .description(s.getDescription())
            .width(s.getWidth())
            .length(s.getLength())
            .sensorId(s.getSensorId())
            .hasCameraCoverage(Boolean.TRUE.equals(s.getHasCameraCoverage()))
            .status(s.getStatus())
            .sensorOccupied(s.getSensorOccupied())
            .sensorReadAt(s.getSensorReadAt())
            .createdAt(s.getCreatedAt())
            .createdBy(s.getCreatedBy())
            .updatedAt(s.getUpdatedAt())
            .updatedBy(s.getUpdatedBy())
            .deletedAt(s.getDeletedAt())
            .deletedBy(s.getDeletedBy())
            .build();
   }

   /** La zona, el código y la creación no cambian después de crear */
   public void updateEntity(SpaceEntity e, Space s) {
      if (e == null || s == null) return;

      e.setType(s.getType());
      e.setDescription(s.getDescription());
      e.setWidth(s.getWidth());
      e.setLength(s.getLength());
      e.setSensorId(s.getSensorId());
      e.setHasCameraCoverage(Boolean.TRUE.equals(s.getHasCameraCoverage()));
      e.setStatus(s.getStatus());
      e.setSensorOccupied(s.getSensorOccupied());
      e.setSensorReadAt(s.getSensorReadAt());
      e.setUpdatedAt(s.getUpdatedAt());
      e.setUpdatedBy(s.getUpdatedBy());
      e.setDeletedAt(s.getDeletedAt());
      e.setDeletedBy(s.getDeletedBy());
   }
}
