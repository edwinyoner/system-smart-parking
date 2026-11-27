package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Infracción a las normas del estacionamiento, con multa opcional.
 *
 * Ciclo de vida:
 * - PENDING: registrada. Si tiene multa, se cobra con QR o efectivo desde payment-service.
 * - RESOLVED: cerrada, con uno de estos tipos de resolución:
 *     PAID      la multa se pagó por completo (lo avisa payment-service);
 *     DISMISSED anulada con un motivo (registro erróneo, se aclaró el caso);
 *     WARNING   cerrada con amonestación, solo si no tenía multa.
 *
 * Es un registro histórico: no se elimina, se anula. Mientras tenga pagos no se puede anular
 * ni cambiar la multa (primero se devuelve el pago en payment-service).
 * Se identifica por la placa: el vehículo se busca o se crea al registrarla.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 */
public class Infraction {

   // ========================= CONSTANTES =========================

   public static final String TYPE_OVERSTAY = "OVERSTAY";
   public static final String TYPE_UNPAID_EXIT = "UNPAID_EXIT";
   public static final String TYPE_WRONG_SPACE = "WRONG_SPACE";
   public static final String TYPE_DOUBLE_PARKING = "DOUBLE_PARKING";
   public static final String TYPE_NO_REGISTRATION = "NO_REGISTRATION";
   public static final String TYPE_ABANDONED = "ABANDONED";
   public static final String TYPE_DAMAGE = "DAMAGE";
   public static final String TYPE_OTHER = "OTHER";
   public static final Set<String> TYPES = Set.of(TYPE_OVERSTAY, TYPE_UNPAID_EXIT, TYPE_WRONG_SPACE,
         TYPE_DOUBLE_PARKING, TYPE_NO_REGISTRATION, TYPE_ABANDONED, TYPE_DAMAGE, TYPE_OTHER);

   public static final String SEVERITY_MINOR = "MINOR";
   public static final String SEVERITY_MODERATE = "MODERATE";
   public static final String SEVERITY_SEVERE = "SEVERE";
   public static final String SEVERITY_CRITICAL = "CRITICAL";
   public static final Set<String> SEVERITIES = Set.of(SEVERITY_MINOR, SEVERITY_MODERATE, SEVERITY_SEVERE,
         SEVERITY_CRITICAL);

   public static final String STATUS_PENDING = "PENDING";
   public static final String STATUS_RESOLVED = "RESOLVED";

   public static final String RESOLUTION_PAID = "PAID";
   public static final String RESOLUTION_DISMISSED = "DISMISSED";
   public static final String RESOLUTION_WARNING = "WARNING";

   /** Estado que se muestra: el de la tabla más "vencida" y el tipo de resolución */
   public static final String DISPLAY_OVERDUE = "OVERDUE";

   public static final String METHOD_MANUAL = "MANUAL";
   public static final String METHOD_SYSTEM = "SYSTEM";

   public static final int DESCRIPTION_MIN = 5;
   public static final int DESCRIPTION_MAX = 1000;
   public static final int NOTES_MAX = 500;
   public static final int REASON_MAX = 500;
   public static final int EVIDENCE_MAX_ITEMS = 5;
   public static final int EVIDENCE_URL_MAX = 500;
   public static final BigDecimal FINE_MAX = new BigDecimal("10000.00");

   /** Separador de las URL de evidencia en la columna (una por línea) */
   private static final String EVIDENCE_SEPARATOR = "\n";

   // ========================= CAMPOS =========================

   private Long id;
   private String infractionCode;              // INF-AAAA-NNNNNN, se genera tras guardar

   private Long parkingId;
   private Long zoneId;                        // opcional
   private Long spaceId;                       // opcional (exige zona)
   private Long transactionId;                 // opcional: la estadía relacionada
   private Long vehicleId;
   private String plateNumber;                 // copia normalizada, para listar y buscar
   private Long customerId;                    // el de la estadía, si entró con documento

   private String infractionType;
   private String severity;
   private LocalDateTime detectedAt;
   private Long detectedBy;
   private String detectedByName;              // copia del nombre al registrar (puede faltar)
   private String detectionMethod;
   private String description;
   private String evidence;                    // URL separadas por salto de línea

   private BigDecimal fineAmount;              // null = sin multa
   private String currency = "PEN";
   private LocalDate fineDueDate;

