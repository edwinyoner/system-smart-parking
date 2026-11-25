package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Entidad de dominio que representa un TURNO horario de operación.
 *
 * Un turno define un periodo del día con hora de inicio y de fin. Los turnos son globales:
 * cada parqueo elige cuáles usa y con qué tarifa mediante ParkingShiftRate. Si un horario no
 * está cubierto por ningún turno del parqueo, el parqueo no opera en ese horario.
 *
 * Convención de horario: el inicio y el fin se incluyen y se comparan al minuto.
 * - MAÑANA: 06:00 - 13:59  (13:59:30 sigue siendo Mañana; 14:00 ya es Tarde)
 * - TARDE:  14:00 - 19:59
 * - NOCHE:  20:00 - 05:59  (cruza la medianoche)
 *
 * Los turnos pueden solaparse entre sí (un parqueo usa "Día completo" y otro "Mañana" y
 * "Tarde"). El solapamiento solo importa dentro de un mismo parqueo y se valida en
 * ParkingShiftRate.
 */
public class Shift {

   /** Minutos de un día: para turnos que cruzan la medianoche */
   private static final int MINUTES_PER_DAY = 24 * 60;

   // ========================= CAMPOS DE IDENTIDAD =========================

   private Long id;
   private String name;                                   // Nombre (ej: "Mañana", "Tarde")
   private String code;                                   // Código único (ej: "MORNING")
   private String description;                            // Descripción detallada (opcional)

   // ========================= CAMPOS DE HORARIO =========================

   private LocalTime startTime;                           // Hora de inicio, incluida (ej: 06:00)
   private LocalTime endTime;                             // Hora de fin, incluida (ej: 13:59)

   // ========================= CAMPOS DE ESTADO =========================

