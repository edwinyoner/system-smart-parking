package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Lectura de placa que la IA no pudo confirmar (confianza menor a 0.85) y que espera a un operador.
 *
 * Flujo:
 * 1. iot-service envía la captura (foto, placa leída, confianza) y queda PENDING.
 * 2. El operador ve la foto en el panel y:
 *    - CONFIRMA: con la placa leída o corregida; el servicio registra la entrada o la salida
 *      y la revisión queda enlazada a esa transacción.
 *    - DESCARTA: con un motivo (no era un vehículo, foto ilegible, falsa alarma...).
 * La barrera espera a esa decisión.
 *
 * Reglas:
 * - captureId es único: si la cámara reenvía la misma captura no se abre otra revisión.
 * - La placa leída se guarda tal cual (puede no tener formato válido, por eso se revisa);
 *   la confirmada siempre pasa por PlateNumber.normalize.
 * - Una revisión resuelta (CONFIRMED o DISCARDED) ya no cambia: es historial.
 */
public class PlateReview {

   // ========================= CONSTANTES =========================

   public static final String DIRECTION_ENTRY = "ENTRY";
   public static final String DIRECTION_EXIT = "EXIT";
   public static final Set<String> DIRECTIONS = Set.of(DIRECTION_ENTRY, DIRECTION_EXIT);

   public static final String STATUS_PENDING = "PENDING";
   public static final String STATUS_CONFIRMED = "CONFIRMED";
   public static final String STATUS_DISCARDED = "DISCARDED";

   public static final int MAX_CAPTURE_ID = 64;
   public static final int MAX_DETECTED_PLATE = 20;
   public static final int MAX_REASON = 255;

   // ========================= CAMPOS =========================

   private Long id;
   private String captureId;            // Captura de iot-service (única)
   private String direction;            // ENTRY o EXIT
   private Long parkingId;
   private Long zoneId;                 // Zona de la cámara (obligatoria en la entrada)
   private String deviceId;             // Cámara que tomó la foto (opcional)

   private String detectedPlate;        // Lo que leyó la IA, tal cual (puede venir vacío o mal formado)
   private Double confidence;           // Confianza de la lectura (0.0 - 1.0)
   private String photoUrl;             // Foto para que el operador la vea

   private String status;
   private String confirmedPlate;       // Placa confirmada por el operador (ABC-123)
   private Long transactionId;          // Estadía creada o cerrada al confirmar
   private String discardReason;
   private Long resolvedBy;
   private LocalDateTime resolvedAt;

   private LocalDateTime createdAt;
   private LocalDateTime updatedAt;

   // ========================= CONSTRUCTORES =========================

   public PlateReview() {
   }

   /**
    * Abre una revisión para una lectura dudosa.
    *
    * @param captureId     captura de iot-service (obligatoria)
    * @param direction     ENTRY o EXIT
    * @param parkingId     parqueo (obligatorio)
    * @param zoneId        zona de la cámara (obligatoria si es ENTRY)
    * @param deviceId      cámara (opcional)
    * @param detectedPlate placa leída por la IA (opcional)
    * @param confidence    confianza 0.0 - 1.0 (obligatoria)
    * @param photoUrl      foto (opcional)
    * @param now           hora de la captura
    */
   public static PlateReview open(String captureId, String direction, Long parkingId, Long zoneId,
                                  String deviceId, String detectedPlate, Double confidence,
                                  String photoUrl, LocalDateTime now) {
      PlateReview review = new PlateReview();
      review.captureId = requireText(captureId, "la captura", MAX_CAPTURE_ID);
      review.direction = normalizeDirection(direction);
      if (parkingId == null) {
         throw new ParkingDomainException("La revisión debe indicar el parqueo");
      }
      if (DIRECTION_ENTRY.equals(review.direction) && zoneId == null) {
         throw new ParkingDomainException("La revisión de una entrada debe indicar la zona de la cámara");
      }
      if (confidence == null || confidence < 0.0 || confidence > 1.0) {
         throw new ParkingDomainException("La confianza de la lectura debe estar entre 0 y 1");
      }
      review.parkingId = parkingId;
      review.zoneId = zoneId;
      review.deviceId = blankToNull(deviceId);
      String plate = blankToNull(detectedPlate);
      review.detectedPlate = plate == null ? null : truncate(plate.toUpperCase(), MAX_DETECTED_PLATE);
      review.confidence = confidence;
      review.photoUrl = blankToNull(photoUrl);
      review.status = STATUS_PENDING;
      review.createdAt = now;
      review.updatedAt = now;
      return review;
   }

   // ========================= MÉTODOS DE NEGOCIO =========================

   /**
    * Sugerencia para el operador: la placa leída ya normalizada, o null si no tiene formato válido.
    */
   public String getSuggestedPlate() {
      return PlateNumber.isValid(detectedPlate) ? PlateNumber.normalize(detectedPlate) : null;
   }

