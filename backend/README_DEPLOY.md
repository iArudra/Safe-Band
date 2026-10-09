# SafeWatch Backend — AWS EC2 Windows Server Deployment Guide

> **Read this before doing anything**: Every step that creates AWS resources,
> incurs costs, or opens ports is marked **[BILLABLE]** or **[ASK FIRST]**.
> Do not proceed past any such step without your explicit approval.

---

## Architecture Overview

```
A9G (COM3, USB) -> a9g_relay.py (Windows PC) -> HTTPS POST
                                                     |
                                     AWS EC2 Windows Server
                                     Flask + Waitress (port 5000)
                                     | Firebase Admin SDK
                             Firebase RTDB (Asia SE1)
                                     |
                             SafeWatch Android App
```

---

## Pre-requisites (local, no cost)

- [ ] AWS account with billing alerts configured
- [ ] Firebase service-account JSON downloaded
- [ ] `DEVICE_SECRET` generated:
  ```powershell
  python -c "import secrets; print(secrets.token_hex(32))"
  ```
- [ ] `.env` file created from `.env.example`

---

## Stage 1 - Local Testing (No AWS Cost)

### 1.1 Create and activate virtual environment

```powershell
cd C:\Users\User\SafeBand\Safe-Band\backend
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 1.2 Create your .env file

```powershell
Copy-Item .env.example .env
notepad .env   # Fill in DEVICE_SECRET and FIREBASE_CREDENTIALS_PATH
```

### 1.3 Run automated tests (no hardware, no Firebase needed)

```powershell
pytest tests/ -v
```

Expected: **all tests pass**.

### 1.4 Test GPS reading (A9G connected to COM3)

```powershell
$env:OFFLINE_MODE="true"
python a9g_relay.py
```

The script will:
- Open COM3
- Send `AT` - verify "OK" appears
- Enable GPS
- Poll every 30 seconds
- Print coordinates when a fix is acquired
- **Not** POST to any server (OFFLINE_MODE=true)

> Move the A9G near a window or outdoors for first fix. Cold-start can take 2-10 minutes.

### 1.5 Test the Flask API locally

```powershell
# Terminal 1 - start server
python app.py

# Terminal 2 - test health endpoint
Invoke-WebRequest http://localhost:5000/health | ConvertFrom-Json

# Terminal 3 - test location endpoint
$headers = @{ "X-Device-Token" = "YOUR_DEVICE_SECRET"; "Content-Type" = "application/json" }
$body = '{"device_id":"band_001","lat":12.9716,"lng":77.5946}'
Invoke-RestMethod -Uri http://localhost:5000/api/location -Method Post -Headers $headers -Body $body
```

---

## Stage 2 - AWS EC2 Setup [BILLABLE - Ask First]

### 2.1 Choose instance type

| Tier | Instance | Monthly cost (approx) | Use when |
|------|----------|-----------------------|----------|
| Free tier | t2.micro | $0 for 12 months | Development / testing |
| Light prod | t3.small | ~$15/month | Single-device tracking |
| Medium prod | t3.medium | ~$30/month | Multiple devices |

**Recommendation for your use case:** `t2.micro` (Free Tier) while testing.

### 2.2 Create the EC2 instance [BILLABLE]

1. Sign in to AWS Console - EC2
2. Click **Launch instance**
3. **Name**: `safewatch-backend`
4. **AMI**: Windows Server 2022 Base (64-bit)
5. **Instance type**: `t2.micro`
6. **Key pair**: Create new - download `.pem` file - **store safely**
7. **Network settings**: Create security group (configure below)
8. **Storage**: 30 GB gp3 (Free Tier includes 30 GB)
9. Click **Launch instance**

### 2.3 Configure Security Group

In the EC2 console, edit inbound rules:

| Type | Protocol | Port | Source | Purpose |
|------|----------|------|--------|---------|
| RDP | TCP | 3389 | My IP | Remote Desktop access |
| Custom TCP | TCP | 5000 | 0.0.0.0/0 | API access (use HTTPS ALB later) |
| HTTPS | TCP | 443 | 0.0.0.0/0 | After adding HTTPS |

> After adding HTTPS via ALB, remove the direct port 5000 rule so the API is
> only reachable through the load balancer.

### 2.4 Allocate an Elastic IP [BILLABLE ~$3.65/month if not attached]

1. EC2 > Elastic IPs > Allocate
2. Associate with your instance
3. Your server will now have a **fixed public IP** that does not change on restart.

---

## Stage 3 - Connect and Configure the Server

### 3.1 Connect via Remote Desktop

1. EC2 Console > Instances > select instance > **Connect**
2. Click **RDP client** tab
3. Click **Get password** > upload your `.pem` key > decrypt password
4. Open **Remote Desktop Connection** (mstsc.exe)
5. Enter: Public IP or DNS, username: `Administrator`, password: decrypted above

### 3.2 Install Python on the EC2 instance

Inside the RDP session:

```powershell
# Download Python 3.12 (stable, tested)
Invoke-WebRequest -Uri "https://www.python.org/ftp/python/3.12.7/python-3.12.7-amd64.exe" -OutFile "python_installer.exe"

