import sys
import time
import threading
import numpy as np
import serial
import tkinter as tk
from tkinter import ttk
import pyaudio
import sounddevice as sd
import math
import signal

# ── 配置参数 ──
WIDTH = 8
HEIGHT = 11
PORT = 'COM13'      # Arduino 串口
BAUD = 115200
BLOCKSIZE = 1024
CHANNELS = 2
FPS = 60            # LED & GUI 更新频率

# 串口初始化
ser = serial.Serial(PORT, BAUD)
time.sleep(2)

# 全局状态
latest = np.zeros(WIDTH, dtype=int)
volume_db = 0.0
lock = threading.Lock()
gain = 1.0
paused = False
color_mode = "彩虹"  # "彩虹" / "单色" / "渐变"
running = True


# ── 自动选择音频设备 ──
audio_mode = None
selected_index = None
selected_rate = 44100

p = pyaudio.PyAudio()

threads = []

# 查找 Stereo Mix
for i in range(p.get_device_count()):
    info = p.get_device_info_by_index(i)
    name = info['name']
    if ('Stereo Mix' in name) or ('立体声混音' in name):
        selected_index = i
        selected_rate = int(info['defaultSampleRate'])
        audio_mode = "pyaudio"
        print(f"✔ 使用 Stereo Mix: {name} (index {i})")
        break

if selected_index is None:
    audio_mode = "sounddevice"
    print("⚠ 没找到 Stereo Mix，切换到 WASAPI Loopback 模式")

print(f"采样率: {selected_rate} Hz\n")

# ── 颜色函数 ──
def Wheel(pos):
    pos = 255 - pos
    if pos < 85:
        return (255 - pos * 3, 0, pos * 3)
    elif pos < 170:
        pos -= 85
        return (0, pos * 3, 255 - pos * 3)
    else:
        pos -= 170
        return (pos * 3, 255 - pos * 3, 0)

def get_color(x):
    if color_mode == "彩虹":
        return Wheel(int(255 * x / (WIDTH - 1)))
    elif color_mode == "单色":
        return (255, 255, 255)  # 绿色
    elif color_mode == "渐变":
        return (int(255 * x / (WIDTH - 1)), 0, 255 - int(255 * x / (WIDTH - 1)))

# ── 音频处理 ──
def process_audio(samples, samplerate):
    global gain, volume_db
    # 加窗
    samples = samples * np.hanning(len(samples))

    # 归一化（防止溢出）
    samples = samples / (np.max(np.abs(samples)) + 1e-6)

    # FFT
    fft_vals = np.abs(np.fft.rfft(samples)) * gain

    # **动态压缩**
    fft_vals = np.log1p(fft_vals)  # 对数压缩，防止高能量过爆

    freqs = np.fft.rfftfreq(len(samples), 1.0 / samplerate)
    edges = np.logspace(np.log10(20), np.log10(samplerate / 2), WIDTH + 1)

    levels = []
    for i in range(WIDTH):
        idxs = np.where((freqs >= edges[i]) & (freqs < edges[i + 1]))[0]
        energy = fft_vals[idxs].mean() if idxs.size else 0
        levels.append(energy)

    # 标准化
    mx = max(levels) or 1
    heights = [int(np.clip(e / mx * HEIGHT, 0, HEIGHT)) for e in levels]

    # RMS 转 dB（用于显示）
    rms = np.sqrt(np.mean(samples ** 2))
    volume_db = 20 * math.log10(rms + 1e-6)

    with lock:
        latest[:] = heights

# ── LED 发送线程 ──
def sender():
    interval = 1 / FPS
    while running:
        start = time.time()
        if not paused:
            with lock:
                lvl = latest.copy()

            ser.write(b"FRAME_BEGIN\n")
            buf = bytearray(WIDTH * HEIGHT * 3)
            idx = 0
            for x in range(WIDTH):
                h = max(0, min(HEIGHT, lvl[x]))
                for y in range(HEIGHT):
                    if y < h:
                        r, g, b = get_color(x)
                    else:
                        r = g = b = 0
                    buf[idx] = r
                    buf[idx + 1] = g
                    buf[idx + 2] = b
                    idx += 3
            ser.write(buf)

        elapsed = time.time() - start
        time.sleep(max(0.02, interval - elapsed))