   private String status;
   private String resolutionType;
   private String resolution;                  // motivo o comentario de la resolución
   private LocalDateTime resolvedAt;
   private Long resolvedBy;                    // null si la resolvió el sistema (pago)

   private Boolean finePaid = false;
   private LocalDateTime finePaidAt;
   private BigDecimal finePaidAmount = BigDecimal.ZERO;
   private String finePaymentReference;

   private String notes;

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;

   public Infraction() {
   }

   // ========================= REGISTRO =========================

   /** Datos para registrar una infracción. El vehículo ya existe (se busca o crea por placa). */
   public record RegisterData(Long parkingId, Long zoneId, Long spaceId, Long transactionId,
                              Long vehicleId, String plateNumber, Long customerId,
                              String type, String severity, String description, List<String> evidence,
                              BigDecimal fineAmount, LocalDate fineDueDate, LocalDateTime detectedAt,
                              String notes, Long registeredBy) {
   }

   /**
    * Registra una infracción PENDING.
    *
    * @param defaultDueDays días para pagar si hay multa y no se indicó fecha límite
    */
   public static Infraction register(RegisterData d, LocalDateTime now, int defaultDueDays) {
      if (d.parkingId() == null) {
         throw new ParkingDomainException("El parqueo es obligatorio");
      }
      if (d.vehicleId() == null) {
         throw new ParkingDomainException("El vehículo es obligatorio");
      }
      if (d.spaceId() != null && d.zoneId() == null) {
         throw new ParkingDomainException("Para indicar el espacio también hay que indicar la zona");
      }
      Infraction i = new Infraction();
      i.parkingId = d.parkingId();
      i.zoneId = d.zoneId();
      i.spaceId = d.spaceId();
      i.transactionId = d.transactionId();
      i.vehicleId = d.vehicleId();
      i.plateNumber = PlateNumber.normalize(d.plateNumber());
      i.customerId = d.customerId();
      i.infractionType = validType(d.type());
      i.severity = validSeverity(d.severity());
      i.description = validDescription(d.description());
      i.evidence = joinEvidence(d.evidence());
      i.notes = validNotes(d.notes());
      i.detectedAt = validDetectedAt(d.detectedAt(), now);
      i.detectedBy = d.registeredBy();
      i.detectionMethod = METHOD_MANUAL;
      i.status = STATUS_PENDING;
      if (d.fineAmount() != null) {
         i.applyFine(d.fineAmount(), d.fineDueDate(), now.toLocalDate(), defaultDueDays);
      }
      i.createdAt = now;
      i.createdBy = d.registeredBy();
      i.updatedAt = now;
      i.updatedBy = d.registeredBy();
      return i;
   }

   /** Código legible a partir del ID (requiere que ya esté guardada) */
   public void generateInfractionCode() {
      if (id == null) {
         throw new IllegalStateException("No se puede generar el código sin ID");
      }
      int year = detectedAt != null ? detectedAt.getYear() : LocalDate.now().getYear();
      this.infractionCode = String.format("INF-%d-%06d", year, id);
   }

   // ========================= EDICIÓN =========================

   /** Datos editables mientras está pendiente y sin pagos */
   public record EditData(String type, String severity, String description, List<String> evidence, String notes) {
   }

   public void edit(EditData d, Long userId, LocalDateTime now) {
      ensureEditable();
      this.infractionType = validType(d.type());
      this.severity = validSeverity(d.severity());
      this.description = validDescription(d.description());
      this.evidence = joinEvidence(d.evidence());
      this.notes = validNotes(d.notes());
      touch(userId, now);
   }

   /**
    * Pone, cambia o quita (amount null) la multa. Solo pendiente y sin pagos.
    */
   public void changeFine(BigDecimal amount, LocalDate dueDate, Long userId, LocalDateTime now, int defaultDueDays) {
      ensureEditable();
      if (amount == null) {
         this.fineAmount = null;
         this.fineDueDate = null;
      } else {
         applyFine(amount, dueDate, now.toLocalDate(), defaultDueDays);
      }
      touch(userId, now);
   }

