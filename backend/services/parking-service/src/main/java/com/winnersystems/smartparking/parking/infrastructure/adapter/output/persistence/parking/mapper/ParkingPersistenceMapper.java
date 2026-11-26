package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.mapper;

import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.entity.ParkingEntity;
import org.springframework.stereotype.Component;

/**
 * Convierte entre Parking (dominio) y ParkingEntity (JPA).
 * La capacidad no se guarda: la calcula ParkingCapacityPersistenceAdapter.
 */
@Component
public class ParkingPersistenceMapper {

   public Parking toDomain(ParkingEntity entity) {
      if (entity == null) return null;

      Parking parking = new Parking();
      parking.setId(entity.getId());
      parking.setName(entity.getName());
      parking.setCode(entity.getCode());
      parking.setDescription(entity.getDescription());
      parking.setAddress(entity.getAddress());
      parking.setLatitude(entity.getLatitude());
      parking.setLongitude(entity.getLongitude());
      parking.setManagerId(entity.getManagerId());
      parking.setManagerName(entity.getManagerName());
      parking.setStatus(entity.getStatus());
      parking.setRequireDriverDocument(entity.isRequireDriverDocument());
      parking.setCreatedAt(entity.getCreatedAt());
      parking.setCreatedBy(entity.getCreatedBy());
      parking.setUpdatedAt(entity.getUpdatedAt());
      parking.setUpdatedBy(entity.getUpdatedBy());
      parking.setDeletedAt(entity.getDeletedAt());
      parking.setDeletedBy(entity.getDeletedBy());
      return parking;
   }

   public ParkingEntity toEntity(Parking parking) {
      if (parking == null) return null;

      return ParkingEntity.builder()
            .id(parking.getId())
            .name(parking.getName())
            .code(parking.getCode())
            .description(parking.getDescription())
            .address(parking.getAddress())
            .latitude(parking.getLatitude())
            .longitude(parking.getLongitude())
            .managerId(parking.getManagerId())
            .managerName(parking.getManagerName())
            .status(parking.getStatus())
            .requireDriverDocument(parking.isDriverDocumentRequired())
            .createdAt(parking.getCreatedAt())
            .createdBy(parking.getCreatedBy())
            .updatedAt(parking.getUpdatedAt())
            .updatedBy(parking.getUpdatedBy())
            .deletedAt(parking.getDeletedAt())
            .deletedBy(parking.getDeletedBy())
            .build();
   }

   /** El código y la creación no cambian después de crear */
   public void updateEntity(ParkingEntity entity, Parking parking) {
      if (entity == null || parking == null) return;

      entity.setName(parking.getName());
      entity.setDescription(parking.getDescription());
      entity.setAddress(parking.getAddress());
      entity.setLatitude(parking.getLatitude());
      entity.setLongitude(parking.getLongitude());
      entity.setManagerId(parking.getManagerId());
      entity.setManagerName(parking.getManagerName());
      entity.setStatus(parking.getStatus());
      entity.setRequireDriverDocument(parking.isDriverDocumentRequired());
      entity.setUpdatedAt(parking.getUpdatedAt());
      entity.setUpdatedBy(parking.getUpdatedBy());
      entity.setDeletedAt(parking.getDeletedAt());
      entity.setDeletedBy(parking.getDeletedBy());
   }
}
