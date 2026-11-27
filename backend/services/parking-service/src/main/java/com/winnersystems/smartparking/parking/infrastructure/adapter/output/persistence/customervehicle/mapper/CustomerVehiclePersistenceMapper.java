package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.customervehicle.mapper;

import com.winnersystems.smartparking.parking.domain.model.CustomerVehicle;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.customervehicle.entity.CustomerVehicleEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper bidireccional entre CustomerVehicle (dominio) y CustomerVehicleEntity (persistencia).
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
public class CustomerVehiclePersistenceMapper {

   /**
    * Convierte CustomerVehicle (dominio) → CustomerVehicleEntity (JPA).
    */
   public CustomerVehicleEntity toEntity(CustomerVehicle customerVehicle) {
      if (customerVehicle == null) {
         return null;
      }

      CustomerVehicleEntity entity = new CustomerVehicleEntity();

      // IDs
      entity.setId(customerVehicle.getId());
      entity.setCustomerId(customerVehicle.getCustomerId());
      entity.setVehicleId(customerVehicle.getVehicleId());

      // Veces que este conductor usó este vehículo
      entity.setUsageCount(customerVehicle.getUsageCount());

      // Auditoría (solo creación)
      entity.setCreatedAt(customerVehicle.getCreatedAt());
      entity.setCreatedBy(customerVehicle.getCreatedBy());

      return entity;
   }

   /**
    * Convierte CustomerVehicleEntity (JPA) → CustomerVehicle (dominio).
    */
   public CustomerVehicle toDomain(CustomerVehicleEntity entity) {
      if (entity == null) {
         return null;
      }

      CustomerVehicle customerVehicle = new CustomerVehicle();

      // IDs
      customerVehicle.setId(entity.getId());
      customerVehicle.setCustomerId(entity.getCustomerId());
      customerVehicle.setVehicleId(entity.getVehicleId());

      customerVehicle.setUsageCount(entity.getUsageCount());

      // Auditoría (solo creación)
      customerVehicle.setCreatedAt(entity.getCreatedAt());
      customerVehicle.setCreatedBy(entity.getCreatedBy());

      return customerVehicle;
   }
}