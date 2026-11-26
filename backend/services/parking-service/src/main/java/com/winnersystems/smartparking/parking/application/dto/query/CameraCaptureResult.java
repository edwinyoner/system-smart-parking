package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;

/**
 * Respuesta a iot-service para una lectura de cámara. Le dice al dispositivo qué hacer:
 * abrir la barrera, mostrar el monto a pagar o esperar al operador.
 *
 * Resultados:
 * - ENTRY_REGISTERED: entrada registrada, se abre la barrera.
 * - EXIT_REGISTERED:  salida registrada, se abre la barrera.
 * - PAYMENT_REQUIRED: falta pagar; la pantalla de salida cobra amountDue (QR) y luego se reintenta.
 * - PENDING_REVIEW:   la lectura espera a un operador (placa dudosa o el parqueo exige documento).
 * - REJECTED:         no se puede registrar (ya está dentro, zona llena, parqueo cerrado,
 *                     sin estadía para salir...); message explica el motivo.
 *
 * @param outcome       uno de los resultados de arriba
 * @param openGate      true solo si se registró la entrada o la salida
 * @param message       texto para la pantalla o el registro del dispositivo
 * @param plateNumber   placa normalizada, si se pudo leer
 * @param transactionId estadía creada, cerrada o pendiente de pago
 * @param reviewId      revisión abierta (PENDING_REVIEW)
 * @param spaceCode     espacio descontado en la entrada (solo informativo: es de libre elección)
 * @param amountDue     monto que falta pagar (PAYMENT_REQUIRED)
 * @param currency      moneda del monto
 */
public record CameraCaptureResult(
      String outcome,
      boolean openGate,
      String message,
      String plateNumber,
      Long transactionId,
      Long reviewId,
      String spaceCode,
      BigDecimal amountDue,
      String currency
) {
   public static final String ENTRY_REGISTERED = "ENTRY_REGISTERED";
   public static final String EXIT_REGISTERED = "EXIT_REGISTERED";
   public static final String PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
   public static final String PENDING_REVIEW = "PENDING_REVIEW";
   public static final String REJECTED = "REJECTED";

   public static CameraCaptureResult entered(String plate, Long transactionId, String spaceCode) {
      return new CameraCaptureResult(ENTRY_REGISTERED, true, "Bienvenido", plate, transactionId, null,
            spaceCode, null, null);
   }

   public static CameraCaptureResult exited(String plate, Long transactionId) {
      return new CameraCaptureResult(EXIT_REGISTERED, true, "Buen viaje", plate, transactionId, null,
            null, null, null);
   }

   public static CameraCaptureResult paymentRequired(String plate, Long transactionId, BigDecimal due) {
      return new CameraCaptureResult(PAYMENT_REQUIRED, false, "Falta pagar antes de salir", plate,
            transactionId, null, null, due, "PEN");
   }

   public static CameraCaptureResult pendingReview(String plate, Long reviewId, String message) {
      return new CameraCaptureResult(PENDING_REVIEW, false, message, plate, null, reviewId, null, null, null);
   }

   public static CameraCaptureResult rejected(String plate, String message) {
      return new CameraCaptureResult(REJECTED, false, message, plate, null, null, null, null, null);
   }
}
