package com.winnersystems.smartparking.payment.domain.model;

/** Quién confirma que el dinero llegó */
public enum ConfirmationMode {
   /** Lo confirma un operador (por ejemplo, el efectivo) */
   MANUAL,
   /** Lo confirma la pasarela de pagos con un aviso (webhook); sin intervención de personas */
   AUTOMATIC
}
