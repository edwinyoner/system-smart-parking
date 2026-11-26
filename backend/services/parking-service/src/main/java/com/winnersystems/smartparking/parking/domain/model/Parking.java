// domain/model/Parking.java
package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parqueo: una ubicación física (ej.: "Parqueo San Martín") que agrupa una o más zonas
 * (Zona Norte, Zona Sur) y, dentro de cada zona, sus espacios.
 *
 * Los turnos y las tarifas se configuran POR PARQUEO (ParkingShiftRate) y valen para
 * todas sus zonas.
 *
 * La capacidad (zonas, espacios, disponibles) NO se guarda aquí: se calcula contando
 * los espacios reales, para que nunca muestre espacios libres en un parqueo lleno.
 *
 * Reglas:
 * - El código es único (también frente a los eliminados), se guarda en mayúsculas y no cambia.
 * - La ubicación GPS es opcional, pero si se indica van latitud y longitud juntas.
 * - Solo un parqueo ACTIVE y no eliminado recibe vehículos (isOperational). Los que ya
 *   están dentro pueden salir aunque el parqueo pase a inactivo o mantenimiento.
 * - Eliminar es lógico y se puede restaurar.
 * - requireDriverDocument (apagado por defecto): si está encendido, la entrada pide el documento
 *   del conductor y la salida exige el mismo (anti-robo). Apagado, se entra y sale solo con la placa.
 */
public class Parking {

   // ========================= CONSTANTES =========================

   public static final String STATUS_ACTIVE = "ACTIVE";
   public static final String STATUS_INACTIVE = "INACTIVE";
   public static final String STATUS_MAINTENANCE = "MAINTENANCE";
   public static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";
   public static final Set<String> STATUSES =
         Set.of(STATUS_ACTIVE, STATUS_INACTIVE, STATUS_MAINTENANCE, STATUS_OUT_OF_SERVICE);