   private void applyFine(BigDecimal amount, LocalDate dueDate, LocalDate today, int defaultDueDays) {
      if (amount.signum() <= 0) {
         throw new ParkingDomainException("El monto de la multa debe ser mayor que cero");
      }
      if (amount.scale() > 2) {
         throw new ParkingDomainException("El monto de la multa admite hasta 2 decimales");
      }
      if (amount.compareTo(FINE_MAX) > 0) {
         throw new ParkingDomainException("El monto de la multa no puede superar S/ " + FINE_MAX);
      }
      LocalDate due = dueDate != null ? dueDate : today.plusDays(defaultDueDays);
      if (due.isBefore(today)) {
         throw new ParkingDomainException("La fecha límite de pago no puede estar en el pasado");
      }
      this.fineAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
      this.fineDueDate = due;
   }

   // ========================= RESOLUCIÓN =========================

   /** Anula la infracción. No se puede si recibió pagos: primero se devuelven. */
   public void dismiss(String reason, Long userId, LocalDateTime now) {
      ensurePending();
      if (hasPayments()) {
         throw new ResourceConflictException(
               "La multa tiene pagos registrados: devuélvelos en payment-service antes de anularla");
      }
      resolve(RESOLUTION_DISMISSED, validReason(reason, true), userId, now);
   }

   /** Cierra con amonestación una infracción sin multa */
   public void closeWithWarning(String comment, Long userId, LocalDateTime now) {
      ensurePending();
      if (hasFine()) {
         throw new ResourceConflictException(
               "La infracción tiene multa: se cierra al pagarla, o se anula si no corresponde");
      }
      resolve(RESOLUTION_WARNING, validReason(comment, false), userId, now);
   }

   private void resolve(String type, String text, Long userId, LocalDateTime now) {
      this.status = STATUS_RESOLVED;
      this.resolutionType = type;
      this.resolution = text;
      this.resolvedAt = now;
      this.resolvedBy = userId;
      touch(userId, now);
   }

   // ========================= PAGO DE LA MULTA =========================

   /** Se puede cobrar: pendiente, con multa y con saldo */
   public boolean isFinePayable() {
      return isPending() && hasFine() && getFineAmountDue().signum() > 0;
   }

   /** Lo que falta pagar de la multa */
   public BigDecimal getFineAmountDue() {
      if (!hasFine()) {
         return BigDecimal.ZERO;
      }
      BigDecimal due = fineAmount.subtract(paidSoFar());
      return due.signum() < 0 ? BigDecimal.ZERO : due;
   }

   /**
    * Registra lo que avisa payment-service: el total pagado (neto de devoluciones).
    * Pagada por completo: se resuelve como PAID. Si luego se devuelve el pago, vuelve a PENDING.
    *
    * @param lastPaymentId pago más reciente, como referencia (el detalle está en payment-service)
    */
   public void applyFineSettlement(BigDecimal netPaid, LocalDateTime paidAt, Long lastPaymentId) {
      this.finePaidAmount = netPaid == null || netPaid.signum() < 0 ? BigDecimal.ZERO : netPaid;
      this.finePaymentReference = lastPaymentId == null ? null : "PAGO-" + lastPaymentId;
      boolean paid = hasFine() && finePaidAmount.compareTo(fineAmount) >= 0;
      boolean wasPaid = isFinePaid();
      this.finePaid = paid;
      this.finePaidAt = paid ? paidAt : null;
      LocalDateTime when = paidAt != null ? paidAt : LocalDateTime.now();
      if (paid && isPending()) {
         resolve(RESOLUTION_PAID, "Multa pagada (pago N.° " + lastPaymentId + ")", null, when);
      } else if (!paid && wasPaid && RESOLUTION_PAID.equals(resolutionType)) {
         // Se devolvió el pago: la multa vuelve a estar pendiente
         this.status = STATUS_PENDING;
         this.resolutionType = null;
         this.resolution = null;
         this.resolvedAt = null;
         this.resolvedBy = null;
      }
      this.updatedAt = when;
   }

   // ========================= CONSULTAS =========================

   public boolean isPending() {
      return STATUS_PENDING.equals(status);
   }

   public boolean isResolved() {
      return STATUS_RESOLVED.equals(status);
   }

   public boolean hasFine() {
      return fineAmount != null && fineAmount.signum() > 0;
   }

   public boolean isFinePaid() {
      return Boolean.TRUE.equals(finePaid);
   }

   /** Recibió algún pago (neto de devoluciones) */
   public boolean hasPayments() {
      return paidSoFar().signum() > 0;
   }

