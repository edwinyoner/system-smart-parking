#include "display.h"

#include <ESP32-HUB75-MatrixPanel-I2S-DMA.h>
#include <SmartParkingCommon.h>

#include "config.h"

namespace display {

namespace {

constexpr int kWidth = PANEL_WIDTH * PANEL_CHAIN;
constexpr int kCharW = 6;   // fuente por defecto de Adafruit GFX: 5x7 + 1 de espacio
constexpr int kLineH = 8;

MatrixPanel_I2S_DMA* panel = nullptr;

// Qué hay en pantalla; tick() lo vuelve a dibujar en cada cuadro (por el desplazamiento)
enum class Screen { None, Message, Parking };
Screen screen = Screen::None;
String msgTitle, msgText;
ParkingView current;
bool currentWithAddress = false;
int currentPage = 0, currentPages = 0;

// Desplazamiento horizontal de las líneas largas (nombre y dirección)
int scroll = 0;
uint32_t lastStep = 0;
bool dirty = true;       // cambió el contenido: hay que redibujar
bool scrolling = false;  // alguna línea no cabe: se redibuja en cada paso del desplazamiento

uint16_t white() { return panel->color565(255, 255, 255); }
uint16_t gray() { return panel->color565(120, 120, 120); }
uint16_t green() { return panel->color565(0, 220, 60); }
uint16_t red() { return panel->color565(255, 40, 30); }
uint16_t amber() { return panel->color565(255, 160, 0); }

// Texto en una línea (0-3). Si no cabe, se desplaza en bucle con un espacio entre vueltas
void line(int row, const String& text, uint16_t color) {
  int y = row * kLineH;
  int width = text.length() * kCharW;
  panel->setTextColor(color);
  if (width <= kWidth) {
    panel->setCursor(0, y);
    panel->print(text);
    return;
  }
  scrolling = true;
  int loop = width + kCharW * 4;
  int x = -(scroll % loop);
  panel->setCursor(x, y);
  panel->print(text);
  panel->setCursor(x + loop, y);
  panel->print(text);
}

// "ETIQUETA ......... 12": etiqueta a la izquierda y número a la derecha
void labelValue(int row, const char* label, int value, uint16_t labelColor, uint16_t valueColor) {
  int y = row * kLineH;
  String v(value);
  panel->setTextColor(labelColor);
  panel->setCursor(0, y);
  panel->print(label);
  panel->setTextColor(valueColor);
  panel->setCursor(kWidth - v.length() * kCharW, y);
  panel->print(v);
}

// Aviso centrado en las líneas 1-3 ("CERRADO", "FUERA DE SERVICIO", "SIN CONEXION")
void notice(const String& text, uint16_t color) {
  // Si cabe en grande (12x16) se muestra en grande; si no, en tamaño normal
  int big = text.length() * kCharW * 2;
  panel->setTextColor(color);
  if (big <= kWidth) {
    panel->setTextSize(2);
    panel->setCursor((kWidth - big) / 2, kLineH + 4);
    panel->print(text);
    panel->setTextSize(1);
  } else {
    int w = text.length() * kCharW;
    panel->setCursor(w <= kWidth ? (kWidth - w) / 2 : 0, kLineH * 2);
    panel->print(text);
  }
}

void drawParking() {
  const ParkingView& p = current;
  line(0, p.name, white());
  bool attending = p.notice.length() == 0;
  if (!attending) {
    if (currentWithAddress && p.address.length() > 0) line(1, p.address, gray());
    notice(p.notice, amber());
    return;
  }
  uint16_t freeColor = p.available > 0 ? green() : red();
  if (currentWithAddress) {
    // Avenida: nombre, dirección, libres y ocupados en una línea, total y página
    line(1, p.address, gray());
    int y = 2 * kLineH;
    panel->setCursor(0, y);
    panel->setTextColor(freeColor);
    panel->print(p.available > 0 ? "DISP " + String(p.available) : String("LLENO"));
    String occ = "OCUP " + String(p.occupied);
    panel->setTextColor(red());
    panel->setCursor(kWidth - occ.length() * kCharW, y);
    panel->print(occ);
    labelValue(3, "TOTAL", p.total, white(), white());
    if (currentPages > 1) {
      String pg = String(currentPage) + "/" + String(currentPages);
      panel->setTextColor(gray());
      panel->setCursor((kWidth - pg.length() * kCharW) / 2, 3 * kLineH);
      panel->print(pg);
    }
    return;
  }
  // Junto al parqueo: nombre, total, disponibles y ocupados (de arriba hacia abajo)
  labelValue(1, "TOTAL", p.total, white(), white());
  if (p.available > 0) {
    labelValue(2, "DISPONIBLES", p.available, green(), green());
  } else {
    panel->setTextColor(red());
    panel->setCursor(0, 2 * kLineH);
    panel->print("DISPONIBLES");
    panel->setCursor(kWidth - 5 * kCharW, 2 * kLineH);
    panel->print("LLENO");
  }
  labelValue(3, "OCUPADOS", p.occupied, red(), red());
}

void redraw() {
  scrolling = false;
  dirty = false;
  panel->clearScreen();
  panel->setTextSize(1);
  panel->setTextWrap(false);
  if (screen == Screen::Message) {
    line(0, msgTitle, white());
    notice(msgText, amber());
  } else if (screen == Screen::Parking) {
    drawParking();
  }
  panel->flipDMABuffer();
}

}  // namespace

bool begin() {
  HUB75_I2S_CFG::i2s_pins pins = {HUB75_R1, HUB75_G1, HUB75_B1, HUB75_R2, HUB75_G2, HUB75_B2, HUB75_A,
                                  HUB75_B,  HUB75_C,  HUB75_D,  HUB75_E,  HUB75_LAT, HUB75_OE, HUB75_CLK};
  HUB75_I2S_CFG cfg(PANEL_WIDTH, PANEL_HEIGHT, PANEL_CHAIN, pins);
  cfg.double_buff = true;
  panel = new MatrixPanel_I2S_DMA(cfg);
  if (!panel->begin()) {
    SP_LOG("LED", "No se pudo iniciar el panel HUB75 (falta memoria o pines mal configurados)");
    return false;
  }
  panel->setBrightness8(PANEL_BRIGHTNESS);
  panel->clearScreen();
  panel->flipDMABuffer();
  SP_LOG("LED", "Panel %dx%d listo", kWidth, PANEL_HEIGHT);
  return true;
}

void showMessage(const String& title, const String& message) {
  String t = panelText(title);
  String m = panelText(message);
  if (screen == Screen::Message && t == msgTitle && m == msgText) return;
  screen = Screen::Message;
  msgTitle = t;
  msgText = m;
  scroll = 0;
  dirty = true;
}

bool sameParking(const ParkingView& a, const ParkingView& b) {
  return a.id == b.id && a.name == b.name && a.address == b.address && a.total == b.total &&
         a.available == b.available && a.occupied == b.occupied && a.state == b.state && a.notice == b.notice;
}

void showParking(const ParkingView& p, bool withAddress, int page, int pages) {
  if (screen == Screen::Parking && sameParking(current, p) && currentWithAddress == withAddress &&
      currentPage == page && currentPages == pages) {
    return;
  }
  // Otro parqueo: el texto largo empieza desde el principio; si solo cambió un número, sigue
  if (screen != Screen::Parking || current.id != p.id) scroll = 0;
  screen = Screen::Parking;
  current = p;
  currentWithAddress = withAddress;
  currentPage = page;
  currentPages = pages;
  dirty = true;
}

void tick() {
  if (!panel) return;
  bool step = scrolling && millis() - lastStep >= SCROLL_STEP_MS;
  if (step) {
    lastStep = millis();
    scroll++;
  }
  if (dirty || step) redraw();
}

}  // namespace display
