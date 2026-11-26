package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.mapper;

import com.winnersystems.smartparking.parking.domain.model.PlateReview;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.entity.PlateReviewEntity;
import org.springframework.stereotype.Component;

@Component
public class PlateReviewPersistenceMapper {

   public PlateReviewEntity toEntity(PlateReview r) {
      PlateReviewEntity e = new PlateReviewEntity();
      e.setId(r.getId());
      e.setCaptureId(r.getCaptureId());
      e.setDirection(r.getDirection());
      e.setParkingId(r.getParkingId());
      e.setZoneId(r.getZoneId());
      e.setDeviceId(r.getDeviceId());
      e.setDetectedPlate(r.getDetectedPlate());
      e.setConfidence(r.getConfidence());
      e.setPhotoUrl(r.getPhotoUrl());
      e.setStatus(r.getStatus());
      e.setConfirmedPlate(r.getConfirmedPlate());
      e.setTransactionId(r.getTransactionId());
      e.setDiscardReason(r.getDiscardReason());
      e.setResolvedBy(r.getResolvedBy());
      e.setResolvedAt(r.getResolvedAt());
      e.setCreatedAt(r.getCreatedAt());
      e.setUpdatedAt(r.getUpdatedAt());
      return e;
   }

   public PlateReview toDomain(PlateReviewEntity e) {
      PlateReview r = new PlateReview();
      r.setId(e.getId());
      r.setCaptureId(e.getCaptureId());
      r.setDirection(e.getDirection());
      r.setParkingId(e.getParkingId());
      r.setZoneId(e.getZoneId());
      r.setDeviceId(e.getDeviceId());
      r.setDetectedPlate(e.getDetectedPlate());
      r.setConfidence(e.getConfidence());
      r.setPhotoUrl(e.getPhotoUrl());
      r.setStatus(e.getStatus());
      r.setConfirmedPlate(e.getConfirmedPlate());
      r.setTransactionId(e.getTransactionId());
      r.setDiscardReason(e.getDiscardReason());
      r.setResolvedBy(e.getResolvedBy());
      r.setResolvedAt(e.getResolvedAt());
      r.setCreatedAt(e.getCreatedAt());
      r.setUpdatedAt(e.getUpdatedAt());
      return r;
   }
}