   private boolean status;                                // true = activo, false = inactivo

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
    * Un turno nuevo empieza inactivo salvo que se active explícitamente.
    */
   public Shift() {
      this.status = false;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   /**
    * Constructor con los datos básicos. No valida: sirve también para reconstruir datos guardados.
    * Para crear un turno nuevo usar {@link #create}.
    */
   public Shift(String name, String code, LocalTime startTime, LocalTime endTime) {
      this();
      this.name = name;
      this.code = code;
      this.startTime = startTime;
      this.endTime = endTime;
   }

   public Shift(String name, String code, LocalTime startTime, LocalTime endTime, String description) {
      this(name, code, startTime, endTime);
      this.description = description;
   }

   /**
    * Crea un turno nuevo validando sus datos.
    *
    * @param active    si nace activo
    * @param createdBy usuario que lo crea (auditoría)
    * @throws ParkingDomainException si el nombre, el código o el horario no son válidos
    */
   public static Shift create(String name, String code, LocalTime startTime, LocalTime endTime,
                              String description, boolean active, Long createdBy) {
      Shift shift = new Shift(requireText(name, "nombre"), normalizeCode(code), null, null,
            blankToNull(description));
      shift.applySchedule(startTime, endTime);
      shift.status = active;
      shift.createdBy = createdBy;
      shift.updatedBy = createdBy;
      return shift;
   }

   // ========================= MÉTODOS DE NEGOCIO - INFORMACIÓN =========================

   /**
    * @return identificador formateado (ej: "[MORNING] Mañana")
    */
   public String getFullName() {
      return "[" + code + "] " + name;
   }

   /**
    * @return rango horario (ej: "06:00 - 13:59")
    */
   public String getTimeRange() {
      return startTime + " - " + endTime;
   }

   /**
    * Duración del turno en horas, contando el minuto final (06:00 - 13:59 son 8 h).
    * Maneja turnos que cruzan la medianoche (20:00 - 05:59 son 10 h).
    */
   public double getDurationHours() {
      if (!hasSchedule()) {
         return 0.0;
      }
      int start = minuteOfDay(startTime);
      int end = minuteOfDay(endTime);
      int minutes = Math.floorMod(end - start, MINUTES_PER_DAY) + 1;
      return minutes / 60.0;
   }

   // ========================= MÉTODOS DE NEGOCIO - VALIDACIÓN HORARIA =========================

   /**
    * Verifica si una hora está dentro de este turno.
    *
    * Se compara al minuto: 13:59:30 cuenta como 13:59. Así no queda ningún hueco entre un
    * turno que termina a las 13:59 y otro que empieza a las 14:00.
    *
    * @param time hora a verificar
    * @return true si la hora está dentro del turno
    */
   public boolean isWithinShift(LocalTime time) {
      if (time == null || !hasSchedule()) {
         return false;
      }
      LocalTime t = time.truncatedTo(ChronoUnit.MINUTES);
      LocalTime start = startTime.truncatedTo(ChronoUnit.MINUTES);
      LocalTime end = endTime.truncatedTo(ChronoUnit.MINUTES);

      if (!crossesMidnight()) {
         // Ej: 06:00 - 13:59
         return !t.isBefore(start) && !t.isAfter(end);
      }
      // Ej: 20:00 - 05:59: desde el inicio hasta medianoche, o desde medianoche hasta el fin
      return !t.isBefore(start) || !t.isAfter(end);
   }

   /**
    * @return true si el fin es anterior al inicio (ej: 20:00 - 05:59)
    */
   public boolean crossesMidnight() {
      return hasSchedule() && endTime.isBefore(startTime);
   }

   // ========================= MÉTODOS DE NEGOCIO - GESTIÓN DE ESTADO =========================

   /**
    * Activa el turno: puede usarse en la configuración de tarifas de los parqueos.
    */
   public void activate() {
      this.status = true;
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Desactiva el turno. Los parqueos que lo usan dejan de operar en ese horario
    * hasta que se active de nuevo. Sus tarifas no se eliminan.
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
    * Marca el turno como eliminado (soft delete) y lo desactiva.
    * El servicio verifica antes que ningún parqueo lo use.
    *
    * @param deletedByUserId usuario que elimina (auditoría)
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
    * Restaura un turno eliminado. Queda inactivo: debe activarse manualmente.
    */
   public void restore() {
      this.deletedAt = null;
      this.deletedBy = null;
      this.status = false;
      this.updatedAt = LocalDateTime.now();
   }

   public boolean isDeleted() {
      return deletedAt != null;
   }

   // ========================= MÉTODOS DE NEGOCIO - ACTUALIZACIÓN DE DATOS =========================

   /**
    * Actualiza el nombre y la descripción. El código no cambia: lo usan las tarifas.
    */
   public void updateBasicInfo(String name, String description) {
      this.name = requireText(name, "nombre");
      this.description = blankToNull(description);
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Actualiza el horario validándolo.
    *
    * @throws ParkingDomainException si falta una hora o el inicio es igual al fin
    */
   public void updateSchedule(LocalTime startTime, LocalTime endTime) {
      applySchedule(startTime, endTime);
      this.updatedAt = LocalDateTime.now();
   }

   /**
    * Registra quién modificó el turno (auditoría).
    */
   public void updateModifiedBy(Long userId) {
      this.updatedBy = userId;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= MÉTODOS DE VALIDACIÓN =========================

   public boolean hasSchedule() {
      return startTime != null && endTime != null;
   }

   /**
    * Puede usarse si está activo, no eliminado y con horario.
    */
   public boolean isUsable() {
      return isActive() && !isDeleted() && hasSchedule();
   }

   private void applySchedule(LocalTime start, LocalTime end) {
      if (start == null || end == null) {
         throw new ParkingDomainException("La hora de inicio y la de fin son obligatorias");
      }
      LocalTime startMinute = start.truncatedTo(ChronoUnit.MINUTES);
      LocalTime endMinute = end.truncatedTo(ChronoUnit.MINUTES);
      if (startMinute.equals(endMinute)) {
         // Con inicio y fin iguales no se sabe si dura un minuto o el día entero
         throw new ParkingDomainException(
               "La hora de inicio y la de fin no pueden ser iguales. Para todo el día usa 00:00 - 23:59");
      }
      this.startTime = startMinute;
      this.endTime = endMinute;
   }

   private static int minuteOfDay(LocalTime time) {
      return time.getHour() * 60 + time.getMinute();
   }

   private static String requireText(String value, String field) {
      if (value == null || value.isBlank()) {
         throw new ParkingDomainException("El " + field + " del turno es obligatorio");
      }
      return value.trim();
   }

   /** Código en mayúsculas y sin espacios alrededor (ej: " morning " → "MORNING") */
   private static String normalizeCode(String code) {
      return requireText(code, "código").toUpperCase();
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   // ========================= GETTERS Y SETTERS =========================

   public Long getId() {
      return id;
   }

   public void setId(Long id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getCode() {
      return code;
   }

   public void setCode(String code) {
      this.code = code;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public LocalTime getStartTime() {
      return startTime;
   }

   public void setStartTime(LocalTime startTime) {
      this.startTime = startTime;
   }

   public LocalTime getEndTime() {
      return endTime;
   }

   public void setEndTime(LocalTime endTime) {
      this.endTime = endTime;
   }

   public boolean getStatus() {
      return status;
   }

   // No hay setStatus() público: usar activate() o deactivate()

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

   public LocalDateTime getDeletedAt() {
      return deletedAt;
   }

   public void setDeletedAt(LocalDateTime deletedAt) {
      this.deletedAt = deletedAt;
   }

   public Long getDeletedBy() {
      return deletedBy;
   }

   public void setDeletedBy(Long deletedBy) {
      this.deletedBy = deletedBy;
   }

   // ========================= EQUALS, HASHCODE Y TOSTRING =========================

   /**
    * Dos turnos son iguales si tienen el mismo ID. Sin ID (aún no guardado), solo es igual a sí mismo.
    * Así equals y hashCode son coherentes (antes comparaba por ID o por código).
    */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Shift other = (Shift) o;
      return id != null && id.equals(other.id);
   }

   @Override
   public int hashCode() {
      return id != null ? id.hashCode() : System.identityHashCode(this);
   }

   @Override
   public String toString() {
      return "Shift{" +
            "id=" + id +
            ", code='" + code + '\'' +
            ", name='" + name + '\'' +
            ", timeRange='" + getTimeRange() + '\'' +
            ", status=" + (status ? "ACTIVO" : "INACTIVO") +
            ", durationHours=" + String.format("%.1f", getDurationHours()) +
            ", crossesMidnight=" + crossesMidnight() +
            '}';
   }
}
