// domain/model/Space.java
package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Espacio de estacionamiento para un vehículo, dentro de una zona.
 *
 * Reglas:
 * - El código es único dentro de su zona (también frente a los eliminados), se guarda
 *   en mayúsculas y no cambia. Dos zonas pueden tener un espacio "A-001".
 * - La zona no cambia después de crear el espacio.
 * - El estado de ocupación (AVAILABLE ↔ OCCUPIED) lo cambian la entrada y salida de
 *   vehículos, o la detección por cámara o sensor (occupy / release).
 * - A mano solo se pasa entre AVAILABLE, MAINTENANCE y OUT_OF_SERVICE, y nunca con un
 *   vehículo dentro: primero debe salir.
 * - Eliminar es lógico y no se permite con un vehículo dentro.
 * - "Tiene sensor" se deduce de sensorId.
 */
public class Space {

   // ========================= CONSTANTES =========================

   public static final String STATUS_AVAILABLE = "AVAILABLE";
   public static final String STATUS_OCCUPIED = "OCCUPIED";
   public static final String STATUS_MAINTENANCE = "MAINTENANCE";
   public static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";
   public static final Set<String> STATUSES =
         Set.of(STATUS_AVAILABLE, STATUS_OCCUPIED, STATUS_MAINTENANCE, STATUS_OUT_OF_SERVICE);

   public static final String TYPE_PARALLEL = "PARALLEL";
   public static final String TYPE_DIAGONAL = "DIAGONAL";
   public static final String TYPE_PERPENDICULAR = "PERPENDICULAR";
   public static final Set<String> TYPES = Set.of(TYPE_PARALLEL, TYPE_DIAGONAL, TYPE_PERPENDICULAR);