# Install silently, add to PATH
.\python_installer.exe /quiet InstallAllUsers=1 PrependPath=1

# Verify
python --version
pip --version
```

Close and reopen PowerShell after installation so PATH is updated.

### 3.3 Copy your project to the EC2 instance

**Option A - Git (recommended):**
```powershell
# On EC2:
git clone https://github.com/YOUR_USERNAME/Safe-Band.git
cd Safe-Band\backend
```

**Option B - Copy via RDP drag-and-drop:**
Drag the `backend` folder from your local machine into the RDP window.

### 3.4 Set up virtual environment

```powershell
cd C:\Safe-Band\backend
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 3.5 Upload Firebase service-account JSON securely

**Do NOT copy the JSON into the project folder.**

```powershell
# Create a secure folder outside the project
New-Item -ItemType Directory -Path "C:\secrets"
# Copy your serviceAccount.json into C:\secrets\
# (Use RDP drag-and-drop or a secure transfer method)
```

> Never put the JSON in the Git repository. It is already excluded by `.gitignore`.

### 3.6 Set environment variables

```powershell
# Set permanently via PowerShell (requires admin):
[System.Environment]::SetEnvironmentVariable("DEVICE_SECRET", "your_secret_here", "Machine")
[System.Environment]::SetEnvironmentVariable("FIREBASE_CREDENTIALS_PATH", "C:\secrets\serviceAccount.json", "Machine")
[System.Environment]::SetEnvironmentVariable("FIREBASE_DATABASE_URL", "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app", "Machine")
[System.Environment]::SetEnvironmentVariable("ENABLE_HISTORY", "true", "Machine")
[System.Environment]::SetEnvironmentVariable("PORT", "5000", "Machine")
```

Alternatively, create `C:\Safe-Band\backend\.env` directly on the EC2 instance
(this file is gitignored and never committed).

### 3.7 Test the API manually

```powershell
# Start the server
cd C:\Safe-Band\backend
.\venv\Scripts\Activate.ps1
python run_server.py

# In another PowerShell window, test health:
Invoke-WebRequest http://localhost:5000/health

# From your LOCAL machine (replace with EC2 public IP):
$headers = @{ "X-Device-Token" = "your_secret"; "Content-Type" = "application/json" }
$body = '{"device_id":"band_001","lat":12.9716,"lng":77.5946}'
Invoke-RestMethod -Uri http://YOUR_EC2_IP:5000/api/location -Method Post -Headers $headers -Body $body
```

---

## Stage 4 - Windows Firewall

```powershell
# Allow API port through Windows Firewall (run on EC2 as Administrator)
New-NetFirewallRule -DisplayName "SafeWatch API" -Direction Inbound -Protocol TCP -LocalPort 5000 -Action Allow
```

---

## Stage 5 - Auto-start After Reboot

### Option A - Windows Task Scheduler (simple, no extra cost)

Create `C:\Safe-Band\backend\start_server.bat`:
```batch
@echo off
cd C:\Safe-Band\backend
call venv\Scripts\activate.bat
python run_server.py >> logs\startup.log 2>&1
```

Register the task (run as Administrator):
```powershell
$action = New-ScheduledTaskAction -Execute "C:\Safe-Band\backend\start_server.bat"
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -RunLevel Highest
Register-ScheduledTask -TaskName "SafeWatchAPI" -Action $action -Trigger $trigger -Principal $principal -Force
```

### Option B - NSSM (Non-Sucking Service Manager, better for production)

