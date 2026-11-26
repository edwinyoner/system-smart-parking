package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.mapper;

import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.entity.OperatorAssignmentEntity;
import org.springframework.stereotype.Component;

/**
 * Convierte entre OperatorAssignment (dominio) y OperatorAssignmentEntity (JPA).
 */
@Component
public class OperatorAssignmentPersistenceMapper {

   public OperatorAssignment toDomain(OperatorAssignmentEntity e) {
      if (e == null) return null;
      OperatorAssignment a = new OperatorAssignment();
      a.setId(e.getId());
      a.setOperatorId(e.getOperatorId());
      a.setOperatorName(e.getOperatorName());
      a.setParkingId(e.getParkingId());
      a.setZoneId(e.getZoneId());
      a.setShiftId(e.getShiftId());
      a.setStartDate(e.getStartDate());
      a.setEndDate(e.getEndDate());
      a.setCreatedAt(e.getCreatedAt());
      a.setCreatedBy(e.getCreatedBy());
      a.setUpdatedAt(e.getUpdatedAt());
      a.setUpdatedBy(e.getUpdatedBy());
      return a;
   }

   /** Copia el estado del dominio en la entidad (nueva o cargada) */
   public OperatorAssignmentEntity copyInto(OperatorAssignmentEntity e, OperatorAssignment a) {
      if (e.getId() == null) {
         e.setOperatorId(a.getOperatorId());
         e.setParkingId(a.getParkingId());
         e.setZoneId(a.getZoneId());
         e.setShiftId(a.getShiftId());
         e.setCreatedAt(a.getCreatedAt());
         e.setCreatedBy(a.getCreatedBy());
      }
      e.setOperatorName(a.getOperatorName());
      e.setStartDate(a.getStartDate());
      e.setEndDate(a.getEndDate());
      e.setUpdatedAt(a.getUpdatedAt());
      e.setUpdatedBy(a.getUpdatedBy());
      return e;
   }
}
