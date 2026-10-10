import serial
import time

# =========================
# CONFIGURATION
# =========================
PORT = "COM3"
BAUDRATE = 115200

number = "+918019914152"
txt = "SafeBand test message"

# =========================
# A9G SMS FUNCTION
# =========================
def send_command(ser, command, delay=1):
    print(f">>> {command}")
    ser.write((command + "\r\n").encode())
    time.sleep(delay)

    response = ser.read_all().decode(errors="ignore")
    if response:
        print(response)

    return response


try:
    print(f"Connecting to A9G on {PORT}...")

    with serial.Serial(PORT, BAUDRATE, timeout=2) as ser:
        time.sleep(2)

        # Check module
        send_command(ser, "AT")

        # Set SMS text mode
        send_command(ser, "AT+CMGF=1")

        # Start SMS
        print(f'>>> Sending SMS to {number}')
        ser.write(f'AT+CMGS="{number}"\r\n'.encode())
        time.sleep(2)

        response = ser.read_all().decode(errors="ignore")
        print(response)

        # Check for SMS prompt
        if ">" not in response:
            print("ERROR: A9G did not give SMS prompt.")
            exit()

        # Send message text
        print(f">>> {txt}")
        ser.write(txt.encode())

        # Ctrl+Z / ASCII 26 = send SMS
        ser.write(bytes([26]))

        print(">>> Waiting for SMS result...")
        time.sleep(10)

        response = ser.read_all().decode(errors="ignore")
        print(response)

        if "+CMGS:" in response and "OK" in response:
            print("SMS SENT SUCCESSFULLY")
        elif "ERROR" in response:
            print("SMS FAILED")
        else:
            print("No definitive response received.")

except serial.SerialException as e:
    print(f"Serial error: {e}")

except Exception as e:
    print(f"Error: {e}")