```powershell
# Download NSSM from https://nssm.cc/download
nssm install SafeWatchAPI "C:\Safe-Band\backend\venv\Scripts\python.exe" "C:\Safe-Band\backend\run_server.py"
nssm set SafeWatchAPI AppDirectory "C:\Safe-Band\backend"
nssm set SafeWatchAPI AppStdout "C:\Safe-Band\backend\logs\service.log"
nssm set SafeWatchAPI AppStderr "C:\Safe-Band\backend\logs\service_err.log"
nssm start SafeWatchAPI
```

---

## Stage 6 - HTTPS [BILLABLE - AWS ALB ~$16/month OR self-signed cert]

### Option A - AWS Application Load Balancer + ACM (recommended, ~$16/month)

1. AWS Certificate Manager > Request public certificate > enter your domain
2. Validate via DNS or email
3. EC2 > Load Balancers > Create ALB
4. Listener: HTTPS 443 > forward to Target Group > EC2 instance port 5000
5. After ALB is working, **remove port 5000 from Security Group inbound rules**

### Option B - Self-signed certificate (free, browser will warn)

```powershell
New-SelfSignedCertificate -DnsName "YOUR_EC2_IP" -CertStoreLocation "cert:\LocalMachine\My"
```

> For your A9G relay script with a self-signed cert, temporarily set
> `verify=False` in the requests.post() call in a9g_relay.py for testing only.

---

## Stage 7 - Update A9G Relay to Point at AWS

On your local PC, update `.env`:
```
API_URL=https://YOUR_EC2_IP_OR_DOMAIN/api/location
OFFLINE_MODE=false
```

Run the relay:
```powershell
python a9g_relay.py
```

You should see:
```
[GPS FIX]  lat=12.971600  lng=77.594600
[API POST success]  status=200
```

And Firebase RTDB should show:
```json
{
  "devices": {
    "band_001": {
      "location": {
        "lat": 12.9716,
        "lng": 77.5946,
        "timestamp": 1728497816,
        "updated_at": "2026-10-09T16:26:00+00:00"
      }
    }
  }
}
```

---

## Cost Summary

| Service | Free Tier | After Free Tier |
|---------|-----------|-----------------|
| EC2 t2.micro | Free 12 months | ~$8-10/month |
| Elastic IP (attached) | Free | Free |
| Elastic IP (unattached) | $3.65/month | $3.65/month |
| ALB (HTTPS) | None | ~$16/month |
| Firebase RTDB | Spark plan: 1 GB free | Blaze: pay as you go |
| Data transfer | 1 GB/month free | $0.09/GB |

**Minimum cost:**
- With ALB: ~$24-26/month
- Without ALB (HTTP only, not recommended): ~$8-10/month
- During Free Tier: $0-3.65/month

---

## Monitoring and Logs

```powershell
# View live log on EC2:
Get-Content C:\Safe-Band\backend\logs\safewatch_backend.log -Wait -Tail 50

# Rotate logs automatically: already configured in app.py (RotatingFileHandler, 5 MB, 3 backups)
```

---

## Troubleshooting

| Problem | Check |
|---------|-------|
| `Serial error: Access denied` | Another process has COM3 open. Close it. |
| `GPS NOT FIX NOW` after 10 minutes | Move A9G outdoors - clear sky view needed |
| `401 Unauthorised` from API | DEVICE_SECRET mismatch between .env and relay script |
| `502 Bad Gateway` from API | Firebase credentials path wrong or file missing |
| Android shows no data | Old `Device/location` node may have stale data - clear it in Firebase console |
| Firebase write fails with permission denied | Check RTDB rules allow Admin SDK |
| EC2 API not reachable | Check Security Group port 5000 AND Windows Firewall rule |
| SSL certificate error in relay | Using self-signed cert - add verify=False temporarily |

---

## Firebase RTDB Rules (for Android app access)

```json
{
  "rules": {
    "devices": {
      "$device_id": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

> The Python backend uses the Admin SDK which bypasses these rules.
> These rules control what the Android app can read/write.

---

## Tests: Automated vs. Hardware-Required

| Test | Automated | Requires |
|------|-----------|---------|
| API auth (401/200) | YES - pytest | Nothing |
| Coordinate validation | YES - pytest | Nothing |
| GPS parser (all response formats) | YES - pytest | Nothing |
| Error handlers (404/405) | YES - pytest | Nothing |
| Firebase write/read | NO | Real Firebase credentials |
| A9G GPS acquisition | NO | Physical A9G on COM3 |
| SMS sending | NO | Physical A9G + active SIM |
| AWS end-to-end POST | NO | Deployed EC2 instance |
| Android live location update | NO | Built app + Firebase data |
