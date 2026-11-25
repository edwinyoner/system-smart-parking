package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tipo de documento de identidad de un cliente (DNI, CE, RUC, pasaporte...).
 *
 * Identifica al cliente en la entrada y la salida y sirve para emitir el comprobante:
 * el número de documento se valida con las reglas del tipo (longitud y si es solo numérico)
 * y sunatCode es el código del Catálogo N.° 06 de SUNAT (1 = DNI, 4 = CE, 6 = RUC, 7 = pasaporte)
 * que pide la facturación electrónica.
 *
 * Reglas:
 * - El código es único, se guarda en mayúsculas y no cambia después de crearlo.
 * - 1 <= longitud mínima <= longitud máxima <= 20.
 * - Eliminar es lógico (deletedAt) y se puede restaurar.
 */
public class DocumentType {

   public static final int MAX_NUMBER_LENGTH = 20;
   private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,20}$");
   private static final Pattern SUNAT_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{1,2}$");
   private static final Pattern DIGITS = Pattern.compile("^\\d+$");
   private static final Pattern ALPHANUMERIC = Pattern.compile("^[A-Z0-9]+$");

   // ========================= CAMPOS DE IDENTIDAD =========================

   private Long id;
   private String code;                                   // Código único (ej: "DNI", "CE")
   private String name;                                   // Nombre completo
   private String description;                            // Descripción (opcional)
   private String sunatCode;                              // Catálogo 06 de SUNAT (opcional)

   // ========================= REGLAS DEL NÚMERO =========================

   private int minLength;                                 // Longitud mínima del número
   private int maxLength;                                 // Longitud máxima del número
   private boolean numericOnly;                           // true = solo dígitos

   // ========================= ESTADO Y AUDITORÍA =========================

   private boolean status;
   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;
   private LocalDateTime deletedAt;                       // Soft delete
   private Long deletedBy;

   /**
    * Reglas del número de documento. Un valor null toma el valor por defecto
    * (1 a 20 caracteres, letras y dígitos).
    */
   public record NumberRules(Integer minLength, Integer maxLength, Boolean numericOnly) {
      public static NumberRules defaults() {
         return new NumberRules(null, null, null);
      }
   }

   // ========================= CONSTRUCTORES =========================

   public DocumentType() {
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
      this.minLength = 1;
      this.maxLength = MAX_NUMBER_LENGTH;
   }

   public DocumentType(String code, String name) {
      this();
      this.code = code;
      this.name = name;
   }

   /** Crea un tipo de documento validado. El código se normaliza a mayúsculas */
   public static DocumentType create(String code, String name, String description, String sunatCode,
                                     NumberRules rules, boolean active, Long createdBy) {
      DocumentType type = new DocumentType(normalizeCode(code), requireText(name, "nombre"));
      type.description = blankToNull(description);
      type.sunatCode = normalizeSunatCode(sunatCode);
      type.applyRules(rules);
      type.status = active;
      type.createdBy = createdBy;
      type.updatedBy = createdBy;
      return type;
   }

   // ========================= MÉTODOS DE NEGOCIO =========================

   /** Actualiza los datos editables (el código no cambia) */
   public void update(String name, String description, String sunatCode, NumberRules rules) {
      this.name = requireText(name, "nombre");
      this.description = blankToNull(description);
      this.sunatCode = normalizeSunatCode(sunatCode);
      applyRules(rules);
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Normaliza un número de documento (sin espacios ni guiones, en mayúsculas) y comprueba
    * que cumpla las reglas del tipo.
    *
    * @return el número normalizado, listo para guardar o buscar
    * @throws ParkingDomainException si no cumple el formato
    */
   public String normalizeNumber(String number) {
      String value = number == null ? "" : number.trim().toUpperCase().replaceAll("[\\s-]", "");
      if (value.isEmpty()) {
         throw new ParkingDomainException("El número de " + code + " es obligatorio");
      }
      if (numericOnly && !DIGITS.matcher(value).matches()) {
         throw new ParkingDomainException("El " + code + " solo admite dígitos");
      }
      if (!numericOnly && !ALPHANUMERIC.matcher(value).matches()) {
         throw new ParkingDomainException("El " + code + " solo admite letras y dígitos");
      }
      if (value.length() < minLength || value.length() > maxLength) {
         throw new ParkingDomainException("El " + code + " debe tener " + describeLength());
      }
      return value;
   }

   /** "8 caracteres" o "entre 9 y 12 caracteres" */
   public String describeLength() {
      String unit = numericOnly ? "dígitos" : "caracteres";
      return minLength == maxLength
            ? minLength + " " + unit
            : "entre " + minLength + " y " + maxLength + " " + unit;
   }

   public void activate() {
      this.status = true;
      this.updatedAt = LocalDateTime.now();
   }

   public void deactivate() {
      this.status = false;
      this.updatedAt = LocalDateTime.now();
   }

   public boolean isActive() {
      return status;
   }

   /** Se puede usar en registros nuevos: activo y no eliminado */
   public boolean isUsable() {
      return status && !isDeleted();
   }

   public void markAsDeleted(Long deletedByUserId) {
      LocalDateTime now = LocalDateTime.now();
      this.deletedAt = now;
      this.deletedBy = deletedByUserId;
      this.updatedBy = deletedByUserId;
      this.status = false;
      this.updatedAt = now;
   }

   /** Sale de la papelera y vuelve activo */
   public void restore(Long restoredBy) {
      if (!isDeleted()) {
         throw new ParkingDomainException("El tipo de documento no está eliminado");
      }
      this.deletedAt = null;
      this.deletedBy = null;
      this.status = true;
      this.updatedBy = restoredBy;
      this.updatedAt = LocalDateTime.now();
   }

   public boolean isDeleted() {
      return deletedAt != null;
   }

   public void updateModifiedBy(Long userId) {
      this.updatedBy = userId;
      this.updatedAt = LocalDateTime.now();
   }

   public String getDisplayName() {
      return "[" + code + "] " + name;
   }

   // ========================= VALIDACIONES =========================

   private void applyRules(NumberRules rules) {
      NumberRules r = rules == null ? NumberRules.defaults() : rules;
      int min = r.minLength() == null ? 1 : r.minLength();
      int max = r.maxLength() == null ? MAX_NUMBER_LENGTH : r.maxLength();
      if (min < 1 || max > MAX_NUMBER_LENGTH || min > max) {
         throw new ParkingDomainException(
               "Longitud del número inválida: debe cumplir 1 <= mínima <= máxima <= " + MAX_NUMBER_LENGTH);
      }
      this.minLength = min;
      this.maxLength = max;
      this.numericOnly = Boolean.TRUE.equals(r.numericOnly());
   }

   private static String normalizeCode(String code) {
      String value = code == null ? "" : code.trim().toUpperCase();
      if (!CODE_PATTERN.matcher(value).matches()) {
         throw new ParkingDomainException(
               "El código debe tener de 2 a 20 caracteres: letras, dígitos o guion bajo");
      }
      return value;
   }

   private static String normalizeSunatCode(String sunatCode) {
      String value = blankToNull(sunatCode);
      if (value == null) {
         return null;
      }
      value = value.toUpperCase();
      if (!SUNAT_CODE_PATTERN.matcher(value).matches()) {
         throw new ParkingDomainException("El código SUNAT tiene 1 o 2 caracteres (ej: 1 = DNI, 6 = RUC)");
      }
      return value;
   }

   private static String requireText(String value, String field) {
      if (value == null || value.isBlank()) {
         throw new ParkingDomainException("El " + field + " es obligatorio");
      }
      return value.trim();
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   // ========================= GETTERS Y SETTERS =========================
   // No hay setStatus(): se usa activate() o deactivate()

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }

   public String getCode() { return code; }
   public void setCode(String code) { this.code = code; }

   public String getName() { return name; }
   public void setName(String name) { this.name = name; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public String getSunatCode() { return sunatCode; }
   public void setSunatCode(String sunatCode) { this.sunatCode = sunatCode; }

   public int getMinLength() { return minLength; }
   public void setMinLength(int minLength) { this.minLength = minLength; }

   public int getMaxLength() { return maxLength; }
   public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

   public boolean isNumericOnly() { return numericOnly; }
   public void setNumericOnly(boolean numericOnly) { this.numericOnly = numericOnly; }

   public boolean getStatus() { return status; }

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

   /** Igualdad por ID: dos tipos nuevos (sin ID) no son iguales entre sí */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DocumentType that = (DocumentType) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "DocumentType{id=" + id + ", code='" + code + "', name='" + name
            + "', status=" + (status ? "ACTIVO" : "INACTIVO") + '}';
   }
}
