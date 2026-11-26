// domain/model/Zone.java
package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Zona de un parqueo (ej.: "Zona Norte" del Parqueo San Martín). Agrupa espacios.
 *
 * Reglas:
 * - Pertenece a un único parqueo y no se mueve a otro.
 * - El código es único dentro de su parqueo (también frente a las eliminadas), se
 *   guarda en mayúsculas y no cambia. Dos parqueos pueden tener una zona "NORTE".
 * - La capacidad (espacios totales, libres, ocupados) NO se guarda: se cuenta desde
 *   sus espacios reales.
 * - Los turnos y las tarifas son del parqueo, no de la zona.
 * - La dirección es opcional: si falta, vale la del parqueo.
 * - Solo una zona ACTIVE y no eliminada recibe vehículos.
 */
public class Zone {

   // ========================= CONSTANTES =========================

   public static final String STATUS_ACTIVE = "ACTIVE";
   public static final String STATUS_INACTIVE = "INACTIVE";
   public static final String STATUS_MAINTENANCE = "MAINTENANCE";
   public static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";
   public static final Set<String> STATUSES =
         Set.of(STATUS_ACTIVE, STATUS_INACTIVE, STATUS_MAINTENANCE, STATUS_OUT_OF_SERVICE);

   public static final int MAX_NAME = 100;
   public static final int MAX_ADDRESS = 200;
   public static final int MAX_DESCRIPTION = 500;
   public static final int MAX_CAMERAS = 10;
   private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");
   /** ID de cámara de iot-service (ej.: CAM-PSM-01) */
   private static final Pattern CAMERA_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,39}$");

   // ========================= CAMPOS =========================

   private Long id;
   private Long parkingId;
   private String name;                    // "Zona Norte"
   private String code;                    // "NORTE"
   private String address;
   private String description;

   private Double latitude;
   private Double longitude;

   private String cameraIds;               // IDs separados por coma (ej.: "CAM-01,CAM-02")

   private String status;

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;
   private LocalDateTime deletedAt;
   private Long deletedBy;

   // ========================= CONSTRUCTORES =========================

   public Zone() {
      this.status = STATUS_ACTIVE;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   /** Crea una zona validada y activa dentro de un parqueo */
   public static Zone create(Long parkingId, String name, String code, String address, String description,
                             Double latitude, Double longitude, List<String> cameraIds, Long createdBy) {
      if (parkingId == null) {
         throw new ParkingDomainException("La zona debe pertenecer a un parqueo");
      }
      Zone zone = new Zone();
      zone.parkingId = parkingId;
      zone.code = normalizeCode(code);
      zone.applyInfo(name, address, description, latitude, longitude, cameraIds);
      zone.createdBy = createdBy;
      zone.updatedBy = createdBy;
      return zone;
   }

   // ========================= DATOS =========================

   /** Actualiza los datos editables (el parqueo y el código no cambian) */
   public void update(String name, String address, String description,
                      Double latitude, Double longitude, List<String> cameraIds) {
      applyInfo(name, address, description, latitude, longitude, cameraIds);
      touch();
   }

   /** Nombre con código, para mostrar (ej.: "[NORTE] Zona Norte") */
   public String getFullName() {
      return "[" + code + "] " + name;
   }

   public boolean hasLocation() {
      return latitude != null && longitude != null;
   }

   // ========================= CÁMARAS =========================

   /** Lista de IDs de cámara (nunca null) */
   public List<String> getCameraIdsList() {
      if (cameraIds == null || cameraIds.isBlank()) {
         return new ArrayList<>();
      }
      return new ArrayList<>(Arrays.stream(cameraIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList());
   }

   public boolean hasCameraMonitoring() {
      return !getCameraIdsList().isEmpty();
   }

   public int getCameraCount() {
      return getCameraIdsList().size();
   }

   // ========================= ESTADO =========================

   public void activate() {
      this.status = STATUS_ACTIVE;
      touch();
   }

   public void deactivate() {
      this.status = STATUS_INACTIVE;
      touch();
   }

   public void setInMaintenance() {
      this.status = STATUS_MAINTENANCE;
      touch();
   }

   public void setOutOfService() {
      this.status = STATUS_OUT_OF_SERVICE;
      touch();
   }

   /** Cambia a cualquiera de los 4 estados; un estado desconocido es un error */
   public void changeStatus(String newStatus) {
      String value = newStatus == null ? "" : newStatus.trim().toUpperCase();
      switch (value) {
         case STATUS_ACTIVE -> activate();
         case STATUS_INACTIVE -> deactivate();
         case STATUS_MAINTENANCE -> setInMaintenance();
         case STATUS_OUT_OF_SERVICE -> setOutOfService();
         default -> throw new ParkingDomainException(
               "Estado de zona inválido: " + newStatus + ". Usa ACTIVE, INACTIVE, MAINTENANCE u OUT_OF_SERVICE");
      }
   }

   public boolean isActive() {
      return STATUS_ACTIVE.equals(status);
   }

   public boolean isInactive() {
      return STATUS_INACTIVE.equals(status);
   }

   public boolean isInMaintenance() {
      return STATUS_MAINTENANCE.equals(status);
   }

   public boolean isOutOfService() {
      return STATUS_OUT_OF_SERVICE.equals(status);
   }

   /** Recibe vehículos nuevos: activa y no eliminada (el parqueo se valida aparte) */
   public boolean isOperational() {
      return isActive() && !isDeleted();
   }

   public boolean belongsTo(Long parkingId) {
      return this.parkingId != null && this.parkingId.equals(parkingId);
   }

   // ========================= PAPELERA =========================

   public void markAsDeleted(Long deletedByUserId) {
      LocalDateTime now = LocalDateTime.now();
      this.deletedAt = now;
      this.deletedBy = deletedByUserId;
      this.updatedBy = deletedByUserId;
      this.status = STATUS_INACTIVE;
      this.updatedAt = now;
   }

   /** Sale de la papelera y vuelve activa, con sus espacios */
   public void restore(Long restoredBy) {
      if (!isDeleted()) {
         throw new ParkingDomainException("La zona no está eliminada");
      }
      this.deletedAt = null;
      this.deletedBy = null;
      this.status = STATUS_ACTIVE;
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

   private void applyInfo(String name, String address, String description,
                          Double latitude, Double longitude, List<String> cameraIdsList) {
      if (name == null || name.isBlank()) {
         throw new ParkingDomainException("El nombre de la zona es obligatorio");
      }
      String trimmedName = name.trim();
      if (trimmedName.length() > MAX_NAME) {
         throw new ParkingDomainException("El nombre no puede exceder " + MAX_NAME + " caracteres");
      }
      String addr = blankToNull(address);
      if (addr != null && addr.length() > MAX_ADDRESS) {
         throw new ParkingDomainException("La dirección no puede exceder " + MAX_ADDRESS + " caracteres");
      }
      String desc = blankToNull(description);
      if (desc != null && desc.length() > MAX_DESCRIPTION) {
         throw new ParkingDomainException("La descripción no puede exceder " + MAX_DESCRIPTION + " caracteres");
      }
      this.name = trimmedName;
      this.address = addr;
      this.description = desc;
      applyLocation(latitude, longitude);
      this.cameraIds = normalizeCameras(cameraIdsList);
   }

   private void applyLocation(Double latitude, Double longitude) {
      if (latitude == null && longitude == null) {
         this.latitude = null;
         this.longitude = null;
         return;
      }
      if (latitude == null || longitude == null) {
         throw new ParkingDomainException("Indica latitud y longitud juntas, o ninguna");
      }
      if (latitude < -90 || latitude > 90) {
         throw new ParkingDomainException("La latitud debe estar entre -90 y 90");
      }
      if (longitude < -180 || longitude > 180) {
         throw new ParkingDomainException("La longitud debe estar entre -180 y 180");
      }
      this.latitude = latitude;
      this.longitude = longitude;
   }

   /** Quita vacíos y repetidos; valida formato y cantidad. null si no hay cámaras */
   private static String normalizeCameras(List<String> ids) {
      if (ids == null) {
         return null;
      }
      Set<String> unique = new LinkedHashSet<>();
      for (String raw : ids) {
         if (raw == null || raw.isBlank()) {
            continue;
         }
         String id = raw.trim();
         if (!CAMERA_PATTERN.matcher(id).matches()) {
            throw new ParkingDomainException("ID de cámara inválido: " + id
                  + ". Usa hasta 40 letras, dígitos, guion, guion bajo, punto o dos puntos");
         }
         unique.add(id);
      }
      if (unique.size() > MAX_CAMERAS) {
         throw new ParkingDomainException("Una zona admite como máximo " + MAX_CAMERAS + " cámaras");
      }
      return unique.isEmpty() ? null : String.join(",", unique);
   }

   private static String normalizeCode(String code) {
      String value = code == null ? "" : code.trim().toUpperCase();
      if (!CODE_PATTERN.matcher(value).matches()) {
         throw new ParkingDomainException(
               "El código de la zona debe tener de 1 a 20 caracteres: letras, dígitos, guion o guion bajo (ej.: NORTE)");
      }
      return value;
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

   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }

   public String getName() { return name; }
   public void setName(String name) { this.name = name; }

   public String getCode() { return code; }
   public void setCode(String code) { this.code = code; }

   public String getAddress() { return address; }
   public void setAddress(String address) { this.address = address; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public Double getLatitude() { return latitude; }
   public void setLatitude(Double latitude) { this.latitude = latitude; }

   public Double getLongitude() { return longitude; }
   public void setLongitude(Double longitude) { this.longitude = longitude; }

   public String getCameraIds() { return cameraIds; }
   public void setCameraIds(String cameraIds) { this.cameraIds = cameraIds; }

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

   /** Igualdad por ID: dos zonas nuevas (sin ID) no son iguales entre sí */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Zone that = (Zone) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "Zone{id=" + id + ", parkingId=" + parkingId + ", code='" + code + "', name='" + name
            + "', status=" + status + '}';
   }
}
