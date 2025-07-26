import tkinter as tk
from tkinter import ttk, colorchooser, messagebox
import serial
import serial.tools.list_ports

# ──────────────────────────────────────────
# 串口连接
ser = None

def connect_serial():
    global ser
    port = port_var.get()
    try:
        ser = serial.Serial(port, 115200, timeout=1)
        log(f"已连接 {port}")
    except Exception as e:
        messagebox.showerror("错误", f"无法连接串口: {e}")

def disconnect_serial():
    global ser
    if ser and ser.is_open:
        ser.close()
        log("串口已断开")

def send_command(cmd):
    global ser
    if ser and ser.is_open:
        ser.write((cmd + "\n").encode())
        log(f"发送: {cmd}")
    else:
        messagebox.showwarning("提示", "请先连接串口")

# ──────────────────────────────────────────
# 发送模式命令
def clear_matrix():
    send_command(f"CLEAR\n")

def fill_color():
    color = colorchooser.askcolor()[0]  # 返回 (R,G,B)
    if color:
        r, g, b = map(int, color)
        send_command(f"FILL {r} {g} {b}\n")

def set_pixel():
    try:
        x = int(entry_x.get())
        y = int(entry_y.get())
        color = colorchooser.askcolor()[0]
        if color:
            r, g, b = map(int, color)
            send_command(f"PIX {x} {y} {r} {g} {b}\n")
    except ValueError:
        messagebox.showerror("错误", "请输入正确的坐标")

# ──────────────────────────────────────────
# 日志输出
def log(msg):
    text_log.insert(tk.END, msg + "\n")
    text_log.see(tk.END)

# ──────────────────────────────────────────
# GUI 界面
root = tk.Tk()
root.title("Arduino LED 控制面板")
root.geometry("450x400")

# 串口选择
frame_serial = ttk.LabelFrame(root, text="串口")
frame_serial.pack(fill="x", padx=10, pady=5)

ports = [port.device for port in serial.tools.list_ports.comports()]
port_var = tk.StringVar(value=ports[0] if ports else "")
ttk.Label(frame_serial, text="端口:").pack(side="left", padx=5)
ttk.Combobox(frame_serial, textvariable=port_var, values=ports, width=10).pack(side="left", padx=5)
ttk.Button(frame_serial, text="连接", command=connect_serial).pack(side="left", padx=5)
ttk.Button(frame_serial, text="断开", command=disconnect_serial).pack(side="left", padx=5)

# 模式选择
frame_mode = ttk.LabelFrame(root, text="控制")
frame_mode.pack(fill="x", padx=10, pady=5)

ttk.Button(frame_mode, text="清屏", command=clear_matrix).pack(side="left", padx=10, pady=10)
ttk.Button(frame_mode, text="填充颜色", command=fill_color).pack(side="left", padx=10, pady=10)

# 单点控制
frame_pixel = ttk.LabelFrame(root, text="单点控制")
frame_pixel.pack(fill="x", padx=10, pady=5)

ttk.Label(frame_pixel, text="X:").pack(side="left")
entry_x = ttk.Entry(frame_pixel, width=5)
entry_x.pack(side="left", padx=5)
ttk.Label(frame_pixel, text="Y:").pack(side="left")
entry_y = ttk.Entry(frame_pixel, width=5)
entry_y.pack(side="left", padx=5)
ttk.Button(frame_pixel, text="设置颜色", command=set_pixel).pack(side="left", padx=10)

# 日志窗口
frame_log = ttk.LabelFrame(root, text="日志")
frame_log.pack(fill="both", expand=True, padx=10, pady=5)
text_log = tk.Text(frame_log, height=10)
text_log.pack(fill="both", expand=True)

root.mainloop()
