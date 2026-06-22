# SafeWatch

SafeWatch is an Android application designed to help monitor device locations and manage safety alerts. It features real-time location tracking on Google Maps, customizable geo-fencing, and an SOS alert system integrated with Firebase Realtime Database.

## Features

### 📍 Real-time Tracking
- Displays the current location of a tracked device on Google Maps.
- Automatically refreshes the location every 5 seconds from the Firebase node `device/location`.

### ⭕ Geo-fencing
- **Set Center**: Long-press on the map to set the center point of a geo-fence.
- **Adjust Radius**: Drag the edge marker to visually resize the geo-fence circle.
- **Persistence**: Geo-fence data (latitude, longitude, and radius) is saved to Firebase under `device/geofence`.

### 🚨 SOS Alerts
- **Real-time Monitoring**: Listens to the `device/alerts` node for new SOS entries.
- **Full-Screen Alerts**: When an SOS alert is received, a high-priority, full-screen red notification displays the device's coordinates.
- **Acknowledgment**: Users can acknowledge alerts, which syncs the status back to Firebase.

### 🧭 Easy Navigation
- Bottom navigation with three main tabs: **Map**, **Alerts**, and **Settings**.

## Tech Stack
- **Language**: Kotlin
- **Architecture**: Jetpack Navigation Component, ViewBinding
- **Backend**: Firebase Realtime Database
- **Maps**: Google Maps SDK for Android
- **Concurrency**: Kotlin Coroutines

## Setup Instructions

### 1. Firebase Configuration
1. Create a new project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with the package name `com.example.safewatch`.
3. Download the `google-services.json` file and place it in the `app/` directory of this project.
4. Enable **Realtime Database** and set the rules to allow read/write access (for development).

### 2. Google Maps API Key
1. Obtain an API key from the [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **Maps SDK for Android**.
3. Open `app/src/main/AndroidManifest.xml` and replace `YOUR_API_KEY_HERE` with your actual API key:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```

### 3. Build the Project
1. Open the project in Android Studio.
2. Perform a **Gradle Sync**.
3. Build and run the app on an emulator or physical device.

## Data Structure (Firebase)
The app expects the following structure in your Realtime Database:
```json
{
  "device": {
    "location": {
      "latitude": 37.4220,
      "longitude": -122.0841
    },
    "geofence": {
      "latitude": 37.4220,
      "longitude": -122.0841,
      "radius": 200.0
    },
    "alerts": {
      "alert_id_001": {
        "type": "SOS",
        "status": "pending",
        "latitude": 37.4220,
        "longitude": -122.0841
      }
    }
  }
}
```

## Permissions
The app requires the following permissions:
- `INTERNET`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `SEND_SMS`