   /** Pendiente, con saldo y pasada la fecha límite */
   public boolean isFineOverdue(LocalDate today) {
      return isFinePayable() && fineDueDate != null && today.isAfter(fineDueDate);
   }

   /** Se pueden cambiar sus datos o su multa */
   public boolean isEditable() {
      return isPending() && !hasPayments();
   }

   /** PENDING, OVERDUE, PAID, DISMISSED o WARNING: lo que ve el usuario */
   public String displayStatus(LocalDate today) {
      if (isResolved()) {
         return resolutionType;
      }
      return isFineOverdue(today) ? DISPLAY_OVERDUE : STATUS_PENDING;
   }

   public List<String> getEvidenceList() {
      if (evidence == null || evidence.isBlank()) {
         return List.of();
      }
      return Arrays.stream(evidence.split(EVIDENCE_SEPARATOR)).filter(s -> !s.isBlank()).toList();
   }

   private BigDecimal paidSoFar() {
      return finePaidAmount == null ? BigDecimal.ZERO : finePaidAmount;
   }

   // ========================= VALIDACIONES =========================

   private void ensurePending() {
      if (!isPending()) {
         throw new ResourceConflictException("La infracción ya está cerrada");
      }
   }

   private void ensureEditable() {
      ensurePending();
      if (hasPayments()) {
         throw new ResourceConflictException(
               "La multa ya tiene pagos: no se puede modificar (devuelve el pago primero)");
      }
   }

   private void touch(Long userId, LocalDateTime now) {
      this.updatedBy = userId;
      this.updatedAt = now;
   }

   private static String validType(String type) {
      String t = type == null ? null : type.trim().toUpperCase();
      if (t == null || !TYPES.contains(t)) {
         throw new ParkingDomainException("Tipo de infracción no válido: " + type);
      }
      return t;
   }

   private static String validSeverity(String severity) {
      String s = severity == null ? null : severity.trim().toUpperCase();
      if (s == null || !SEVERITIES.contains(s)) {
         throw new ParkingDomainException("Gravedad no válida: " + severity);
      }
      return s;
   }

   private static String validDescription(String description) {
      String d = description == null ? "" : description.trim();
      if (d.length() < DESCRIPTION_MIN || d.length() > DESCRIPTION_MAX) {
         throw new ParkingDomainException(
               "La descripción debe tener entre " + DESCRIPTION_MIN + " y " + DESCRIPTION_MAX + " caracteres");
      }
      return d;
   }

   private static String validNotes(String notes) {
      if (notes == null || notes.isBlank()) {
         return null;
      }
      String n = notes.trim();
      if (n.length() > NOTES_MAX) {
         throw new ParkingDomainException("Las observaciones admiten hasta " + NOTES_MAX + " caracteres");
      }
      return n;
   }

   private static String validReason(String reason, boolean required) {
      String r = reason == null ? "" : reason.trim();
      if (required && r.length() < 5) {
         throw new ParkingDomainException("Indica el motivo (al menos 5 caracteres)");
      }
      if (r.length() > REASON_MAX) {
         throw new ParkingDomainException("El motivo admite hasta " + REASON_MAX + " caracteres");
      }
      return r.isEmpty() ? null : r;
   }

   private static LocalDateTime validDetectedAt(LocalDateTime detectedAt, LocalDateTime now) {
      if (detectedAt == null) {
         return now;
      }
      // Un minuto de margen por la diferencia de reloj entre el navegador y el servidor
      if (detectedAt.isAfter(now.plusMinutes(1))) {
         throw new ParkingDomainException("La fecha de la infracción no puede estar en el futuro");
      }
      if (detectedAt.isBefore(now.minusDays(30))) {
         throw new ParkingDomainException("Solo se registran infracciones de los últimos 30 días");
      }
      return detectedAt;
   }

   /** Valida las URL de evidencia (http/https, sin repetir) y las guarda una por línea */
   private static String joinEvidence(List<String> urls) {
      if (urls == null || urls.isEmpty()) {
         return null;
      }
      Set<String> clean = new LinkedHashSet<>();
      for (String raw : urls) {
         if (raw == null || raw.isBlank()) {
            continue;
         }
         String url = raw.trim();
         if (url.length() > EVIDENCE_URL_MAX || !isHttpUrl(url)) {
            throw new ParkingDomainException("Enlace de evidencia no válido (http o https, hasta "
                  + EVIDENCE_URL_MAX + " caracteres): " + abbreviate(url));
         }
         clean.add(url);
      }
      if (clean.size() > EVIDENCE_MAX_ITEMS) {
         throw new ParkingDomainException("Se admiten hasta " + EVIDENCE_MAX_ITEMS + " enlaces de evidencia");
      }
      return clean.isEmpty() ? null : String.join(EVIDENCE_SEPARATOR, new ArrayList<>(clean));
   }

