import serial
import threading
import sys

# 串口配置
PORT = "COM13"    # 改成你的Arduino串口号
BAUD = 115200

def read_from_port(ser):
    while True:
        try:
            data = ser.read(ser.in_waiting or 1)
            if data:
                print(data.decode('utf-8', errors='ignore'), end='', flush=True)
        except Exception as e:
            print(f"\n读取串口异常: {e}")
            break

def main():
    try:
        ser = serial.Serial(PORT, BAUD, timeout=0.1)
    except Exception as e:
        print(f"无法打开串口 {PORT}: {e}")
        return

    print(f"已连接 {PORT}，输入命令，回车发送。Ctrl+C退出")

    # 启动读取线程
    t = threading.Thread(target=read_from_port, args=(ser,), daemon=True)
    t.start()

    try:
        while True:
            line = input()
            if line == "":
                continue
            # 发送命令并加换行符
            ser.write((line + "\n").encode('utf-8'))
    except KeyboardInterrupt:
        print("\n退出程序")
    finally:
        ser.close()

if __name__ == "__main__":
    main()
