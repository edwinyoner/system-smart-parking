#include "camera.h"

#include <Arduino.h>
#include <SmartParkingCommon.h>

#include "config.h"

namespace cam {

bool begin() {
  if (!psramFound()) {
    SP_LOG("CAM", "No hay PSRAM: revisa board_build.arduino.memory_type en platformio.ini");
    return false;
  }
  camera_config_t c = {};
  c.pin_pwdn = CAM_PIN_PWDN;
  c.pin_reset = CAM_PIN_RESET;
  c.pin_xclk = CAM_PIN_XCLK;
  c.pin_sccb_sda = CAM_PIN_SIOD;
  c.pin_sccb_scl = CAM_PIN_SIOC;
  c.pin_d7 = CAM_PIN_D7;
  c.pin_d6 = CAM_PIN_D6;
  c.pin_d5 = CAM_PIN_D5;
  c.pin_d4 = CAM_PIN_D4;
  c.pin_d3 = CAM_PIN_D3;
  c.pin_d2 = CAM_PIN_D2;
  c.pin_d1 = CAM_PIN_D1;
  c.pin_d0 = CAM_PIN_D0;
  c.pin_vsync = CAM_PIN_VSYNC;
  c.pin_href = CAM_PIN_HREF;
  c.pin_pclk = CAM_PIN_PCLK;
  c.xclk_freq_hz = 20000000;
  c.ledc_timer = LEDC_TIMER_0;
  c.ledc_channel = LEDC_CHANNEL_0;
  c.pixel_format = PIXFORMAT_JPEG;   // la cámara entrega el JPEG ya comprimido
  c.frame_size = CAM_FRAME_SIZE;
  c.jpeg_quality = CAM_JPEG_QUALITY;
  c.fb_count = 2;
  c.fb_location = CAMERA_FB_IN_PSRAM;
  c.grab_mode = CAMERA_GRAB_LATEST;  // siempre la imagen más reciente

  esp_err_t err = esp_camera_init(&c);
  if (err != ESP_OK) {
    SP_LOG("CAM", "No se pudo iniciar la camara (error 0x%x): revisa el cable plano", err);
    return false;
  }
  sensor_t* s = esp_camera_sensor_get();
  // Exposición y balance automáticos; un poco más de nitidez ayuda a leer la placa
  s->set_sharpness(s, 1);
  s->set_gain_ctrl(s, 1);
  s->set_exposure_ctrl(s, 1);
  s->set_whitebal(s, 1);
  SP_LOG("CAM", "Camara lista (sensor PID 0x%x)", s->id.PID);
  return true;
}

camera_fb_t* capture() {
  // La primera imagen puede ser de antes de que llegara el vehículo: se descarta
  camera_fb_t* old = esp_camera_fb_get();
  if (old != nullptr) esp_camera_fb_return(old);
  camera_fb_t* frame = esp_camera_fb_get();
  if (frame == nullptr) {
    SP_LOG("CAM", "No se pudo tomar la foto");
    return nullptr;
  }
  SP_LOG("CAM", "Foto %ux%u, %u KB", frame->width, frame->height, (unsigned)(frame->len / 1024));
  return frame;
}

void release(camera_fb_t* frame) {
  if (frame != nullptr) esp_camera_fb_return(frame);
}

}  // namespace cam
