package com.winnersystems.smartparking.payment.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ingresos de un rango de fechas. Cuentan los pagos cobrados (COMPLETED, PARTIALLY_REFUNDED y
 * REFUNDED) según el día en que se cobraron; lo devuelto se resta en ese mismo día.
 */
public final class IncomeReportDto {

   private IncomeReportDto() {
   }

   /**
    * @param gross    lo cobrado
    * @param refunded lo devuelto de esos pagos
    * @param net      cobrado menos devuelto
    * @param stays    neto por estadías
    * @param fines    neto por multas
    */
   public record Totals(int payments, BigDecimal gross, BigDecimal refunded, BigDecimal net,
                        BigDecimal stays, BigDecimal fines) {
   }

   public record DayIncome(LocalDate date, int payments, BigDecimal gross, BigDecimal refunded, BigDecimal net) {
   }

   public record TypeIncome(String code, String name, int payments, BigDecimal net) {
   }

   /** @param parkingId null en pagos antiguos sin parqueo copiado */
   public record ParkingIncome(Long parkingId, String parkingName, int payments, BigDecimal stays, BigDecimal fines,
                               BigDecimal net) {
   }

   /** @param byDay un elemento por cada día del rango, aunque no haya pagos */
   public record IncomeReport(LocalDate from, LocalDate to, String currency, Totals totals, List<DayIncome> byDay,
                              List<TypeIncome> byPaymentType, List<ParkingIncome> byParking) {
   }
}