   /**
    * El operador confirma la placa (la leída o la que corrigió).
    *
    * @param plate  placa confirmada
    * @param userId operador
    * @param now    hora de la confirmación
    * @return la placa normalizada, para registrar la entrada o la salida
    */
   public String confirm(String plate, Long userId, LocalDateTime now) {
      ensurePending();
      if (userId == null) {
         throw new ParkingDomainException("La confirmación exige el operador");
      }
      this.confirmedPlate = PlateNumber.normalize(plate);
      this.status = STATUS_CONFIRMED;
      this.resolvedBy = userId;
      this.resolvedAt = now;
      this.updatedAt = now;
      return confirmedPlate;
   }

   /** Enlaza la estadía que se creó o cerró al confirmar */
   public void linkTransaction(Long transactionId) {
      if (!isConfirmed()) {
         throw new ParkingDomainException("Solo una revisión confirmada se enlaza a una estadía");
      }
      if (transactionId == null) {
         throw new ParkingDomainException("Falta la estadía a enlazar");
      }
      this.transactionId = transactionId;
   }

   /**
    * El operador descarta la lectura (no era un vehículo, foto ilegible, falsa alarma...).
    *
    * @param reason motivo (obligatorio)
    * @param userId operador
    * @param now    hora
    */
   public void discard(String reason, Long userId, LocalDateTime now) {
      ensurePending();
      if (userId == null) {
         throw new ParkingDomainException("Descartar exige el operador");
      }
      this.discardReason = requireText(reason, "el motivo", MAX_REASON);
      this.status = STATUS_DISCARDED;
      this.resolvedBy = userId;
      this.resolvedAt = now;
      this.updatedAt = now;
   }

   public boolean isPending() {
      return STATUS_PENDING.equals(status);
   }

   public boolean isConfirmed() {
      return STATUS_CONFIRMED.equals(status);
   }

   public boolean isDiscarded() {
      return STATUS_DISCARDED.equals(status);
   }

   public boolean isEntry() {
      return DIRECTION_ENTRY.equals(direction);
   }

   // ========================= VALIDACIONES =========================

   private void ensurePending() {
      if (!isPending()) {
         throw new ParkingDomainException("La revisión ya fue resuelta");
      }
   }

   private static String normalizeDirection(String direction) {
      String value = direction == null ? "" : direction.trim().toUpperCase();
      if (!DIRECTIONS.contains(value)) {
         throw new ParkingDomainException("Dirección no válida: debe ser ENTRY o EXIT");
      }
      return value;
   }

   private static String requireText(String value, String field, int max) {
      if (value == null || value.isBlank()) {
         throw new ParkingDomainException("Falta " + field);
      }
      String trimmed = value.trim();
      if (trimmed.length() > max) {
         throw new ParkingDomainException("El valor de " + field + " supera " + max + " caracteres");
      }
      return trimmed;
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   private static String truncate(String value, int max) {
      return value.length() <= max ? value : value.substring(0, max);
   }

   // ========================= GETTERS Y SETTERS (para la persistencia) =========================

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }

   public String getCaptureId() { return captureId; }
   public void setCaptureId(String captureId) { this.captureId = captureId; }

   public String getDirection() { return direction; }
   public void setDirection(String direction) { this.direction = direction; }

   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }

   public Long getZoneId() { return zoneId; }
   public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

   public String getDeviceId() { return deviceId; }
   public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

   public String getDetectedPlate() { return detectedPlate; }
   public void setDetectedPlate(String detectedPlate) { this.detectedPlate = detectedPlate; }

   public Double getConfidence() { return confidence; }
   public void setConfidence(Double confidence) { this.confidence = confidence; }

   public String getPhotoUrl() { return photoUrl; }
   public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

   public String getStatus() { return status; }
   public void setStatus(String status) { this.status = status; }

   public String getConfirmedPlate() { return confirmedPlate; }
   public void setConfirmedPlate(String confirmedPlate) { this.confirmedPlate = confirmedPlate; }

   public Long getTransactionId() { return transactionId; }
   public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

   public String getDiscardReason() { return discardReason; }
   public void setDiscardReason(String discardReason) { this.discardReason = discardReason; }

   public Long getResolvedBy() { return resolvedBy; }
   public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }

   public LocalDateTime getResolvedAt() { return resolvedAt; }
   public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

   // ========================= EQUALS Y HASHCODE =========================

   /** Por ID; una revisión sin guardar solo es igual a sí misma */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PlateReview that)) return false;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return id != null ? Objects.hash(id) : System.identityHashCode(this);
   }

   @Override
   public String toString() {
      return "PlateReview{id=" + id + ", captureId='" + captureId + "', direction='" + direction
            + "', status='" + status + "'}";
   }
}
