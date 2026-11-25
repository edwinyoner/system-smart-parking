package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Entidad de dominio que representa una TARIFA de estacionamiento.
 *
 * Una tarifa define el precio por hora y CÓMO se cobra el tiempo. Cada parqueo elige qué tarifa
 * aplica en cada turno mediante ParkingShiftRate. El precio que se usa es el vigente al salir.
 *
 * Reglas de cobro (todas configurables desde el panel):
 * - amount:             precio por hora en PEN (ej: 2.00)
 * - billingUnitMinutes: tamaño de la fracción de cobro. 60 = por hora o fracción,
 *                       30 = por media hora, 1 = al minuto. Cada fracción iniciada se cobra completa.
 * - minimumMinutes:     tiempo mínimo que se cobra (ej: fracciones de 15 min, pero mínimo 1 h)
 * - graceMinutes:       si sale ANTES de este tiempo no paga nada: entró por error (0 = siempre se cobra)
 * - toleranceMinutes:   cortesía antes de empezar a cobrar la fracción siguiente
 *                       (con 5: hasta 1 h 05 se cobra como 1 h)
 *
 * Valores por defecto (regla de Edwin para todas las tarifas): fracción de 60 min, sin mínimo,
 * 5 min sin costo y 5 min de tolerancia. Con S/ 2.00 la hora:
 * 4 min = gratis, 5 min = S/ 2.00, 1 h 05 = S/ 2.00, 1 h 06 = S/ 4.00, 2 h 05 = S/ 4.00.
 * Cargar o descargar no tiene trato especial: se cobra como cualquier estadía.
 */
public class Rate {

   /** Moneda única del sistema (regla de negocio: soles) */
   public static final String CURRENCY_PEN = "PEN";

   public static final int DEFAULT_BILLING_UNIT_MINUTES = 60;
   public static final int DEFAULT_GRACE_MINUTES = 5;
   public static final int DEFAULT_TOLERANCE_MINUTES = 5;
   public static final int MAX_BILLING_UNIT_MINUTES = 1440;     // un día
   public static final int MAX_MINIMUM_MINUTES = 1440;
   public static final int MAX_GRACE_MINUTES = 120;

   private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

   // ========================= CAMPOS DE IDENTIDAD =========================

   private Long id;
   private String name;                                   // Nombre (ej: "Auto - Día")
   private String description;                            // Descripción (opcional)

   // ========================= CAMPOS DE PRECIO Y COBRO =========================

   private BigDecimal amount;                             // Precio por hora
   private String currency;                               // Siempre PEN
   private int billingUnitMinutes;                        // Fracción de cobro en minutos
   private int minimumMinutes;                            // Tiempo mínimo cobrado
   private int graceMinutes;                              // Sale antes de esto: sin costo
   private int toleranceMinutes;                          // Cortesía entre fracciones

   // ========================= CAMPOS DE ESTADO =========================

   private boolean status;                                // true = activa, false = inactiva

   // ========================= CAMPOS DE AUDITORÍA =========================

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;
   private LocalDateTime deletedAt;                       // Soft delete
   private Long deletedBy;

   // ========================= CONSTRUCTORES =========================

