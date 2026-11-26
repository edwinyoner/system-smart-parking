package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.RecordEntryCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RecordExitCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ActiveTransactionDto;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request.RecordEntryRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request.RecordExitRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.ActiveTransactionResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.TransactionDetailResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.TransactionResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper para conversión entre Request/Response y Commands/DTOs.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
public class TransactionRestMapper {

   // ========================= REQUEST → COMMAND =========================

   public RecordEntryCommand toCommand(RecordEntryRequest request) {
      if (request == null) return null;

      return new RecordEntryCommand(
            request.plateNumber(), request.parkingId(), request.zoneId(), request.spaceId(),
            request.documentTypeId(), request.documentNumber(),
            request.customerFirstName(), request.customerLastName(),
            request.customerPhone(), request.customerEmail(), request.notes());
   }

   public RecordExitCommand toCommand(RecordExitRequest request) {
      if (request == null) return null;

      return new RecordExitCommand(
            request.transactionId(), request.plateNumber(),
            request.exitDocumentTypeId(), request.exitDocumentNumber(), request.notes());
   }

   // ========================= DTO → RESPONSE =========================

   public TransactionResponse toResponse(TransactionDto dto) {
      if (dto == null) return null;

      return TransactionResponse.builder()
            .id(dto.id())
            .plateNumber(dto.plateNumber())
            .customerName(dto.customerName())
            .parkingName(dto.parkingName())
            .zoneName(dto.zoneName())
            .spaceCode(dto.spaceCode())
            .entryTime(dto.entryTime())
            .exitTime(dto.exitTime())
            .duration(dto.duration())
            .totalAmount(dto.totalAmount())
            .status(dto.status())
            .paymentStatus(dto.paymentStatus())
            .createdAt(dto.createdAt())
            .build();
   }

   public TransactionDetailResponse toDetailResponse(TransactionDetailDto dto) {
      if (dto == null) return null;

      TransactionDetailResponse response = TransactionDetailResponse.builder()
            .id(dto.id())
            .status(dto.status())
            .paymentStatus(dto.paymentStatus())
            .entryTime(dto.entryTime())
            .exitTime(dto.exitTime())
            .durationMinutes(dto.durationMinutes())
            .durationFormatted(dto.durationFormatted())
            .calculatedAmount(dto.calculatedAmount())
            .discountAmount(dto.discountAmount())
            .totalAmount(dto.totalAmount())
            .currency(dto.currency())
            .entryPhotoUrl(dto.entryPhotoUrl())
            .exitPhotoUrl(dto.exitPhotoUrl())
            .entryPlateConfidence(dto.entryPlateConfidence())
            .exitPlateConfidence(dto.exitPlateConfidence())
            .receiptSent(dto.receiptSent())
            .receiptSentAt(dto.receiptSentAt())
            .receiptWhatsAppStatus(dto.receiptWhatsAppStatus())
            .receiptEmailStatus(dto.receiptEmailStatus())
            .notes(dto.notes())
            .cancellationReason(dto.cancellationReason())
            .createdAt(dto.createdAt())
            .updatedAt(dto.updatedAt())
            .entryMethod(dto.entryMethod())
            .exitMethod(dto.exitMethod())
            .entryOperatorId(dto.entryOperator() != null ? dto.entryOperator().id() : null)
            .exitOperatorId(dto.exitOperator() != null ? dto.exitOperator().id() : null)
            .build();

      // Vehículo
      if (dto.vehicle() != null) {
         response.setVehicle(TransactionDetailResponse.VehicleInfo.builder()
               .id(dto.vehicle().id())
               .plateNumber(dto.vehicle().plateNumber())
               .build());
      }

      // Cliente
      if (dto.customer() != null) {
         response.setCustomer(TransactionDetailResponse.CustomerInfo.builder()
               .id(dto.customer().id())
               .documentType(dto.customer().documentType())
               .documentNumber(dto.customer().documentNumber())
               .name(dto.customer().name())
               .phone(dto.customer().phone())
               .email(dto.customer().email())
               .build());
      }

      if (dto.parking() != null) {
         response.setParking(TransactionDetailResponse.ParkingInfo.builder()
               .id(dto.parking().id())
               .name(dto.parking().name())
               .code(dto.parking().code())
               .requireDriverDocument(dto.parking().requireDriverDocument())
               .build());
      }

      // Zona
      if (dto.zone() != null) {
         response.setZone(TransactionDetailResponse.ZoneInfo.builder()
               .id(dto.zone().id())
               .name(dto.zone().name())
               .code(dto.zone().code())
               .build());
      }

      // Espacio
      if (dto.space() != null) {
         response.setSpace(TransactionDetailResponse.SpaceInfo.builder()
               .id(dto.space().id())
               .code(dto.space().code())
               .build());
      }

      // Documento entrada
      if (dto.entryDocument() != null) {
         response.setEntryDocument(TransactionDetailResponse.DocumentInfo.builder()
               .type(dto.entryDocument().type())
               .number(dto.entryDocument().number())
               .build());
      }

      // Documento salida
      if (dto.exitDocument() != null) {
         response.setExitDocument(TransactionDetailResponse.DocumentInfo.builder()
               .type(dto.exitDocument().type())
               .number(dto.exitDocument().number())
               .build());
      }

      // Tarifa
      if (dto.rate() != null) {
         response.setRate(TransactionDetailResponse.RateInfo.builder()
               .id(dto.rate().id())
               .name(dto.rate().name())
               .hourlyRate(dto.rate().hourlyRate())
               .build());
      }

      // Pago
      if (dto.payment() != null) {
         response.setPayment(TransactionDetailResponse.PaymentInfo.builder()
               .paidAmount(dto.payment().paidAmount())
               .paidAt(dto.payment().paidAt())
               .amountDue(dto.payment().amountDue())
               .exitDeadline(dto.payment().exitDeadline())
               .build());
      }

      return response;
   }

   public ActiveTransactionResponse toActiveResponse(ActiveTransactionDto dto) {
      if (dto == null) return null;

      return ActiveTransactionResponse.builder()
            .id(dto.id())
            .vehicleId(dto.vehicleId())
            .plateNumber(dto.plateNumber())
            .customerId(dto.customerId())
            .customerName(dto.customerName())
            .customerPhone(dto.customerPhone())
            .customerEmail(dto.customerEmail())
            .documentType(dto.documentType())
            .documentNumber(dto.documentNumber())
            .parkingId(dto.parkingId())
            .parkingName(dto.parkingName())
            .zoneId(dto.zoneId())
            .zoneName(dto.zoneName())
            .spaceId(dto.spaceId())
            .spaceCode(dto.spaceCode())
            .entryTime(dto.entryTime())
            .elapsedMinutes(dto.elapsedMinutes())
            .elapsedFormatted(dto.elapsedFormatted())
            .hourlyRate(dto.hourlyRate())
            .currentAmount(dto.currentAmount())
            .currency(dto.currency())
            .maxRecommendedMinutes(dto.maxRecommendedMinutes())
            .isOverdue(dto.isOverdue())
            .requiresAttention(dto.requiresAttention())
            .entryOperatorId(dto.entryOperatorId())
            .entryMethod(dto.entryMethod())
            .entryPhotoUrl(dto.entryPhotoUrl())
            .plateConfidence(dto.plateConfidence())
            .notes(dto.notes())
            .createdAt(dto.createdAt())
            .build();
   }
}