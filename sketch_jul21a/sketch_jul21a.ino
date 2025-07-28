#include <Adafruit_NeoPixel.h>
#include <EEPROM.h>

#define WIDTH   8
#define HEIGHT  11

unsigned long lastSave = 0;
const unsigned long SAVE_INTERVAL = 5000; // 5秒防抖

// LED 引脚
const uint8_t stripPins[WIDTH] = {6, 7, 8, 9, 10, 11, 12, 13};
Adafruit_NeoPixel* strips[WIDTH];

String bleBuffer = "";
//亮度
int brightness = 0;

// 蓝牙模块控制引脚
#define BLE_STATE_PIN  2   // 蓝牙连接状态输入 (高电平=已连接)
#define BLE_KEY_PIN    3   // 唤醒/睡眠控制 (拉低>1秒睡眠，拉高1秒唤醒)
// 软件串口（蓝牙模块）
// #define BT_RX_PIN 18  // Arduino Mega TX1 (对应蓝牙RX)
// #define BT_TX_PIN 19  // Arduino Mega RX1 (对应蓝牙TX)
// //SoftwareSerial BTSerial(BT_RX_PIN, BT_TX_PIN);

// 缓存变量
bool receivingFrame = false;
bool framePCcom = false;
int  frameByteIndex = 0;
const int totalBytes = WIDTH * HEIGHT * 3;
uint8_t frameBuf[totalBytes];

void setup() {
    Serial.begin(115200);    // USB 串口
    Serial1.begin(115200);   // 蓝牙串口 TX1=18, RX1=19
    EEPROM.get(0, brightness);

    pinMode(BLE_STATE_PIN, INPUT);
    pinMode(BLE_KEY_PIN, OUTPUT);
    digitalWrite(BLE_KEY_PIN, HIGH);  // 初始唤醒状态

    for (int x = 0; x < WIDTH; x++) {
        strips[x] = new Adafruit_NeoPixel(HEIGHT, stripPins[x], NEO_GRB + NEO_KHZ800);
        strips[x]->begin();
        strips[x]->setBrightness(brightness);
        strips[x]->show();
    }
    clearMatrix();
    Serial.println("System Ready (PC + BLE Control)");
}

void loop() {
    handleSerial();
    handleBLE();
    checkBLEState();
}

// ─────────────────────────────────────────────
// 处理 PC 串口输入
void handleSerial() {
    static char cmdBuf[64];  // 缓冲区增大
    static uint8_t pos = 0;

    while (Serial.available()) {
        char c = Serial.read();

        if (receivingFrame) {
            frameBuf[frameByteIndex++] = c;
            if (frameByteIndex >= totalBytes) {
                applyFrame();
                receivingFrame = false;
            }
            // ⚠ 不再 while 读到底，直接 return，防止阻塞
            return;
        }

        if (c == '\n') {
            cmdBuf[pos] = '\0';
            String cmd = String(cmdBuf);
            cmd.trim();

            if (cmd.startsWith("BT:")) {
                cmd.remove(0, 3);
                Serial1.println(cmd);
                Serial.print("[发送到蓝牙] ");
                Serial.println(cmd);
            } else {
                processCommand(cmd.c_str());
            }
            pos = 0;
        } else if (pos < sizeof(cmdBuf) - 1) {
            cmdBuf[pos++] = c;
        }
    }
}


void handleBLE() {
    while (Serial1.available()) {
        char c = Serial1.read();
        if (c == '\r' || c == '\n') {
            if (bleBuffer.length() > 0) {
                Serial.print("[BLE] ");
                Serial.println(bleBuffer);
                if (Serial.available() == 0) {
                    if (bleBuffer.startsWith("FILL") || bleBuffer.startsWith("PIX") ||
                        bleBuffer == "CLEAR" || bleBuffer == "FRAME_BEGIN") {
                        processCommand(bleBuffer.c_str());
                    }
                }
                bleBuffer = "";
            }
        } else {
            bleBuffer += c;
        }
    }
}