   /**
    * Constructor vacío: lo usan los mappers al reconstruir desde la base de datos.
    * Valores por defecto: por hora o fracción, sin mínimo, 5 min sin costo y 5 min de tolerancia.
    */
   public Rate() {
      this.status = false;
      this.currency = CURRENCY_PEN;
      this.billingUnitMinutes = DEFAULT_BILLING_UNIT_MINUTES;
      this.graceMinutes = DEFAULT_GRACE_MINUTES;
      this.toleranceMinutes = DEFAULT_TOLERANCE_MINUTES;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   /**
    * Crea una tarifa nueva validando sus datos.
    *
    * @param active    si nace activa
    * @param createdBy usuario que la crea (auditoría)
    * @throws ParkingDomainException si algún dato no es válido
    */
   public static Rate create(String name, String description, BigDecimal amount, BillingRules rules,
                             boolean active, Long createdBy) {
      Rate rate = new Rate();
      rate.name = requireName(name);
      rate.description = blankToNull(description);
      rate.applyAmount(amount);
      rate.applyRules(rules);
      rate.status = active;
      rate.createdBy = createdBy;
      rate.updatedBy = createdBy;
      return rate;
   }

   /**
    * Reglas de cobro. null en un campo = valor por defecto.
    */
   public record BillingRules(Integer billingUnitMinutes, Integer minimumMinutes,
                              Integer graceMinutes, Integer toleranceMinutes) {
      public static BillingRules defaults() {
         return new BillingRules(null, null, null, null);
      }
   }

   // ========================= MÉTODOS DE NEGOCIO - COBRO =========================

   /**
    * Calcula el monto a cobrar por una estadía.
    *
    * 1. Si sale antes del tiempo sin costo (entró por error), es gratis.
    * 2. Se cobra al menos el tiempo mínimo.
    * 3. Se descuenta la tolerancia y se cuentan las fracciones iniciadas (redondeo hacia arriba),
    *    con un mínimo de una fracción.
    * 4. Monto = precio por hora × minutos de las fracciones / 60, redondeado a 2 decimales.
    *
    * @param durationMinutes minutos de estadía
    * @return monto en PEN (0.00 si es gratis)
    */
   public BigDecimal calculateCost(int durationMinutes) {
      int fractions = billableFractions(durationMinutes);
      if (fractions == 0 || amount == null) {
         return BigDecimal.ZERO.setScale(2);
      }
      return amount
            .multiply(BigDecimal.valueOf((long) fractions * billingUnitMinutes))
            .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
   }

   /**
    * Número de fracciones que se cobran por una estadía (0 si es gratis).
    */
   public int billableFractions(int durationMinutes) {
      if (durationMinutes < 0) {
         return 0;
      }
      if (durationMinutes < graceMinutes) {
         return 0;
      }
      int billable = Math.max(durationMinutes, minimumMinutes) - toleranceMinutes;
      return Math.max(1, Math.ceilDiv(billable, billingUnitMinutes));
   }

   /**
    * Precio de una fracción (ej: S/ 2.00 la hora con fracción de 15 min = S/ 0.50).
    */
   public BigDecimal getFractionAmount() {
      if (amount == null) {
         return BigDecimal.ZERO.setScale(2);
      }
      return amount.multiply(BigDecimal.valueOf(billingUnitMinutes))
            .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
   }

   /**
    * @return descripción legible (ej: "Auto - Día - PEN 2.00/hora")
    */
   public String getFullDescription() {
      return name + " - " + currency + " " + amount + "/hora";
   }

   // ========================= MÉTODOS DE NEGOCIO - GESTIÓN DE ESTADO =========================

   /**
    * Activa la tarifa: puede usarse en la configuración de los parqueos.
    */
   public void activate() {
      this.status = true;
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Desactiva la tarifa. No se ofrece en nuevas configuraciones.
    */
   public void deactivate() {
      this.status = false;
      this.updatedAt = LocalDateTime.now();
   }

   public boolean isActive() {
      return status;
   }

   public boolean isInactive() {
      return !status;
   }

   // ========================= MÉTODOS DE NEGOCIO - SOFT DELETE =========================

   /**
    * Marca la tarifa como eliminada (soft delete) y la desactiva.
    * El servicio verifica antes que ningún parqueo la use.
    */
   public void markAsDeleted(Long deletedByUserId) {
      LocalDateTime now = LocalDateTime.now();
      this.deletedAt = now;
      this.deletedBy = deletedByUserId;
      this.updatedBy = deletedByUserId;
      this.status = false;
      this.updatedAt = now;
   }

   /**
    * Restaura una tarifa eliminada (deshace la eliminación). Vuelve activa: solo se pudo
    * eliminar porque ningún parqueo la usaba.
    *
    * @throws ParkingDomainException si no está eliminada
    */
   public void restore(Long restoredBy) {
      if (!isDeleted()) {
         throw new ParkingDomainException("La tarifa no está eliminada");
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

   // ========================= MÉTODOS DE NEGOCIO - ACTUALIZACIÓN =========================

   /**
    * Actualiza nombre, descripción, precio y reglas de cobro.
    * Regla del negocio: el precio nuevo se aplica también a los vehículos que ya están dentro,
    * porque el monto se calcula al salir.
    */
   public void update(String name, String description, BigDecimal amount, BillingRules rules) {
      this.name = requireName(name);
      this.description = blankToNull(description);
      applyAmount(amount);
      applyRules(rules);
      this.updatedAt = LocalDateTime.now();
   }

   public void updateModifiedBy(Long userId) {
      this.updatedBy = userId;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= VALIDACIÓN =========================

   public boolean hasValidAmount() {
      return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
   }

   /**
    * Puede usarse si está activa, no eliminada y con precio.
    */
   public boolean isUsable() {
      return isActive() && !isDeleted() && hasValidAmount();
   }

   private void applyAmount(BigDecimal value) {
      if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
         throw new ParkingDomainException("El precio por hora debe ser mayor a 0");
      }
      if (value.stripTrailingZeros().scale() > 2) {
         throw new ParkingDomainException("El precio admite como máximo 2 decimales");
      }
      this.amount = value.setScale(2, RoundingMode.UNNECESSARY);
   }

   private void applyRules(BillingRules rules) {
      BillingRules r = rules == null ? BillingRules.defaults() : rules;
      int unit = orDefault(r.billingUnitMinutes(), DEFAULT_BILLING_UNIT_MINUTES);
      int minimum = orDefault(r.minimumMinutes(), 0);
      int grace = orDefault(r.graceMinutes(), DEFAULT_GRACE_MINUTES);
      // La tolerancia por defecto no puede igualar a una fracción muy corta (ej: al minuto)
      int tolerance = orDefault(r.toleranceMinutes(), Math.min(DEFAULT_TOLERANCE_MINUTES, unit - 1));

      if (unit < 1 || unit > MAX_BILLING_UNIT_MINUTES) {
         throw new ParkingDomainException("La fracción de cobro debe estar entre 1 y " + MAX_BILLING_UNIT_MINUTES + " minutos");
      }
      if (minimum < 0 || minimum > MAX_MINIMUM_MINUTES) {
         throw new ParkingDomainException("El tiempo mínimo debe estar entre 0 y " + MAX_MINIMUM_MINUTES + " minutos");
      }
      if (grace < 0 || grace > MAX_GRACE_MINUTES) {
         throw new ParkingDomainException("El tiempo de gracia debe estar entre 0 y " + MAX_GRACE_MINUTES + " minutos");
      }
      if (tolerance < 0 || tolerance >= unit) {
         throw new ParkingDomainException("La tolerancia debe ser menor que la fracción de cobro (" + unit + " min)");
      }
      this.billingUnitMinutes = unit;
      this.minimumMinutes = minimum;
      this.graceMinutes = grace;
      this.toleranceMinutes = tolerance;
   }

   private static int orDefault(Integer value, int defaultValue) {
      return value == null ? defaultValue : value;
   }

   private static String requireName(String value) {
      if (value == null || value.isBlank()) {
         throw new ParkingDomainException("El nombre de la tarifa es obligatorio");
      }
      return value.trim();
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   // ========================= GETTERS Y SETTERS =========================
   // Los setters los usan los mappers de persistencia para reconstruir el objeto.

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }

   public String getName() { return name; }
   public void setName(String name) { this.name = name; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public BigDecimal getAmount() { return amount; }
   public void setAmount(BigDecimal amount) { this.amount = amount; }

   public String getCurrency() { return currency; }
   public void setCurrency(String currency) { this.currency = currency; }

   public int getBillingUnitMinutes() { return billingUnitMinutes; }
   public void setBillingUnitMinutes(int billingUnitMinutes) { this.billingUnitMinutes = billingUnitMinutes; }

   public int getMinimumMinutes() { return minimumMinutes; }
   public void setMinimumMinutes(int minimumMinutes) { this.minimumMinutes = minimumMinutes; }

   public int getGraceMinutes() { return graceMinutes; }
   public void setGraceMinutes(int graceMinutes) { this.graceMinutes = graceMinutes; }

   public int getToleranceMinutes() { return toleranceMinutes; }
   public void setToleranceMinutes(int toleranceMinutes) { this.toleranceMinutes = toleranceMinutes; }

   public boolean getStatus() { return status; }
   // No hay setStatus() público: usar activate() o deactivate()

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

   /**
    * Dos tarifas son iguales si tienen el mismo ID. Sin ID (aún no guardada), solo es igual a sí misma.
    * Antes dos tarifas nuevas (ID null) se consideraban iguales.
    */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Rate other = (Rate) o;
      return id != null && id.equals(other.id);
   }

   @Override
   public int hashCode() {
      return id != null ? id.hashCode() : System.identityHashCode(this);
   }

   @Override
   public String toString() {
      return "Rate{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", amount=" + amount + " " + currency +
            ", billingUnitMinutes=" + billingUnitMinutes +
            ", minimumMinutes=" + minimumMinutes +
            ", graceMinutes=" + graceMinutes +
            ", toleranceMinutes=" + toleranceMinutes +
            ", status=" + (status ? "ACTIVA" : "INACTIVA") +
            '}';
   }
}
