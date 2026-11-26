package com.winnersystems.smartparking.payment.domain.model;

/** Dónde se le ofrece al conductor un tipo de pago */
public enum PaymentChannel {
   /** Pantalla de la salida de un parqueo con puertas */
   EXIT_SCREEN,
   /** Kiosco de pago en la calle (ESP32 con pantalla táctil) */
   STREET_KIOSK,
   /** App móvil del conductor */
   MOBILE_APP,
   /** Un operador en el parqueo */
   OPERATOR
}