   public static final int MAX_NAME = 100;
   public static final int MAX_ADDRESS = 255;
   public static final int MAX_DESCRIPTION = 500;
   private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,19}$");

   // ========================= CAMPOS =========================

   private Long id;
   private String name;                        // "Parqueo San Martín"
   private String code;                        // "PSM"
   private String description;

   private String address;                     // Dirección (ej.: "Jr. San Martín 520, Huaraz")
   private Double latitude;                    // Coordenadas GPS del acceso
   private Double longitude;

   private Long managerId;                     // Usuario responsable (auth-service), opcional
   private String managerName;                 // Copia del nombre, tomada de auth-service

   private String status;

   private boolean requireDriverDocument;      // Anti-robo: exigir documento al entrar y el mismo al salir

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;
   private LocalDateTime deletedAt;
   private Long deletedBy;

   // ========================= CONSTRUCTORES =========================

   public Parking() {
      this.status = STATUS_ACTIVE;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   /** Crea un parqueo validado y activo. El código se normaliza a mayúsculas */
   public static Parking create(String name, String code, String description, String address,
                                Double latitude, Double longitude, Long createdBy) {
      Parking parking = new Parking();
      parking.code = normalizeCode(code);
      parking.applyInfo(name, description, address, latitude, longitude);
      parking.createdBy = createdBy;
      parking.updatedBy = createdBy;
      return parking;
   }

   // ========================= DATOS =========================

   /** Actualiza los datos editables (el código no cambia) */
   public void update(String name, String description, String address, Double latitude, Double longitude) {
      applyInfo(name, description, address, latitude, longitude);
      touch();
   }

   /**
    * Enciende o apaga la exigencia del documento del conductor. Solo afecta a las entradas
    * nuevas: quien entró sin documento sale por placa aunque luego se encienda.
    */
   public void changeDriverDocumentPolicy(boolean required) {
      this.requireDriverDocument = required;
      touch();
   }

   /**
    * Comprueba que una entrada nueva cumple la política de documento del parqueo.
    *
    * @param hasDriverDocument si la entrada trae documento del conductor
    * @throws ParkingDomainException si el parqueo lo exige y no se indicó
    */
   public void ensureEntryDocument(boolean hasDriverDocument) {
      if (requireDriverDocument && !hasDriverDocument) {
         throw new ParkingDomainException("Este parqueo exige el documento del conductor para entrar");
      }
   }

   /** Asigna el responsable. El nombre lo da auth-service, no el cliente */
   public void assignManager(Long managerId, String managerName) {
      if (managerId == null) {
         removeManager();
         return;
      }
      this.managerId = managerId;
      this.managerName = blankToNull(managerName);
      touch();
   }

   public void removeManager() {
      this.managerId = null;
      this.managerName = null;
      touch();
   }

   public boolean hasManager() {
      return managerId != null;
   }

   public boolean hasLocation() {
      return latitude != null && longitude != null;
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
               "Estado de parqueo inválido: " + newStatus + ". Usa ACTIVE, INACTIVE, MAINTENANCE u OUT_OF_SERVICE");
      }
   }

   public boolean isActive() {
      return STATUS_ACTIVE.equals(status);
   }

   public boolean isInactive() {
      return STATUS_INACTIVE.equals(status);
   }

   /** Recibe vehículos nuevos: activo y no eliminado */
   public boolean isOperational() {
      return isActive() && !isDeleted();
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

   /** Sale de la papelera y vuelve activo, con sus zonas, espacios, turnos y tarifas */
   public void restore(Long restoredBy) {
      if (!isDeleted()) {
         throw new ParkingDomainException("El parqueo no está eliminado");
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

   private void applyInfo(String name, String description, String address, Double latitude, Double longitude) {
      this.name = requireText(name, "El nombre", "obligatorio", MAX_NAME);
      this.address = requireText(address, "La dirección", "obligatoria", MAX_ADDRESS);
      String desc = blankToNull(description);
      if (desc != null && desc.length() > MAX_DESCRIPTION) {
         throw new ParkingDomainException("La descripción no puede exceder " + MAX_DESCRIPTION + " caracteres");
      }
      this.description = desc;
      applyLocation(latitude, longitude);
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

   private static String normalizeCode(String code) {
      String value = code == null ? "" : code.trim().toUpperCase();
      if (!CODE_PATTERN.matcher(value).matches()) {
         throw new ParkingDomainException(
               "El código debe tener de 2 a 20 caracteres: letras, dígitos, guion o guion bajo (ej.: PSM)");
      }
      return value;
   }

   /** @param label "El nombre" o "La dirección"; required "obligatorio" u "obligatoria" */
   private static String requireText(String value, String label, String required, int max) {
      if (value == null || value.isBlank()) {
         throw new ParkingDomainException(label + " es " + required);
      }
      String trimmed = value.trim();
      if (trimmed.length() > max) {
         throw new ParkingDomainException(label + " no puede exceder " + max + " caracteres");
      }
      return trimmed;
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

   public String getName() { return name; }
   public void setName(String name) { this.name = name; }

   public String getCode() { return code; }
   public void setCode(String code) { this.code = code; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public String getAddress() { return address; }
   public void setAddress(String address) { this.address = address; }

   public Double getLatitude() { return latitude; }
   public void setLatitude(Double latitude) { this.latitude = latitude; }

   public Double getLongitude() { return longitude; }
   public void setLongitude(Double longitude) { this.longitude = longitude; }

   public Long getManagerId() { return managerId; }
   public void setManagerId(Long managerId) { this.managerId = managerId; }

   public String getManagerName() { return managerName; }
   public void setManagerName(String managerName) { this.managerName = managerName; }

   public boolean isDriverDocumentRequired() { return requireDriverDocument; }
   public void setRequireDriverDocument(boolean requireDriverDocument) { this.requireDriverDocument = requireDriverDocument; }

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

   /** Igualdad por ID: dos parqueos nuevos (sin ID) no son iguales entre sí */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Parking that = (Parking) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "Parking{id=" + id + ", code='" + code + "', name='" + name + "', status=" + status + '}';
   }
}