   public static final int MAX_DESCRIPTION = 255;
   /** Medidas en metros: un rango razonable para autos, motos y camionetas */
   public static final double MIN_DIMENSION = 0.5;
   public static final double MAX_DIMENSION = 20.0;
   private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");
   /** ID del sensor en iot-service (ej.: HCSR04-PSM-001) */
   private static final Pattern SENSOR_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,49}$");

   // ========================= CAMPOS =========================

   private Long id;
   private Long zoneId;
   private String type;                    // PARALLEL, DIAGONAL, PERPENDICULAR
   private String code;                    // "A-001"
   private String description;

   private Double width;                   // metros (opcional)
   private Double length;                  // metros (opcional)

   private String sensorId;                // HC-SR04 asignado (opcional)
   private Boolean hasCameraCoverage;      // lo ve una cámara aérea (escenario B)

   // Última lectura del sensor (null = el sensor nunca informó): lo que ve y cuándo lo vio
   private Boolean sensorOccupied;
   private LocalDateTime sensorReadAt;

   private String status;

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;
   private LocalDateTime deletedAt;
   private Long deletedBy;

   // ========================= CONSTRUCTORES =========================

   public Space() {
      this.status = STATUS_AVAILABLE;
      this.hasCameraCoverage = false;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   /** Crea un espacio validado y libre dentro de una zona */
   public static Space create(Long zoneId, String code, String type, String description,
                              Double width, Double length, String sensorId, boolean hasCameraCoverage,
                              Long createdBy) {
      if (zoneId == null) {
         throw new ParkingDomainException("El espacio debe pertenecer a una zona");
      }
      Space space = new Space();
      space.zoneId = zoneId;
      space.code = normalizeCode(code);
      space.applyInfo(type, description, width, length, sensorId, hasCameraCoverage);
      space.createdBy = createdBy;
      space.updatedBy = createdBy;
      return space;
   }

   /** Normaliza un código como lo guarda el dominio (para comparar antes de crear) */
   public static String normalizeCode(String code) {
      String value = code == null ? "" : code.trim().toUpperCase();
      if (!CODE_PATTERN.matcher(value).matches()) {
         throw new ParkingDomainException("Código de espacio inválido: " + code
               + ". Usa de 1 a 20 letras, dígitos, guion o guion bajo (ej.: A-001)");
      }
      return value;
   }

   // ========================= DATOS =========================

   /** Actualiza los datos editables (la zona y el código no cambian) */
   public void update(String type, String description, Double width, Double length,
                      String sensorId, boolean hasCameraCoverage) {
      applyInfo(type, description, width, length, sensorId, hasCameraCoverage);
      touch();
   }

   public Double getArea() {
      return width != null && length != null ? Math.round(width * length * 100.0) / 100.0 : null;
   }

   public String getTypeName() {
      return switch (type == null ? "" : type) {
         case TYPE_PARALLEL -> "Paralelo";
         case TYPE_DIAGONAL -> "Diagonal";
         case TYPE_PERPENDICULAR -> "Perpendicular";
         default -> "No especificado";
      };
   }

   public boolean hasSensor() {
      return sensorId != null;
   }

   public boolean hasCameraMonitoring() {
      return Boolean.TRUE.equals(hasCameraCoverage);
   }

   // ========================= OCUPACIÓN =========================

   /**
    * Un vehículo ocupa el espacio (entrada, cámara o sensor).
    *
    * @throws ParkingDomainException si no está libre
    */
   public void occupy() {
      if (!isAvailableForOccupation()) {
         throw new ParkingDomainException("El espacio " + code + " no está libre (estado: " + status + ")");
      }
      this.status = STATUS_OCCUPIED;
      touch();
   }

   /** El vehículo dejó el espacio (salida, cámara o sensor). Si ya estaba libre no cambia nada */
   public void release() {
      if (isOccupied()) {
         this.status = STATUS_AVAILABLE;
         touch();
      }
   }

   // ========================= SENSOR =========================

   /**
    * Guarda lo que ve el sensor HC-SR04. No cambia el estado del espacio: eso lo decide
    * SensorOccupancyService (puede mover una estadía o liberar el espacio).
    *
    * @throws ParkingDomainException si el espacio no tiene sensor
    */
   public void recordSensorReading(boolean occupied, LocalDateTime readAt) {
      if (!hasSensor()) {
         throw new ParkingDomainException("El espacio " + code + " no tiene sensor");
      }
      this.sensorOccupied = occupied;
      this.sensorReadAt = readAt;
   }

   /** El sensor ve un vehículo ahora mismo (false si está libre o nunca informó) */
   public boolean sensorDetectsVehicle() {
      return Boolean.TRUE.equals(sensorOccupied);
   }

   // ========================= ESTADO MANUAL =========================

   /**
    * Cambio de estado hecho por una persona: AVAILABLE, MAINTENANCE u OUT_OF_SERVICE.
    * OCCUPIED no se pone a mano, y un espacio ocupado no cambia hasta que el vehículo salga.
    */
   public void changeStatus(String newStatus) {
      String value = newStatus == null ? "" : newStatus.trim().toUpperCase();
      if (STATUS_OCCUPIED.equals(value)) {
         throw new ParkingDomainException(
               "Un espacio pasa a ocupado solo al registrar la entrada de un vehículo o al detectarlo");
      }
      if (!STATUSES.contains(value)) {
         throw new ParkingDomainException(
               "Estado de espacio inválido: " + newStatus + ". Usa AVAILABLE, MAINTENANCE u OUT_OF_SERVICE");
      }
      if (isOccupied() && !STATUS_OCCUPIED.equals(value)) {
         throw new ParkingDomainException("El espacio " + code
               + " tiene un vehículo dentro: registra su salida antes de cambiar el estado");
      }
      this.status = value;
      touch();
   }

   public void setInMaintenance() {
      changeStatus(STATUS_MAINTENANCE);
   }

   public void setOutOfService() {
      changeStatus(STATUS_OUT_OF_SERVICE);
   }

   public void enable() {
      changeStatus(STATUS_AVAILABLE);
   }

   public boolean isAvailable() {
      return STATUS_AVAILABLE.equals(status);
   }

   public boolean isOccupied() {
      return STATUS_OCCUPIED.equals(status);
   }

   public boolean isInMaintenance() {
      return STATUS_MAINTENANCE.equals(status);
   }

   public boolean isOutOfService() {
      return STATUS_OUT_OF_SERVICE.equals(status);
   }

   public boolean isAvailableForOccupation() {
      return isAvailable() && !isDeleted();
   }

   public boolean belongsTo(Long zoneId) {
      return this.zoneId != null && this.zoneId.equals(zoneId);
   }

   // ========================= PAPELERA =========================

   public void markAsDeleted(Long deletedByUserId) {
      if (isOccupied()) {
         throw new ParkingDomainException("El espacio " + code + " tiene un vehículo dentro: no se puede eliminar");
      }
      LocalDateTime now = LocalDateTime.now();
      this.deletedAt = now;
      this.deletedBy = deletedByUserId;
      this.updatedBy = deletedByUserId;
      this.status = STATUS_OUT_OF_SERVICE;
      this.updatedAt = now;
   }

   /** Sale de la papelera libre */
   public void restore(Long restoredBy) {
      if (!isDeleted()) {
         throw new ParkingDomainException("El espacio no está eliminado");
      }
      this.deletedAt = null;
      this.deletedBy = null;
      this.status = STATUS_AVAILABLE;
      this.updatedBy = restoredBy;
      touch();
   }

   public boolean isDeleted() {
      return deletedAt != null;
   }

   public void updateModifiedBy(Long userId) {
      this.updatedBy = userId;
      touch();
   }

   // ========================= VALIDACIONES =========================

   private void applyInfo(String type, String description, Double width, Double length,
                          String sensorId, boolean hasCameraCoverage) {
      String t = type == null ? "" : type.trim().toUpperCase();
      if (!TYPES.contains(t)) {
         throw new ParkingDomainException("El tipo de espacio debe ser PARALLEL, DIAGONAL o PERPENDICULAR");
      }
      String desc = blankToNull(description);
      if (desc != null && desc.length() > MAX_DESCRIPTION) {
         throw new ParkingDomainException("La descripción no puede exceder " + MAX_DESCRIPTION + " caracteres");
      }
      checkDimension(width, "ancho");
      checkDimension(length, "largo");
      String sensor = blankToNull(sensorId);
      if (sensor != null && !SENSOR_PATTERN.matcher(sensor).matches()) {
         throw new ParkingDomainException("ID de sensor inválido: " + sensor
               + ". Usa hasta 50 letras, dígitos, guion, guion bajo, punto o dos puntos");
      }
      this.type = t;
      this.description = desc;
      this.width = width;
      this.length = length;
      // Otro sensor (o ninguno): la lectura anterior ya no describe este espacio
      if (!java.util.Objects.equals(this.sensorId, sensor)) {
         this.sensorOccupied = null;
         this.sensorReadAt = null;
      }
      this.sensorId = sensor;
      this.hasCameraCoverage = hasCameraCoverage;
   }

   private static void checkDimension(Double value, String label) {
      if (value != null && (value < MIN_DIMENSION || value > MAX_DIMENSION)) {
         throw new ParkingDomainException("El " + label + " debe estar entre " + MIN_DIMENSION
               + " y " + MAX_DIMENSION + " metros");
      }
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   private void touch() {
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= GETTERS Y SETTERS =========================
   // Los setters los usa el mapper de persistencia; la lógica usa los métodos de negocio

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }

   public Long getZoneId() { return zoneId; }
   public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

   public String getType() { return type; }
   public void setType(String type) { this.type = type; }

   public String getCode() { return code; }
   public void setCode(String code) { this.code = code; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public Double getWidth() { return width; }
   public void setWidth(Double width) { this.width = width; }

   public Double getLength() { return length; }
   public void setLength(Double length) { this.length = length; }

   public String getSensorId() { return sensorId; }
   public void setSensorId(String sensorId) { this.sensorId = sensorId; }

   public Boolean getHasCameraCoverage() { return hasCameraCoverage; }
   public void setHasCameraCoverage(Boolean hasCameraCoverage) { this.hasCameraCoverage = hasCameraCoverage; }

   public Boolean getSensorOccupied() { return sensorOccupied; }
   public void setSensorOccupied(Boolean sensorOccupied) { this.sensorOccupied = sensorOccupied; }
   public LocalDateTime getSensorReadAt() { return sensorReadAt; }
   public void setSensorReadAt(LocalDateTime sensorReadAt) { this.sensorReadAt = sensorReadAt; }

   public String getStatus() { return status; }
   public void setStatus(String status) { this.status = status; }

   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

   public Long getCreatedBy() { return createdBy; }
   public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

   public Long getUpdatedBy() { return updatedBy; }
   public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

   public LocalDateTime getDeletedAt() { return deletedAt; }
   public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

   public Long getDeletedBy() { return deletedBy; }
   public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }

   // ========================= EQUALS, HASHCODE Y TOSTRING =========================

   /** Igualdad por ID: dos espacios nuevos (sin ID) no son iguales entre sí */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Space that = (Space) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "Space{id=" + id + ", zoneId=" + zoneId + ", code='" + code + "', status=" + status + '}';
   }
}