   private static boolean isHttpUrl(String url) {
      try {
         URI uri = URI.create(url);
         String scheme = uri.getScheme();
         return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   private static String abbreviate(String s) {
      return s.length() <= 60 ? s : s.substring(0, 57) + "...";
   }

   // ========================= GETTERS Y SETTERS (para los mappers) =========================

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }
   public String getInfractionCode() { return infractionCode; }
   public void setInfractionCode(String infractionCode) { this.infractionCode = infractionCode; }
   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }
   public Long getZoneId() { return zoneId; }
   public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
   public Long getSpaceId() { return spaceId; }
   public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
   public Long getTransactionId() { return transactionId; }
   public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
   public Long getVehicleId() { return vehicleId; }
   public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
   public String getPlateNumber() { return plateNumber; }
   public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
   public Long getCustomerId() { return customerId; }
   public void setCustomerId(Long customerId) { this.customerId = customerId; }
   public String getInfractionType() { return infractionType; }
   public void setInfractionType(String infractionType) { this.infractionType = infractionType; }
   public String getSeverity() { return severity; }
   public void setSeverity(String severity) { this.severity = severity; }
   public LocalDateTime getDetectedAt() { return detectedAt; }
   public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
   public Long getDetectedBy() { return detectedBy; }
   public void setDetectedBy(Long detectedBy) { this.detectedBy = detectedBy; }
   public String getDetectedByName() { return detectedByName; }
   public void setDetectedByName(String detectedByName) { this.detectedByName = detectedByName; }
   public String getDetectionMethod() { return detectionMethod; }
   public void setDetectionMethod(String detectionMethod) { this.detectionMethod = detectionMethod; }
   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }
   public String getEvidence() { return evidence; }
   public void setEvidence(String evidence) { this.evidence = evidence; }
   public BigDecimal getFineAmount() { return fineAmount; }
   public void setFineAmount(BigDecimal fineAmount) { this.fineAmount = fineAmount; }
   public String getCurrency() { return currency; }
   public void setCurrency(String currency) { this.currency = currency; }
   public LocalDate getFineDueDate() { return fineDueDate; }
   public void setFineDueDate(LocalDate fineDueDate) { this.fineDueDate = fineDueDate; }
   public String getStatus() { return status; }
   public void setStatus(String status) { this.status = status; }
   public String getResolutionType() { return resolutionType; }
   public void setResolutionType(String resolutionType) { this.resolutionType = resolutionType; }
   public String getResolution() { return resolution; }
   public void setResolution(String resolution) { this.resolution = resolution; }
   public LocalDateTime getResolvedAt() { return resolvedAt; }
   public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
   public Long getResolvedBy() { return resolvedBy; }
   public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
   public Boolean getFinePaid() { return finePaid; }
   public void setFinePaid(Boolean finePaid) { this.finePaid = finePaid; }
   public LocalDateTime getFinePaidAt() { return finePaidAt; }
   public void setFinePaidAt(LocalDateTime finePaidAt) { this.finePaidAt = finePaidAt; }
   public BigDecimal getFinePaidAmount() { return finePaidAmount; }
   public void setFinePaidAmount(BigDecimal finePaidAmount) { this.finePaidAmount = finePaidAmount; }
   public String getFinePaymentReference() { return finePaymentReference; }
   public void setFinePaymentReference(String finePaymentReference) { this.finePaymentReference = finePaymentReference; }
   public String getNotes() { return notes; }
   public void setNotes(String notes) { this.notes = notes; }
   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
   public Long getCreatedBy() { return createdBy; }
   public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
   public Long getUpdatedBy() { return updatedBy; }
   public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

   // ========================= IDENTIDAD =========================

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Infraction that)) return false;
      return id != null && Objects.equals(id, that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "Infraction{id=" + id + ", code='" + infractionCode + "', type='" + infractionType
            + "', status='" + status + "', fine=" + fineAmount + '}';
   }
}
