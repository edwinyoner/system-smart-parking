package com.winnersystems.smartparking.payment.application.service.report;

import com.winnersystems.smartparking.payment.application.dto.query.IncomeReportDto.*;
import com.winnersystems.smartparking.payment.application.port.input.report.GetIncomeReportUseCase;
import com.winnersystems.smartparking.payment.application.port.output.PaymentPersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Ingresos agrupados por día, tipo de pago y parqueo, a partir de los pagos cobrados */
@Service
@Transactional(readOnly = true)
public class IncomeReportService implements GetIncomeReportUseCase {

   /** Rango máximo: un año */
   static final int MAX_DAYS = 366;

   private final PaymentPersistencePort paymentPort;
   private final Clock clock;

   public IncomeReportService(PaymentPersistencePort paymentPort, Clock clock) {
      this.paymentPort = paymentPort;
      this.clock = clock;
   }

   @Override
   public IncomeReport income(LocalDate from, LocalDate to, Long parkingId) {
      LocalDate today = LocalDate.now(clock);
      if (from == null) {
         from = today;
      }
      if (to == null) {
         to = from;
      }
      if (to.isBefore(from)) {
         throw new PaymentDomainException("La fecha final no puede ser anterior a la inicial");
      }
      if (from.isAfter(today)) {
         throw new PaymentDomainException("La fecha inicial no puede ser futura");
      }
      if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
         throw new PaymentDomainException("El rango máximo es de " + MAX_DAYS + " días");
      }

      List<Payment> payments = paymentPort.findPaidBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), parkingId);

      Acc total = new Acc();
      Map<LocalDate, Acc> byDay = new TreeMap<>();
      for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
         byDay.put(d, new Acc());
      }
      Map<String, Acc> byType = new LinkedHashMap<>();
      Map<String, String> typeNames = new HashMap<>();
      Map<Long, Acc> byParking = new LinkedHashMap<>();
      Map<Long, String> parkingNames = new HashMap<>();

      for (Payment p : payments) {
         Acc day = p.getPaidAt() == null ? null : byDay.get(p.getPaidAt().toLocalDate());
         if (day == null) {
            continue;   // fuera del rango pedido
         }
         total.add(p);
         day.add(p);
         byType.computeIfAbsent(p.getPaymentTypeCode(), c -> new Acc()).add(p);
         typeNames.putIfAbsent(p.getPaymentTypeCode(), p.getPaymentTypeName());
         byParking.computeIfAbsent(p.getParkingId(), id -> new Acc()).add(p);
         if (p.getParkingName() != null) {
            parkingNames.put(p.getParkingId(), p.getParkingName());
         }
      }

      List<DayIncome> days = byDay.entrySet().stream()
            .map(e -> new DayIncome(e.getKey(), e.getValue().count, e.getValue().gross, e.getValue().refunded,
                  e.getValue().net()))
            .toList();
      List<TypeIncome> types = byType.entrySet().stream()
            .map(e -> new TypeIncome(e.getKey(), typeNames.get(e.getKey()), e.getValue().count, e.getValue().net()))
            .sorted(Comparator.comparing(TypeIncome::net).reversed())
            .toList();
      List<ParkingIncome> parkings = byParking.entrySet().stream()
            .map(e -> new ParkingIncome(e.getKey(), parkingNames.get(e.getKey()), e.getValue().count,
                  e.getValue().stays, e.getValue().fines, e.getValue().net()))
            .sorted(Comparator.comparing(ParkingIncome::net).reversed())
            .toList();
      return new IncomeReport(from, to, Payment.CURRENCY,
            new Totals(total.count, total.gross, total.refunded, total.net(), total.stays, total.fines),
            days, types, parkings);
   }

   /** Acumulador de montos */
   private static final class Acc {
      int count;
      BigDecimal gross = BigDecimal.ZERO;
      BigDecimal refunded = BigDecimal.ZERO;
      BigDecimal stays = BigDecimal.ZERO;
      BigDecimal fines = BigDecimal.ZERO;

      void add(Payment p) {
         BigDecimal back = p.getRefundedAmount() == null ? BigDecimal.ZERO : p.getRefundedAmount();
         BigDecimal net = p.getAmount().subtract(back);
         count++;
         gross = gross.add(p.getAmount());
         refunded = refunded.add(back);
         if (p.getConcept() == PaymentConcept.FINE) {
            fines = fines.add(net);
         } else {
            stays = stays.add(net);
         }
      }

      BigDecimal net() {
         return gross.subtract(refunded);
      }
   }
}
