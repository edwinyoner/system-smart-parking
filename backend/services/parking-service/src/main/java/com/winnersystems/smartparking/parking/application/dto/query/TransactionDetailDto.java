package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO completo con todos los detalles de una transacción.
 * Usado para consultas individuales donde se necesita toda la información.
 *
 * Las clases internas también son records (inmutables, sin setters).
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public record TransactionDetailDto(

      // ========================= BÁSICO =========================
      Long id,
      String status,
      String paymentStatus,

      // ========================= ENTIDADES ANIDADAS =========================
      VehicleInfo vehicle,
      CustomerInfo customer,
      ParkingInfo parking,
      ZoneInfo zone,
      SpaceInfo space,

      // ========================= TIEMPOS =========================
      LocalDateTime entryTime,
      LocalDateTime exitTime,
      Integer durationMinutes,
      String durationFormatted,         // "3h 30min"

      // ========================= DOCUMENTOS =========================
      DocumentInfo entryDocument,
      DocumentInfo exitDocument,

      // ========================= MONTOS =========================
      RateInfo rate,
      BigDecimal calculatedAmount,
      BigDecimal discountAmount,
      BigDecimal totalAmount,
      String currency,

      // ========================= PAGO =========================
      PaymentInfo payment,

      // ========================= OPERADORES =========================
      OperatorInfo entryOperator,
      OperatorInfo exitOperator,

      // ========================= EVIDENCIA =========================
      String entryPhotoUrl,
      String exitPhotoUrl,
      Double entryPlateConfidence,
      Double exitPlateConfidence,

      // ========================= COMPROBANTE =========================
      Boolean receiptSent,
      LocalDateTime receiptSentAt,
      String receiptWhatsAppStatus,
      String receiptEmailStatus,

      // ========================= OBSERVACIONES =========================
      String notes,
      String cancellationReason,

      // ========================= AUDITORÍA =========================
      LocalDateTime createdAt,
      LocalDateTime updatedAt,

      // ========================= MÉTODO DE REGISTRO =========================
      String entryMethod,                 // MANUAL (operador) o CAMERA_AI (cámara)
      String exitMethod
) {

   // ========================= RECORDS INTERNOS =========================

   /**
    * Información del vehículo involucrado en la transacción.
    */
   public record VehicleInfo(
         Long id,
         String plateNumber
   ) {}

   /**
    * Información del cliente/conductor.
    */
   public record CustomerInfo(
         Long id,
         String documentType,
         String documentNumber,
         String name,
         String phone,
         String email
   ) {}

   /**
    * Información del Lugar de estacionamiento.
    */
   public record ParkingInfo(
         Long id,
         String name,
         String code
   ) {}

   /**
    * Información de la zona de estacionamiento.
    */
   public record ZoneInfo(
         Long id,
         String name,
         String code
   ) {}

   /**
    * Información del espacio de estacionamiento.
    */
   public record SpaceInfo(
         Long id,
         String code,
         String type
   ) {}

   /**
    * Documento de identificación (entrada o salida).
    */
   public record DocumentInfo(
         String type,
         String number
   ) {}

   /**
    * Información de la tarifa aplicada.
    */
   public record RateInfo(
         Long id,
         String name,
         BigDecimal hourlyRate
   ) {}

   /**
    * Estado del pago de la estadía. El detalle de cada pago (tipo, operación, comprobante)
    * está en payment-service: GET /payments/by-source/PARKING_STAY/{id}.
    *
    * @param paidAmount   pagado, neto de devoluciones
    * @param paidAt       último pago
    * @param amountDue    lo que falta pagar para salir ahora (0 si ya salió)
    * @param exitDeadline hasta cuándo puede salir sin pagar más (null si no aplica)
    */
   public record PaymentInfo(
         BigDecimal paidAmount,
         LocalDateTime paidAt,
         BigDecimal amountDue,
         LocalDateTime exitDeadline
   ) {}

   /**
    * Información del operador (entrada o salida).
    */
   public record OperatorInfo(
         Long id,
         String name,
         String email
   ) {}
}