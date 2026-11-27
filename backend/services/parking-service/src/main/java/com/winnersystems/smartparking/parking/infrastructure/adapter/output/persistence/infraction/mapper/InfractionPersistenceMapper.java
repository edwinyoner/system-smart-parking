package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.mapper;

import com.winnersystems.smartparking.parking.domain.model.Infraction;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.entity.InfractionEntity;
import org.springframework.stereotype.Component;

/** Copia campo por campo entre el dominio y la tabla */
@Component
public class InfractionPersistenceMapper {

   public Infraction toDomain(InfractionEntity e) {
      if (e == null) {
         return null;
      }
      Infraction d = new Infraction();
      d.setId(e.getId());
      d.setInfractionCode(e.getInfractionCode());
      d.setParkingId(e.getParkingId());
      d.setZoneId(e.getZoneId());
      d.setSpaceId(e.getSpaceId());
      d.setTransactionId(e.getTransactionId());
      d.setVehicleId(e.getVehicleId());
      d.setPlateNumber(e.getPlateNumber());
      d.setCustomerId(e.getCustomerId());
      d.setInfractionType(e.getInfractionType());
      d.setSeverity(e.getSeverity());
      d.setDetectedAt(e.getDetectedAt());
      d.setDetectedBy(e.getDetectedBy());
      d.setDetectedByName(e.getDetectedByName());
      d.setDetectionMethod(e.getDetectionMethod());
      d.setDescription(e.getDescription());
      d.setEvidence(e.getEvidence());
      d.setFineAmount(e.getFineAmount());
      d.setCurrency(e.getCurrency());
      d.setFineDueDate(e.getFineDueDate());
      d.setStatus(e.getStatus());
      d.setResolutionType(e.getResolutionType());
      d.setResolution(e.getResolution());
      d.setResolvedAt(e.getResolvedAt());
      d.setResolvedBy(e.getResolvedBy());
      d.setFinePaid(e.getFinePaid());
      d.setFinePaidAt(e.getFinePaidAt());
      d.setFinePaidAmount(e.getFinePaidAmount());
      d.setFinePaymentReference(e.getFinePaymentReference());
      d.setNotes(e.getNotes());
      d.setCreatedAt(e.getCreatedAt());
      d.setCreatedBy(e.getCreatedBy());
      d.setUpdatedAt(e.getUpdatedAt());
      d.setUpdatedBy(e.getUpdatedBy());
      return d;
   }

   public InfractionEntity toEntity(Infraction d) {
      if (d == null) {
         return null;
      }
      InfractionEntity e = new InfractionEntity();
      e.setId(d.getId());
      e.setInfractionCode(d.getInfractionCode());
      e.setParkingId(d.getParkingId());
      e.setZoneId(d.getZoneId());
      e.setSpaceId(d.getSpaceId());
      e.setTransactionId(d.getTransactionId());
      e.setVehicleId(d.getVehicleId());
      e.setPlateNumber(d.getPlateNumber());
      e.setCustomerId(d.getCustomerId());
      e.setInfractionType(d.getInfractionType());
      e.setSeverity(d.getSeverity());
      e.setDetectedAt(d.getDetectedAt());
      e.setDetectedBy(d.getDetectedBy());
      e.setDetectedByName(d.getDetectedByName());
      e.setDetectionMethod(d.getDetectionMethod());
      e.setDescription(d.getDescription());
      e.setEvidence(d.getEvidence());
      e.setFineAmount(d.getFineAmount());
      e.setCurrency(d.getCurrency());
      e.setFineDueDate(d.getFineDueDate());
      e.setStatus(d.getStatus());
      e.setResolutionType(d.getResolutionType());
      e.setResolution(d.getResolution());
      e.setResolvedAt(d.getResolvedAt());
      e.setResolvedBy(d.getResolvedBy());
      e.setFinePaid(d.getFinePaid());
      e.setFinePaidAt(d.getFinePaidAt());
      e.setFinePaidAmount(d.getFinePaidAmount());
      e.setFinePaymentReference(d.getFinePaymentReference());
      e.setNotes(d.getNotes());
      e.setCreatedAt(d.getCreatedAt());
      e.setCreatedBy(d.getCreatedBy());
      e.setUpdatedAt(d.getUpdatedAt());
      e.setUpdatedBy(d.getUpdatedBy());
      return e;
   }
}