# ── 音频线程 ──
def audio_thread():
    if audio_mode == "pyaudio":
        stream = p.open(format=pyaudio.paInt16,
                        channels=CHANNELS,
                        rate=selected_rate,
                        input=True,
                        input_device_index=selected_index,
                        frames_per_buffer=BLOCKSIZE)
        while running:
            data = np.frombuffer(stream.read(BLOCKSIZE, exception_on_overflow=False), dtype=np.int16)[::CHANNELS]
            process_audio(data, selected_rate)
            if ser.in_waiting > 0:              # 判断是否有数据
                line = ser.readline()           # 读取一行，遇到换行符结束
                print( "com:"+ line.decode(errors='ignore').strip())  # 解码并打印，去除空白字符
    else:
        device = sd.default.device[0]
        with sd.InputStream(samplerate=selected_rate, channels=2, device=device, blocksize=BLOCKSIZE, dtype='float32', latency='low', callback=lambda indata, frames, time_info, status: process_audio(indata[:, 0], selected_rate)):
            while running:
                time.sleep(0.1)

# ── GUI 线程 ──
def gui_thread():
    global gain, color_mode, paused
    
    def set_gain(val):
        global gain
        gain = val

    def set_color_mode(mode):
        global color_mode
        color_mode = mode

    def toggle_pause(btn):
        global paused
        paused = not paused
        btn.config(text="继续" if paused else "暂停")
    root = tk.Tk()
    root.title("LED 音频频谱控制器")
    canvas = tk.Canvas(root, width=WIDTH * 30, height=HEIGHT * 20, bg="black")
    canvas.pack()

    # 音量 dB 显示
    volume_label = ttk.Label(root, text="音量: 0 dB")
    volume_label.pack()

    # 灵敏度滑块
    ttk.Label(root, text="灵敏度 (0.001x - 5.0x)").pack()
    slider = ttk.Scale(root, from_=0.001, to=5.0, orient="horizontal", command=lambda v: set_gain(float(v)))
    slider.set(1.0)
    slider.pack()

    # 色彩模式选择
    ttk.Label(root, text="色彩模式").pack()
    mode_box = ttk.Combobox(root, values=["彩虹", "单色", "渐变"])
    mode_box.set("彩虹")
    mode_box.bind("<<ComboboxSelected>>", lambda e: set_color_mode(mode_box.get()))
    mode_box.pack()

    # 暂停按钮
    pause_btn = ttk.Button(root, text="暂停", command=lambda: toggle_pause(pause_btn))
    pause_btn.pack()

    def draw():
        canvas.delete("all")
        with lock:
            lvl = latest.copy()
        for x, h in enumerate(lvl):
            color = "#%02x%02x%02x" % get_color(x)
            for y in range(h):
                canvas.create_rectangle(x * 30, (HEIGHT - y - 1) * 20, x * 30 + 28, (HEIGHT - y) * 20, fill=color)
        volume_label.config(text=f"音量: {volume_db:.1f} dB")
        root.after(int(1000 / FPS), draw)

    draw()
    root.protocol("WM_DELETE_WINDOW", on_exit)
    root.mainloop()

# ── 程序退出处理 ──
def on_exit(*args):
    global running, threads
    print("\n正在退出...")
    running = False
    try:
        ser.write(b"CLEAR\n")
        ser.close()
    except:
        pass
    # 等待线程结束
    current = threading.current_thread()
    for t in threads:
        if t is not current:
            t.join(timeout=2)
    print("✅ LED 清屏，程序退出")
    sys.exit(0)

signal.signal(signal.SIGINT, on_exit)


# 启动线程
sender = threading.Thread(target=sender)
sender.start()
threads.append(sender)

audio = threading.Thread(target=audio_thread)
audio.start()
threads.append(audio)

gui = threading.Thread(target=gui_thread)
gui.start()
threads.append(gui)

print("✅ 系统已启动 (Ctrl+C 退出)")
while True:
    time.sleep(1)
