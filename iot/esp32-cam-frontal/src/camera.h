// Cámara OV2640: inicio y captura de una foto JPEG reciente.
#pragma once

#include "esp_camera.h"

namespace cam {

// true si la cámara respondió. Necesita PSRAM (las fotos se guardan ahí)
bool begin();

// Foto JPEG tomada ahora (descarta la que estuviera en el búfer). Devolver con release()
camera_fb_t* capture();

void release(camera_fb_t* frame);

}  // namespace cam
