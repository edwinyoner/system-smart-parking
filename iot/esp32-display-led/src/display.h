// Dibujo en los paneles HUB75 (128x32): 4 líneas de 8 px con la fuente de 6x8 (21 caracteres).
// Usa doble búfer: se dibuja en uno mientras se muestra el otro, sin parpadeo.
#pragma once

#include <Arduino.h>

#include "panel_data.h"

namespace display {

bool begin();

// Pantalla de mensaje (arranque, sin conexión): título arriba y texto al centro
void showMessage(const String& title, const String& message);

// Un parqueo. page/pages: posición en la rotación del panel de avenida (pages 0 = sin indicador)
void showParking(const ParkingView& p, bool withAddress, int page, int pages);

// Llamar seguido: avanza el desplazamiento de los textos largos y presenta el cuadro
void tick();

}  // namespace display