// ─────────────────────────────────────────────
// 检测蓝牙连接状态
void checkBLEState() {
    static bool lastState = false;
    bool currentState = digitalRead(BLE_STATE_PIN);
    if (currentState != lastState) {
        if (!currentState) {
            Serial.println("BLE Connected");
        } else {
            Serial.println("BLE Disconnected, clear LED");
        }
        lastState = currentState;
    }
}

//设置亮度
void setBrightness(int b) {
    brightness = b;
    for (int x = 0; x < WIDTH; x++) {
        strips[x]->setBrightness(b);
        strips[x]->show();
    }
    Serial.print(b);
    Serial.println(" SetBrightness ok");
}

//保存亮度
void saveBrightness() {
    if (millis() - lastSave > SAVE_INTERVAL) {
        EEPROM.put(0, brightness);

        Serial.println("SaveBrightness ok");
        lastSave = millis();
    }
}
// ─────────────────────────────────────────────
// 唤醒蓝牙
void wakeBLE() {
    Serial.println("唤醒 BLE...");
    digitalWrite(BLE_KEY_PIN, HIGH);
    delay(1000);
}

// 进入睡眠
void sleepBLE() {
    Serial.println(" 睡眠 BLE...");
    digitalWrite(BLE_KEY_PIN, LOW);
    delay(1500);
}

// ─────────────────────────────────────────────
// 处理 LED 控制命令
void processCommand(const char *cmd) {
    if (strcmp(cmd, "FRAME_BEGIN") == 0) {
        receivingFrame = true;
        frameByteIndex = 0;
    }
    if (strncmp(cmd, "FILL", 4) == 0) {
        int r, g, b;
        if (sscanf(cmd, "FILL %d %d %d", &r, &g, &b) == 3) {
            fillMatrix(r, g, b);
        }
    } else if (strncmp(cmd, "PIX", 3) == 0) {
        int x, y, r, g, b;
        if (sscanf(cmd, "PIX %d %d %d %d %d", &x, &y, &r, &g, &b) == 5) {
            setPixel(x, y, r, g, b);
        }
    } else if (strcmp(cmd, "CLEAR") == 0) {
        clearMatrix();
    } else if (strcmp(cmd, "BLE_SLEEP") == 0) {
        sleepBLE();
    } else if (strcmp(cmd, "BLE_WAKE") == 0) {
        wakeBLE();
    } else if (strcmp(cmd, "BLE_CONFIG") == 0) {
        configBLE();
    } else if (strncmp(cmd, "BGN", 3) == 0) {
        int x;
        if (sscanf(cmd, "BGN %d", &x) == 1) {
            if (x < 0) x = 0;
            if (x > 255) x = 255;
            setBrightness(x);
        }
    } else if (strcmp(cmd, "SAVEBGN") == 0) {
        saveBrightness();
    }
}
//蓝牙配置模式
void configBLE(){
    Serial1.print("+++atk");
}

// ─────────────────────────────────────────────
// 处理二进制帧
void applyFrame() {
    int idx = 0;
    for (int x = 0; x < WIDTH; x++) {
        for (int y = 0; y < HEIGHT; y++) {
            uint8_t r = frameBuf[idx++];
            uint8_t g = frameBuf[idx++];
            uint8_t b = frameBuf[idx++];
            strips[x]->setPixelColor(y, strips[x]->Color(r, g, b));
        }
        strips[x]->show();
    }
}

// ─────────────────────────────────────────────
// 工具函数
void clearMatrix() {
    for (int x = 0; x < WIDTH; x++) {
        strips[x]->clear();
        strips[x]->show();
    }
}

void fillMatrix(int r, int g, int b) {
    for (int x = 0; x < WIDTH; x++) {
        for (int y = 0; y < HEIGHT; y++) {
            strips[x]->setPixelColor(y, strips[x]->Color(r, g, b));
        }
        strips[x]->show();
    }
}

void setPixel(int x, int y, int r, int g, int b) {
    if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return;
    strips[x]->setPixelColor(y, strips[x]->Color(r, g, b));
    strips[x]->show();
}
