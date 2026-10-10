import serial
import time

# =========================
# CONFIGURATION
# =========================
PORT = "COM3"
BAUDRATE = 115200

# =========================
# CONNECT
# =========================
ser = serial.Serial(PORT, BAUDRATE, timeout=1)

time.sleep(2)

def send_command(command, wait=2):
    print(f"\n>>> {command}")

    ser.reset_input_buffer()

    ser.write((command + "\r\n").encode())

    time.sleep(wait)

    response = ser.read_all().decode(errors="ignore")

    print(response)

    return response


# Check A9G
send_command("AT")

# Enable GPS
send_command("AT+GPS=1", 3)

print("\nGPS enabled.")
print("Take the A9G outdoors with a clear view of the sky.")
print("Waiting for GPS fix...\n")

for attempt in range(60):

    print(f"Attempt {attempt + 1}/60")

    response = send_command("AT+LOCATION=2", 2)

    if "+LOCATION:" in response:

        if "GPS NOT FIX NOW" not in response:

            print("\n==============================")
            print("GPS FIX FOUND")
            print("==============================")
            print(response)
            break

        else:
            print("No satellite fix yet.")

    time.sleep(3)

else:
    print("\nGPS fix was not obtained within the timeout.")

# Turn GPS off
send_command("AT+GPS=0")

ser.close()