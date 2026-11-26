package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.DocumentMismatchException;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad de dominio que representa una TRANSACCIÓN de estacionamiento.
 *
 * Transaction es la entidad MÁS IMPORTANTE del sistema. Registra el ciclo completo
 * desde que un vehículo entra hasta que sale y paga.
 *
 * Ciclo de vida (se paga ANTES de salir):
 * 1. ENTRADA: status ACTIVE y paymentStatus PENDING.
 * 2. PAGO: lo cobra payment-service (QR o efectivo) y avisa: paidAmount y paidAt; PAID si cubre lo debido.
 * 3. SALIDA: solo si no se debe nada. Tras pagar hay exitWindowMinutes para salir sin que suba el monto;
 *    pasado ese tiempo se cobra la diferencia. Una estadía sin costo (gracia) sale sin pagar (NOT_REQUIRED).
 * 4. COMPROBANTE: interno, lo emitirá payment-service.
 *
 * parking-service no guarda pagos: solo cuánto se cubrió (ver ChargeSettlement).
 *
 * Esta entidad conecta TODO el sistema:
 * - Vehículo (qué auto)
 * - Cliente (quién lo dejó/retiró)
 * - Ubicación (dónde se estacionó: Parking > Zone > Space)
 * - Tarifa (cuánto cuesta)
 * - Operadores (quién registró entrada/salida)
 * - Pago: cuánto se pagó y cuándo (el detalle está en payment-service)
 *
 * Dos formas de entrar (las dos usan las mismas reglas, ver registerEntry):
 * - MANUAL: la registra un operador desde el panel; el operador es obligatorio.
 * - CAMERA_AI: la registra iot-service tras leer la placa con IA; no hay operador, pero sí
 *   captura (captureId), foto y confianza de la lectura.
 *
 * Documento del conductor: opcional. Sin documento la estadía queda ligada solo a la placa
 * (sin Customer). Seguridad anti-robo: si el parqueo exige documento (Parking.requireDriverDocument)
 * y se entró con documento, la salida debe presentar el mismo.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public class Transaction {

   // ========================= CAMPOS DE IDENTIDAD =========================

   private Long id;

   // ========================= RELACIONES (FKs) =========================

   private Long vehicleId;                                // FK a Vehicle (NOT NULL)
   private Long customerId;                               // FK a Customer (nullable: sin documento no hay cliente)

   // ========================= JERARQUÍA DE UBICACIÓN =========================
   // Parking > Zone > Space (jerarquía completa para referencia y reportes)

   private Long parkingId;                                // FK a Parking (NOT NULL)
   private Long zoneId;                                   // FK a Zone (NOT NULL)
   private Long spaceId;                                  // FK a Space (NOT NULL): el que se descuenta de la disponibilidad

   // ========================= TARIFA =========================

   private Long rateId;                                   // FK a Rate (NOT NULL)
   // Tarifa aplicada al momento de ENTRADA

   // ========================= SEGURIDAD - DOCUMENTOS DUPLICADOS =========================

   private Long entryDocumentTypeId;                      // FK a DocumentType (nullable: documento opcional)
   private String entryDocumentNumber;                    // Documento usado en ENTRADA (nullable)

   private Long exitDocumentTypeId;                       // FK a DocumentType (nullable)
   private String exitDocumentNumber;                     // Documento usado en SALIDA (nullable)
   // Sistema valida: entry == exit (anti-robo)

   // ========================= TIEMPOS =========================

   private LocalDateTime entryTime;                       // Hora de entrada (NOT NULL)
   private LocalDateTime exitTime;                        // Hora de salida (nullable - NULL si aún dentro)
   private Integer durationMinutes;                       // Duración en minutos (calculado)

   // ========================= OPERADORES Y MÉTODOS =========================

   private Long entryOperatorId;                          // FK a User/auth (nullable si entró por cámara)
   private Long exitOperatorId;                           // FK a User/auth (nullable)

   private String entryMethod;                            // MANUAL, CAMERA_AI, SENSOR
   private String exitMethod;                             // MANUAL, CAMERA_AI, SENSOR

   // ========================= EVIDENCIA (FOTOS DE CÁMARAS) =========================

   private String entryPhotoUrl;                          // URL foto entrada (nullable)
   private String exitPhotoUrl;                           // URL foto salida (nullable)

   private Double entryPlateConfidence;                   // Confianza IA placa entrada (0.0-1.0)
   private Double exitPlateConfidence;                    // Confianza IA placa salida (0.0-1.0)

   private String entryCaptureId;                         // Captura de iot-service que originó la entrada (única)
   private String exitCaptureId;                          // Captura de iot-service de la salida

   // ========================= MONTOS =========================

   private BigDecimal calculatedAmount;                   // Monto calculado (tarifa × duración)
   private BigDecimal discountAmount;                     // Descuentos aplicados (default 0.00)
   private BigDecimal totalAmount;                        // Monto final a pagar
   private String currency;                               // Moneda (default "PEN")
   private BigDecimal paidAmount;                         // Pagado (neto de devoluciones), según payment-service
   private LocalDateTime paidAt;                          // Último pago (inicia la ventana para salir)

   // ========================= ESTADOS =========================

   private String status;                                 // ACTIVE, COMPLETED, CANCELLED
   private String paymentStatus;                          // PENDING, PAID, NOT_REQUIRED, REFUNDED, OVERDUE

   // ========================= COMPROBANTE DIGITAL =========================

   private Boolean receiptSent;                           // ¿Se envió comprobante?
   private LocalDateTime receiptSentAt;                   // Cuándo se envió
   private String receiptWhatsAppStatus;                  // SENT, DELIVERED, READ, FAILED
   private String receiptEmailStatus;                     // SENT, DELIVERED, OPENED, FAILED

   // ========================= NOTAS Y OBSERVACIONES =========================

   private String notes;                                  // Observaciones generales
   private String cancellationReason;                     // Razón si fue cancelada

   // ========================= AUDITORÍA =========================

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;

   // ========================= CONSTANTES - ESTADOS DE TRANSACCIÓN =========================

   public static final String STATUS_ACTIVE = "ACTIVE";
   public static final String STATUS_COMPLETED = "COMPLETED";
   public static final String STATUS_CANCELLED = "CANCELLED";

   // ========================= CONSTANTES - ESTADOS DE PAGO =========================

   public static final String PAYMENT_STATUS_PENDING = "PENDING";
   public static final String PAYMENT_STATUS_PAID = "PAID";
   public static final String PAYMENT_STATUS_OVERDUE = "OVERDUE";
   /** Estadía sin costo (salió dentro del tiempo de gracia de la tarifa) */
   public static final String PAYMENT_STATUS_NOT_REQUIRED = "NOT_REQUIRED";
   /** Se devolvió el pago después de la salida */
   public static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";

   // ========================= CONSTANTES - MÉTODOS DE REGISTRO =========================

   public static final String METHOD_MANUAL = "MANUAL";
   public static final String METHOD_CAMERA_AI = "CAMERA_AI";
   public static final String METHOD_SENSOR = "SENSOR";

   /** Métodos válidos para registrar entrada o salida (SENSOR solo marca ocupación, no estadías) */
   public static final java.util.Set<String> REGISTRATION_METHODS = java.util.Set.of(METHOD_MANUAL, METHOD_CAMERA_AI);

   // ========================= DATOS DE ENTRADA Y SALIDA =========================

   /**
    * Datos para registrar una entrada.
    *
    * @param vehicleId       vehículo (obligatorio)
    * @param customerId      cliente: solo si se dio documento
    * @param parkingId       parqueo (obligatorio)
    * @param zoneId          zona (obligatoria)
    * @param spaceId         espacio que se descuenta (obligatorio)
    * @param rateId          tarifa del turno vigente (obligatoria)
    * @param documentTypeId  tipo de documento del conductor (opcional, va junto con el número)
    * @param documentNumber  número de documento ya normalizado (opcional)
    * @param method          MANUAL o CAMERA_AI
    * @param operatorId      operador (obligatorio si es MANUAL)
    * @param captureId       captura de iot-service (obligatoria si es CAMERA_AI)
    * @param photoUrl        foto de la entrada (opcional)
    * @param plateConfidence confianza de la IA 0.0-1.0 (obligatoria si es CAMERA_AI)
    */
   public record EntryData(Long vehicleId, Long customerId, Long parkingId, Long zoneId, Long spaceId,
                           Long rateId, Long documentTypeId, String documentNumber, String method,
                           Long operatorId, String captureId, String photoUrl, Double plateConfidence) {
   }

   /**
    * Datos para registrar una salida.
    *
    * @param documentTypeId  tipo de documento presentado (opcional, junto con el número)
    * @param documentNumber  número ya normalizado (opcional)
    * @param method          MANUAL o CAMERA_AI
    * @param operatorId      operador (obligatorio si es MANUAL)
    * @param captureId       captura de iot-service (obligatoria si es CAMERA_AI)
    * @param photoUrl        foto de la salida (opcional)
    * @param plateConfidence confianza de la IA (obligatoria si es CAMERA_AI)
    */
   public record ExitData(Long documentTypeId, String documentNumber, String method, Long operatorId,
                          String captureId, String photoUrl, Double plateConfidence) {

      /** Salida manual sin foto */
      public static ExitData manual(Long documentTypeId, String documentNumber, Long operatorId) {
         return new ExitData(documentTypeId, documentNumber, METHOD_MANUAL, operatorId, null, null, null);
      }
   }

   // ========================= CONSTRUCTORES =========================

   /**
    * Constructor vacío - Inicializa con valores por defecto.
    */
   public Transaction() {
      this.status = STATUS_ACTIVE;
      this.paymentStatus = PAYMENT_STATUS_PENDING;
      this.currency = "PEN";
      this.discountAmount = BigDecimal.ZERO;
      this.paidAmount = BigDecimal.ZERO;
      this.receiptSent = false;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = LocalDateTime.now();
   }


   // ========================= MÉTODOS DE NEGOCIO - ENTRADA =========================

   /**
    * Registra la entrada de un vehículo, manual o por cámara, con las mismas reglas:
    * - vehículo, parqueo, zona, espacio y tarifa son obligatorios;
    * - el documento es opcional, pero tipo y número van juntos, y con documento hay cliente;
    * - MANUAL exige operador; CAMERA_AI exige captura y confianza (0.0 a 1.0).
    *
    * @param data datos de la entrada
    * @param now  hora de la entrada
    * @return la transacción ACTIVE con el pago PENDING
    */
   public static Transaction registerEntry(EntryData data, LocalDateTime now) {
      if (data == null) {
         throw new ParkingDomainException("Faltan los datos de la entrada");
      }
      requireId(data.vehicleId(), "vehículo");
      requireId(data.parkingId(), "parqueo");
      requireId(data.zoneId(), "zona");
      requireId(data.spaceId(), "espacio");
      requireId(data.rateId(), "tarifa");

      boolean hasDocument = validateDocumentPair(data.documentTypeId(), data.documentNumber());
      if (hasDocument && data.customerId() == null) {
         throw new ParkingDomainException("Con documento del conductor debe indicarse el cliente");
      }
      if (!hasDocument && data.customerId() != null) {
         throw new ParkingDomainException("Sin documento del conductor no se asocia un cliente");
      }

      String method = validateMethod(data.method(), data.operatorId(), data.captureId(), data.plateConfidence());

      Transaction t = new Transaction();
      t.vehicleId = data.vehicleId();
      t.customerId = data.customerId();
      t.parkingId = data.parkingId();
      t.zoneId = data.zoneId();
      t.spaceId = data.spaceId();
      t.rateId = data.rateId();
      t.entryDocumentTypeId = hasDocument ? data.documentTypeId() : null;
      t.entryDocumentNumber = hasDocument ? data.documentNumber().trim() : null;
      t.entryMethod = method;
      t.entryOperatorId = data.operatorId();
      t.entryCaptureId = blankToNull(data.captureId());
      t.entryPhotoUrl = blankToNull(data.photoUrl());
      t.entryPlateConfidence = data.plateConfidence();
      t.entryTime = now;
      t.createdAt = now;
      t.updatedAt = now;
      t.createdBy = data.operatorId();
      t.updatedBy = data.operatorId();
      return t;
   }

   /** true si se registró el documento del conductor al entrar */
   public boolean hasDriverDocument() {
      return entryDocumentTypeId != null && entryDocumentNumber != null;
   }

   /**
    * Cambia el espacio que ocupa la estadía (el conductor eligió otro, o lo corrige el operador,
    * un sensor o la cámara aérea). El servicio libera el anterior y ocupa el nuevo.
    *
    * @param newSpaceId espacio nuevo, de la misma zona (lo valida el servicio)
    * @param userId     quién hizo el cambio (null si fue un dispositivo)
    * @return el espacio anterior, para liberarlo
    */
   public Long reassignSpace(Long newSpaceId, Long userId) {
      if (!isActive()) {
         throw new ParkingDomainException("Solo se cambia el espacio de una estadía en curso");
      }
      requireId(newSpaceId, "espacio");
      if (newSpaceId.equals(spaceId)) {
         throw new ParkingDomainException("El vehículo ya está en ese espacio");
      }
      Long previous = this.spaceId;
      this.spaceId = newSpaceId;
      this.updatedBy = userId;
      this.updatedAt = LocalDateTime.now();
      return previous;
   }

   /**
    * Registra la entrada del vehículo con operador.
    *
    * @param operatorId ID del operador que registra
    */
   public void recordEntry(Long operatorId) {
      this.entryOperatorId = operatorId;
      this.entryTime = LocalDateTime.now();
      this.status = STATUS_ACTIVE;
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Registra evidencia fotográfica de entrada.
    *
    * @param photoUrl URL de la foto
    * @param plateConfidence confianza de la IA en la placa
    */
   public void recordEntryPhoto(String photoUrl, Double plateConfidence) {
      this.entryPhotoUrl = photoUrl;
      this.entryPlateConfidence = plateConfidence;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= MÉTODOS DE NEGOCIO - SALIDA =========================


   /**
    * Registra la salida (manual o por cámara).
    *
    * Orden de las comprobaciones (si una falla, la transacción no cambia):
    * 1. Que siga dentro y los datos de salida sean coherentes con el método.
    * 2. Anti-robo: si el parqueo exige documento y se entró con documento, debe presentarse el mismo.
    *    Si se entró sin documento (el interruptor estaba apagado), sale por placa.
    * 3. Que no se deba nada (se paga antes de salir).
    *
    * @param exit                  datos de la salida
    * @param rate                  tarifa de la estadía
    * @param now                   hora de la salida
    * @param exitWindowMinutes     minutos para salir tras pagar sin que suba el monto
    * @param requireDriverDocument interruptor del parqueo (Parking.isDriverDocumentRequired)
    * @throws DocumentMismatchException si falta el documento o no coincide
    * @throws com.winnersystems.smartparking.parking.domain.exception.PaymentRequiredException si falta pagar
    */
   public void completeExit(ExitData exit, Rate rate, LocalDateTime now, int exitWindowMinutes,
                            boolean requireDriverDocument) {
      if (!isActive()) {
         throw new ParkingDomainException("La estadía ya no está en curso");
      }
      if (exit == null) {
         throw new ParkingDomainException("Faltan los datos de la salida");
      }
      boolean hasExitDocument = validateDocumentPair(exit.documentTypeId(), exit.documentNumber());
      String method = validateMethod(exit.method(), exit.operatorId(), exit.captureId(), exit.plateConfidence());

      if (requireDriverDocument && hasDriverDocument()) {
         boolean matches = hasExitDocument
               && entryDocumentTypeId.equals(exit.documentTypeId())
               && entryDocumentNumber.equals(exit.documentNumber().trim());
         if (!matches) {
            throw new DocumentMismatchException(entryDocumentNumber,
                  hasExitDocument ? exit.documentNumber().trim() : null, id);
         }
      }

      BigDecimal due = amountDueAt(now, rate, exitWindowMinutes);
      if (due.signum() > 0) {
         throw new com.winnersystems.smartparking.parking.domain.exception.PaymentRequiredException(id, due);
      }
      this.exitMethod = method;
      this.exitCaptureId = blankToNull(exit.captureId());
      if (exit.photoUrl() != null || exit.plateConfidence() != null) {
         this.exitPhotoUrl = blankToNull(exit.photoUrl());
         this.exitPlateConfidence = exit.plateConfidence();
      }
      this.updatedBy = exit.operatorId();
      // Costo bruto de la tarifa; calculateTotal resta el descuento
      this.calculatedAmount = rate.calculateCost(minutesUntil(billingCutoff(now, exitWindowMinutes)));
      this.calculateTotal();
      this.exitDocumentTypeId = hasExitDocument ? exit.documentTypeId() : null;
      this.exitDocumentNumber = hasExitDocument ? exit.documentNumber().trim() : null;
      this.exitOperatorId = exit.operatorId();
      this.exitTime = now;
      this.status = STATUS_COMPLETED;
      this.calculateDuration();
      this.paymentStatus = totalAmount.signum() == 0 ? PAYMENT_STATUS_NOT_REQUIRED : PAYMENT_STATUS_PAID;
      this.updatedAt = now;
   }

   /**
    * Calcula la duración del estacionamiento.
    */
   public void calculateDuration() {
      if (entryTime != null && exitTime != null) {
         Duration duration = Duration.between(entryTime, exitTime);
         this.durationMinutes = (int) duration.toMinutes();
      }
   }

   /**
    * Registra evidencia fotográfica de salida.
    *
    * @param photoUrl URL de la foto
    * @param plateConfidence confianza de la IA en la placa
    */
   public void recordExitPhoto(String photoUrl, Double plateConfidence) {
      this.exitPhotoUrl = photoUrl;
      this.exitPlateConfidence = plateConfidence;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= MÉTODOS DE NEGOCIO - SEGURIDAD =========================

   /**
    * Verifica que el documento de salida coincida con el de entrada.
    * Seguridad anti-robo.
    *
    * @return true si los documentos coinciden
    */
   public boolean verifyDocumentMatch() {
      if (entryDocumentNumber == null || exitDocumentNumber == null) {
         return false;
      }
      return entryDocumentNumber.equals(exitDocumentNumber) &&
            Objects.equals(entryDocumentTypeId, exitDocumentTypeId);
   }

   // ========================= MÉTODOS DE NEGOCIO - CÁLCULO DE MONTOS =========================

   /**
    * Aplica el monto calculado por la tarifa (Rate.calculateCost), que decide cómo se
    * cobra el tiempo: por fracción, mínimo, gracia y tolerancia.
    *
    * @param amount monto de la estadía
    */
   public void applyCalculatedAmount(BigDecimal amount) {
      if (amount == null) {
         return;
      }
      this.calculatedAmount = amount;
      this.calculateTotal();
   }

   /**
    * Aplica un descuento.
    *
    * @param discount monto del descuento
    */
   public void applyDiscount(BigDecimal discount) {
      this.discountAmount = discount;
      this.calculateTotal();
   }

   /**
    * Calcula el monto total final.
    */
   public void calculateTotal() {
      if (calculatedAmount == null) {
         this.totalAmount = BigDecimal.ZERO;
         return;
      }

      BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
      this.totalAmount = calculatedAmount.subtract(discount);

      if (this.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
         this.totalAmount = BigDecimal.ZERO;
      }
   }

   // ========================= MÉTODOS DE NEGOCIO - PAGO =========================

   /**
    * Hasta qué momento se cobra: si pagó y sale dentro de la ventana, hasta la hora del pago;
    * si no, hasta ahora.
    */
   public LocalDateTime billingCutoff(LocalDateTime now, int exitWindowMinutes) {
      boolean withinWindow = paidAt != null && getPaidAmount().signum() > 0
            && !now.isAfter(paidAt.plusMinutes(exitWindowMinutes));
      return withinWindow ? paidAt : now;
   }

   /** Costo de la estadía hasta cutoff, con las reglas de la tarifa y el descuento */
   public BigDecimal costUntil(LocalDateTime cutoff, Rate rate) {
      BigDecimal cost = rate.calculateCost(minutesUntil(cutoff));
      BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
      BigDecimal total = cost.subtract(discount);
      return total.signum() < 0 ? BigDecimal.ZERO : total;
   }

   private int minutesUntil(LocalDateTime cutoff) {
      return (int) Math.max(0, Duration.between(entryTime, cutoff).toMinutes());
   }

   /** Lo que falta pagar para salir ahora */
   public BigDecimal amountDueAt(LocalDateTime now, Rate rate, int exitWindowMinutes) {
      BigDecimal due = costUntil(billingCutoff(now, exitWindowMinutes), rate).subtract(getPaidAmount());
      return due.signum() < 0 ? BigDecimal.ZERO : due;
   }

   /**
    * Registra lo que avisa payment-service: el total pagado (neto de devoluciones) y la hora del último pago.
    * Dentro: PAID si cubre lo debido a la hora del pago. Ya salió: PAID, o REFUNDED si se devolvió algo.
    */
   public void applySettlement(BigDecimal netPaid, LocalDateTime lastPaidAt, Rate rate) {
      this.paidAmount = netPaid == null || netPaid.signum() < 0 ? BigDecimal.ZERO : netPaid;
      this.paidAt = paidAmount.signum() > 0 ? lastPaidAt : null;
      if (isActive()) {
         boolean covered = paidAt != null && costUntil(paidAt, rate).compareTo(paidAmount) <= 0;
         this.paymentStatus = covered ? PAYMENT_STATUS_PAID : PAYMENT_STATUS_PENDING;
      } else if (isCompleted()) {
         BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
         if (total.signum() == 0) {
            this.paymentStatus = PAYMENT_STATUS_NOT_REQUIRED;
         } else {
            this.paymentStatus = paidAmount.compareTo(total) >= 0 ? PAYMENT_STATUS_PAID : PAYMENT_STATUS_REFUNDED;
         }
      }
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Marca la transacción como vencida (sin pago).
    */
   public void markAsOverdue() {
      this.paymentStatus = PAYMENT_STATUS_OVERDUE;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= MÉTODOS DE NEGOCIO - COMPROBANTE =========================

   /**
    * Registra el envío del comprobante digital.
    */
   public void markReceiptAsSent() {
      this.receiptSent = true;
      this.receiptSentAt = LocalDateTime.now();
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Actualiza el estado de envío por WhatsApp.
    *
    * @param status estado del envío
    */
   public void updateWhatsAppStatus(String status) {
      this.receiptWhatsAppStatus = status;
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Actualiza el estado de envío por Email.
    *
    * @param status estado del envío
    */
   public void updateEmailStatus(String status) {
      this.receiptEmailStatus = status;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= MÉTODOS DE NEGOCIO - CANCELACIÓN =========================

   /**
    * Cancela una estadía registrada por error (placa equivocada, vehículo que no llegó a entrar).
    * Solo si sigue en curso y no tiene pagos. El servicio libera el espacio.
    *
    * @param reason razón de la cancelación
    * @param userId usuario que cancela
    */
   public void cancel(String reason, Long userId) {
      if (!isActive()) {
         throw new ParkingDomainException("Solo se cancela una estadía en curso");
      }
      if (reason == null || reason.isBlank()) {
         throw new ParkingDomainException("Indica el motivo de la cancelación");
      }
      // Lo cobrado está en payment-service: primero se devuelve allí y luego se cancela
      if (getPaidAmount().signum() > 0) {
         throw new ParkingDomainException("La estadía tiene pagos: devuélvelos antes de cancelarla");
      }
      this.status = STATUS_CANCELLED;
      this.cancellationReason = reason.trim();
      this.updatedBy = userId;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= MÉTODOS DE CONSULTA - ESTADOS =========================

   /**
    * Verifica si el vehículo está actualmente dentro del parqueo.
    *
    * @return true si status = ACTIVE
    */
   public boolean isActive() {
      return STATUS_ACTIVE.equals(status);
   }

   /**
    * Verifica si la transacción está completada.
    *
    * @return true si status = COMPLETED
    */
   public boolean isCompleted() {
      return STATUS_COMPLETED.equals(status);
   }

   /**
    * Verifica si la transacción fue cancelada.
    *
    * @return true si status = CANCELLED
    */
   public boolean isCancelled() {
      return STATUS_CANCELLED.equals(status);
   }

   /**
    * Verifica si está pendiente de pago.
    *
    * @return true si paymentStatus = PENDING
    */
   public boolean isPending() {
      return PAYMENT_STATUS_PENDING.equals(paymentStatus);
   }

   /**
    * Verifica si ya fue pagada.
    *
    * @return true si paymentStatus = PAID
    */
   public boolean isPaid() {
      return PAYMENT_STATUS_PAID.equals(paymentStatus);
   }

   /**
    * Verifica si está vencida (sin pago).
    *
    * @return true si paymentStatus = OVERDUE
    */
   public boolean isOverdue() {
      return PAYMENT_STATUS_OVERDUE.equals(paymentStatus);
   }

   /**
    * Verifica si el vehículo aún está dentro.
    *
    * @return true si exitTime es null
    */
   public boolean isVehicleInside() {
      return exitTime == null;
   }

   /**
    * Verifica si el comprobante fue enviado.
    *
    * @return true si receiptSent = true
    */
   public boolean isReceiptSent() {
      return Boolean.TRUE.equals(receiptSent);
   }

   // ========================= MÉTODOS DE CONSULTA - INFORMACIÓN =========================

   /**
    * Obtiene la duración en formato legible.
    *
    * @return duración formateada (ej: "3h 30min")
    */
   public String getFormattedDuration() {
      if (durationMinutes == null) {
         return "0min";
      }

      int hours = durationMinutes / 60;
      int minutes = durationMinutes % 60;

      if (hours > 0 && minutes > 0) {
         return hours + "h " + minutes + "min";
      } else if (hours > 0) {
         return hours + "h";
      } else {
         return minutes + "min";
      }
   }

   /**
    * Obtiene una descripción completa de la transacción.
    *
    * @return descripción formateada
    */
   public String getDescription() {
      return "Transaction #" + id + " [" + status + "/" + paymentStatus + "]";
   }

   // ========================= MÉTODOS DE VALIDACIÓN =========================

   /**
    * Verifica si la transacción tiene todos los datos necesarios para entrada.
    *
    * @return true si tiene datos completos de entrada
    */
   public boolean hasCompleteEntryData() {
      return vehicleId != null && parkingId != null && zoneId != null && spaceId != null
            && rateId != null && entryTime != null && entryMethod != null;
   }

   /**
    * Verifica si la transacción tiene todos los datos necesarios para salida.
    *
    * @return true si tiene datos completos de salida
    */
   public boolean hasCompleteExitData() {
      return exitTime != null && exitMethod != null && durationMinutes != null;
   }

   /**
    * Verifica si tiene montos calculados.
    *
    * @return true si tiene calculatedAmount y totalAmount
    */
   public boolean hasCalculatedAmounts() {
      return calculatedAmount != null && totalAmount != null;
   }

   // ========================= GETTERS Y SETTERS =========================

   public Long getId() {
      return id;
   }

   public void setId(Long id) {
      this.id = id;
   }

   public Long getVehicleId() {
      return vehicleId;
   }

   public void setVehicleId(Long vehicleId) {
      this.vehicleId = vehicleId;
   }

   public Long getCustomerId() {
      return customerId;
   }

   public void setCustomerId(Long customerId) {
      this.customerId = customerId;
   }

   public Long getParkingId() {
      return parkingId;
   }

   public void setParkingId(Long parkingId) {
      this.parkingId = parkingId;
   }

   public Long getZoneId() {
      return zoneId;
   }

   public void setZoneId(Long zoneId) {
      this.zoneId = zoneId;
   }

   public Long getSpaceId() {
      return spaceId;
   }

   public void setSpaceId(Long spaceId) {
      this.spaceId = spaceId;
   }

   public Long getRateId() {
      return rateId;
   }

   public void setRateId(Long rateId) {
      this.rateId = rateId;
   }

   public Long getEntryDocumentTypeId() {
      return entryDocumentTypeId;
   }

   public void setEntryDocumentTypeId(Long entryDocumentTypeId) {
      this.entryDocumentTypeId = entryDocumentTypeId;
   }

   public String getEntryDocumentNumber() {
      return entryDocumentNumber;
   }

   public void setEntryDocumentNumber(String entryDocumentNumber) {
      this.entryDocumentNumber = entryDocumentNumber;
   }

   public Long getExitDocumentTypeId() {
      return exitDocumentTypeId;
   }

   public void setExitDocumentTypeId(Long exitDocumentTypeId) {
      this.exitDocumentTypeId = exitDocumentTypeId;
   }

   public String getExitDocumentNumber() {
      return exitDocumentNumber;
   }

   public void setExitDocumentNumber(String exitDocumentNumber) {
      this.exitDocumentNumber = exitDocumentNumber;
   }

   public LocalDateTime getEntryTime() {
      return entryTime;
   }

   public void setEntryTime(LocalDateTime entryTime) {
      this.entryTime = entryTime;
   }

   public LocalDateTime getExitTime() {
      return exitTime;
   }

   public void setExitTime(LocalDateTime exitTime) {
      this.exitTime = exitTime;
   }

   public Integer getDurationMinutes() {
      return durationMinutes;
   }

   public void setDurationMinutes(Integer durationMinutes) {
      this.durationMinutes = durationMinutes;
   }

   public Long getEntryOperatorId() {
      return entryOperatorId;
   }

   public void setEntryOperatorId(Long entryOperatorId) {
      this.entryOperatorId = entryOperatorId;
   }

   public Long getExitOperatorId() {
      return exitOperatorId;
   }

   public void setExitOperatorId(Long exitOperatorId) {
      this.exitOperatorId = exitOperatorId;
   }

   public String getEntryMethod() {
      return entryMethod;
   }

   public void setEntryMethod(String entryMethod) {
      this.entryMethod = entryMethod;
   }

   public String getExitMethod() {
      return exitMethod;
   }

   public void setExitMethod(String exitMethod) {
      this.exitMethod = exitMethod;
   }

   public String getEntryPhotoUrl() {
      return entryPhotoUrl;
   }

   public void setEntryPhotoUrl(String entryPhotoUrl) {
      this.entryPhotoUrl = entryPhotoUrl;
   }

   public String getExitPhotoUrl() {
      return exitPhotoUrl;
   }

   public void setExitPhotoUrl(String exitPhotoUrl) {
      this.exitPhotoUrl = exitPhotoUrl;
   }

   public Double getEntryPlateConfidence() {
      return entryPlateConfidence;
   }

   public void setEntryPlateConfidence(Double entryPlateConfidence) {
      this.entryPlateConfidence = entryPlateConfidence;
   }

   public Double getExitPlateConfidence() {
      return exitPlateConfidence;
   }

   public void setExitPlateConfidence(Double exitPlateConfidence) {
      this.exitPlateConfidence = exitPlateConfidence;
   }

   public String getEntryCaptureId() {
      return entryCaptureId;
   }

   public void setEntryCaptureId(String entryCaptureId) {
      this.entryCaptureId = entryCaptureId;
   }

   public String getExitCaptureId() {
      return exitCaptureId;
   }

   public void setExitCaptureId(String exitCaptureId) {
      this.exitCaptureId = exitCaptureId;
   }

   public BigDecimal getCalculatedAmount() {
      return calculatedAmount;
   }

   public void setCalculatedAmount(BigDecimal calculatedAmount) {
      this.calculatedAmount = calculatedAmount;
   }

   public BigDecimal getDiscountAmount() {
      return discountAmount;
   }

   public void setDiscountAmount(BigDecimal discountAmount) {
      this.discountAmount = discountAmount;
   }

   public BigDecimal getTotalAmount() {
      return totalAmount;
   }

   public void setTotalAmount(BigDecimal totalAmount) {
      this.totalAmount = totalAmount;
   }

   public BigDecimal getPaidAmount() { return paidAmount == null ? BigDecimal.ZERO : paidAmount; }
   public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

   public LocalDateTime getPaidAt() { return paidAt; }
   public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

   public String getCurrency() {
      return currency;
   }

   public void setCurrency(String currency) {
      this.currency = currency;
   }

   public String getStatus() {
      return status;
   }

   public void setStatus(String status) {
      this.status = status;
   }

   public String getPaymentStatus() {
      return paymentStatus;
   }

   public void setPaymentStatus(String paymentStatus) {
      this.paymentStatus = paymentStatus;
   }

   public Boolean getReceiptSent() {
      return receiptSent;
   }

   public void setReceiptSent(Boolean receiptSent) {
      this.receiptSent = receiptSent;
   }

   public LocalDateTime getReceiptSentAt() {
      return receiptSentAt;
   }

   public void setReceiptSentAt(LocalDateTime receiptSentAt) {
      this.receiptSentAt = receiptSentAt;
   }

   public String getReceiptWhatsAppStatus() {
      return receiptWhatsAppStatus;
   }

   public void setReceiptWhatsAppStatus(String receiptWhatsAppStatus) {
      this.receiptWhatsAppStatus = receiptWhatsAppStatus;
   }

   public String getReceiptEmailStatus() {
      return receiptEmailStatus;
   }

   public void setReceiptEmailStatus(String receiptEmailStatus) {
      this.receiptEmailStatus = receiptEmailStatus;
   }

   public String getNotes() {
      return notes;
   }

   public void setNotes(String notes) {
      this.notes = notes;
   }

   public String getCancellationReason() {
      return cancellationReason;
   }

   public void setCancellationReason(String cancellationReason) {
      this.cancellationReason = cancellationReason;
   }

   public LocalDateTime getCreatedAt() {
      return createdAt;
   }

   public void setCreatedAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public Long getCreatedBy() {
      return createdBy;
   }

   public void setCreatedBy(Long createdBy) {
      this.createdBy = createdBy;
   }

   public LocalDateTime getUpdatedAt() {
      return updatedAt;
   }

   public void setUpdatedAt(LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
   }

   public Long getUpdatedBy() {
      return updatedBy;
   }

   public void setUpdatedBy(Long updatedBy) {
      this.updatedBy = updatedBy;
   }

   // ========================= EQUALS, HASHCODE Y TOSTRING =========================

   /**
    * Dos transacciones son iguales si tienen el mismo ID.
    */
   // ========================= VALIDACIONES INTERNAS =========================

   private static void requireId(Long value, String field) {
      if (value == null) {
         throw new ParkingDomainException("Falta el " + field + " de la estadía");
      }
   }

   /**
    * Tipo y número de documento van juntos o ninguno.
    * @return true si se indicó documento
    */
   private static boolean validateDocumentPair(Long typeId, String number) {
      boolean hasType = typeId != null;
      boolean hasNumber = number != null && !number.isBlank();
      if (hasType != hasNumber) {
         throw new ParkingDomainException("El tipo y el número de documento se indican juntos");
      }
      return hasType;
   }

   /**
    * MANUAL exige operador; CAMERA_AI exige captura y una confianza entre 0 y 1.
    * @return el método normalizado
    */
   private static String validateMethod(String rawMethod, Long operatorId, String captureId, Double confidence) {
      String method = rawMethod == null ? METHOD_MANUAL : rawMethod.trim().toUpperCase();
      if (!REGISTRATION_METHODS.contains(method)) {
         throw new ParkingDomainException("Método de registro no válido: " + rawMethod);
      }
      if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
         throw new ParkingDomainException("La confianza de la lectura debe estar entre 0 y 1");
      }
      if (METHOD_MANUAL.equals(method) && operatorId == null) {
         throw new ParkingDomainException("El registro manual exige el operador");
      }
      if (METHOD_CAMERA_AI.equals(method)) {
         if (captureId == null || captureId.isBlank()) {
            throw new ParkingDomainException("El registro por cámara exige la captura");
         }
         if (confidence == null) {
            throw new ParkingDomainException("El registro por cámara exige la confianza de la lectura");
         }
      }
      return method;
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Transaction that = (Transaction) o;
      return Objects.equals(id, that.id);
   }

   /**
    * HashCode basado en ID únicamente.
    */
   @Override
   public int hashCode() {
      return Objects.hash(id);
   }

   /**
    * ToString para logging y debugging.
    */
   @Override
   public String toString() {
      return "Transaction{" +
            "id=" + id +
            ", vehicleId=" + vehicleId +
            ", customerId=" + customerId +
            ", parkingId=" + parkingId +
            ", zoneId=" + zoneId +
            ", spaceId=" + spaceId +
            ", status='" + status + '\'' +
            ", paymentStatus='" + paymentStatus + '\'' +
            ", entryTime=" + entryTime +
            ", exitTime=" + exitTime +
            ", duration='" + getFormattedDuration() + '\'' +
            ", totalAmount=" + totalAmount +
            ", receiptSent=" + receiptSent +
            '}';
   }
}