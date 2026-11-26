package com.winnersystems.smartparking.payment.domain.model;

import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tipo de pago: cómo paga el conductor (QR, efectivo) y quién lo confirma.
 *
 * Reglas:
 * - El código es único, se guarda en mayúsculas y no cambia después de crearlo.
 * - Se ofrece en al menos un canal.
 * - Un tipo MANUAL solo se ofrece con operador: la pantalla, el kiosco y la app no pueden
 *   confirmar que recibieron efectivo.
 * - El número de operación obligatorio solo aplica a los MANUAL; en los AUTOMATIC lo da la pasarela.
 * - Eliminar es lógico (deletedAt) y se puede restaurar.
 */
public class PaymentType {

   private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,20}$");

   private Long id;
   private String code;
   private String name;
   private String description;
   private ConfirmationMode confirmationMode;
   private boolean requiresReference;                      // El operador debe anotar el N.° de operación
   private Set<PaymentChannel> channels = EnumSet.noneOf(PaymentChannel.class);

   private boolean status;
   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;
   private LocalDateTime deletedAt;                        // Soft delete
   private Long deletedBy;

   public PaymentType() {
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
      this.confirmationMode = ConfirmationMode.MANUAL;
   }

   /** Crea un tipo de pago validado. El código se normaliza a mayúsculas */
   public static PaymentType create(String code, String name, String description, ConfirmationMode mode,
                                    boolean requiresReference, Collection<PaymentChannel> channels,
                                    boolean active, Long createdBy) {
      PaymentType type = new PaymentType();
      type.code = normalizeCode(code);
      type.applyData(name, description, mode, requiresReference, channels);
      type.status = active;
      type.createdBy = createdBy;
      type.updatedBy = createdBy;
      return type;
   }

   // ========================= MÉTODOS DE NEGOCIO =========================

   /** Actualiza los datos editables (el código no cambia) */
   public void update(String name, String description, ConfirmationMode mode,
                      boolean requiresReference, Collection<PaymentChannel> channels) {
      applyData(name, description, mode, requiresReference, channels);
      this.updatedAt = LocalDateTime.now();
   }

   /** Se ofrece en este canal y está disponible */
   public boolean isOfferedIn(PaymentChannel channel) {
      return isUsable() && channels.contains(channel);
   }

   public boolean isAutomatic() {
      return confirmationMode == ConfirmationMode.AUTOMATIC;
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
         throw new PaymentDomainException("El tipo de pago no está eliminado");
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

   // ========================= VALIDACIONES =========================

   private void applyData(String name, String description, ConfirmationMode mode,
                          boolean requiresReference, Collection<PaymentChannel> channels) {
      if (mode == null) {
         throw new PaymentDomainException("El modo de confirmación es obligatorio");
      }
      if (channels == null || channels.isEmpty()) {
         throw new PaymentDomainException("Elige al menos un canal donde se ofrece el tipo de pago");
      }
      Set<PaymentChannel> chosen = EnumSet.copyOf(channels);
      if (mode == ConfirmationMode.MANUAL && !chosen.equals(EnumSet.of(PaymentChannel.OPERATOR))) {
         throw new PaymentDomainException(
               "Un tipo de pago manual solo se ofrece con operador: la pantalla, el kiosco y la app no pueden confirmarlo");
      }
      this.name = requireText(name, "nombre");
      this.description = description == null || description.isBlank() ? null : description.trim();
      this.confirmationMode = mode;
      // En los automáticos el número de operación lo entrega la pasarela
      this.requiresReference = mode == ConfirmationMode.MANUAL && requiresReference;
      this.channels = chosen;
   }

   private static String normalizeCode(String code) {
      String value = code == null ? "" : code.trim().toUpperCase();
      if (!CODE_PATTERN.matcher(value).matches()) {
         throw new PaymentDomainException("El código debe tener de 2 a 20 caracteres: letras, dígitos o guion bajo");
      }
      return value;
   }

   private static String requireText(String value, String field) {
      if (value == null || value.isBlank()) {
         throw new PaymentDomainException("El " + field + " es obligatorio");
      }
      return value.trim();
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

   public ConfirmationMode getConfirmationMode() { return confirmationMode; }
   public void setConfirmationMode(ConfirmationMode confirmationMode) { this.confirmationMode = confirmationMode; }

   public boolean isRequiresReference() { return requiresReference; }
   public void setRequiresReference(boolean requiresReference) { this.requiresReference = requiresReference; }

   /** Copia de solo lectura */
   public Set<PaymentChannel> getChannels() { return Set.copyOf(channels); }
   public void setChannels(Collection<PaymentChannel> channels) {
      this.channels = channels == null || channels.isEmpty()
            ? EnumSet.noneOf(PaymentChannel.class)
            : EnumSet.copyOf(channels);
   }

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
      PaymentType that = (PaymentType) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "PaymentType{id=" + id + ", code='" + code + "', mode=" + confirmationMode
            + ", status=" + (status ? "ACTIVO" : "INACTIVO") + '}';
   }
}
