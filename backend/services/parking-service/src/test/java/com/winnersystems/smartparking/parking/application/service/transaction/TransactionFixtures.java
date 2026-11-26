package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.domain.model.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Datos de prueba compartidos por las pruebas de estadías.
 * Hoy es 2026-11-26 a las 10:00 (hora de Lima), dentro del turno MORNING 06:00-11:59.
 */
final class TransactionFixtures {

   static final ZoneId LIMA = ZoneId.of("America/Lima");
   static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);
   static final LocalDate TODAY = NOW.toLocalDate();
   static final Clock CLOCK = Clock.fixed(NOW.atZone(LIMA).toInstant(), LIMA);

   static final Long OPERATOR = 7L;

   private TransactionFixtures() {
   }

   static Parking parking(Long id) {
      Parking p = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      p.setId(id);
      return p;
   }

   static Zone zone(Long id, Long parkingId) {
      Zone z = Zone.create(parkingId, "Zona A", "A", null, null, null, null, null, 1L);
      z.setId(id);
      return z;
   }

   static Space space(Long id, Long zoneId) {
      Space s = Space.create(zoneId, "A-" + id, "PERPENDICULAR", null, null, null, null, false, 1L);
      s.setId(id);
      return s;
   }

   static Shift shift(Long id, String from, String to) {
      Shift s = Shift.create("Turno " + id, "T" + id, LocalTime.parse(from), LocalTime.parse(to), null, true, 1L);
      s.setId(id);
      return s;
   }

   /** S/ 2.50 por hora, 5 min de gracia y 5 de tolerancia */
   static Rate rate(Long id) {
      Rate r = Rate.create("Estándar", null, new BigDecimal("2.50"), new Rate.BillingRules(60, 0, 5, 5), true, 1L);
      r.setId(id);
      return r;
   }

   static Vehicle vehicle(Long id, String plate) {
      Vehicle v = new Vehicle(plate);
      v.setId(id);
      return v;
   }

   /** Estadía en curso registrada a mano */
   static Transaction activeStay(Long id, Long vehicleId, Long parkingId, Long zoneId, Long spaceId,
                                 Long rateId, LocalDateTime entry) {
      Transaction t = Transaction.registerEntry(new Transaction.EntryData(vehicleId, null, parkingId, zoneId,
            spaceId, rateId, null, null, Transaction.METHOD_MANUAL, OPERATOR, null, null, null), entry);
      t.setId(id);
      return t;
   }

   /** Usuario actual con permisos configurables (CurrentUserPort de pruebas) */
   static final class FakeUser implements CurrentUserPort {
      Long id = OPERATOR;
      final Set<String> authorities = new HashSet<>();

      @Override
      public Optional<Long> getCurrentUserId() {
         return Optional.ofNullable(id);
      }

      @Override
      public boolean hasAuthority(String authority) {
         return authorities.contains(authority);
      }
   }
}
