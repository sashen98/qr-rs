# Price Scanner - Android App

A modern, fast Android barcode and QR code scanner designed for supermarket bills, receipts, retail products, and soap boxes, built with Jetpack Compose, CameraX, and Google ML Kit.

## Features

- **Instant Barcode & QR Code Scanning**: Powered by CameraX and Google ML Kit Barcode Scanning. Supports EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, QR Code, Data Matrix, and Aztec.
- **Prominent Price Display**: Highlights the price right at the very top of the scan result screen in bold, glowing typography.
- **Bill & Receipt Parser**: Automatically extracts total amounts, merchant names, invoice numbers, and dates from supermarket bills, receipts, and LankaQR / EMVCo payloads.
- **Built-in Product Directory**: Pre-configured recognition for common retail items and soap boxes (Lifebuoy, Sunlight, Dettol, Lux, Anchor, Munchee, Maliban, etc.) with standard retail prices.
- **Audio & Haptic Feedback**: Plays a crisp "tick" sound tone and triggers a vibration pulse on every successful scan.
- **Quick Delete**: 1-tap delete from the result sheet and the scan history list.
- **Scan History & Expenditure Tracker**: Tracks scanned bills and calculates total expenditure with search and filter support.
- **Flashlight & Camera Flip**: Built-in torch toggle and front/rear camera switching.
- **Gallery Image Scanner & Manual Code Input**: Scan barcodes from existing photos or type code manually.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM with Kotlin Coroutines & StateFlow
- **Camera & Barcode Detection**: CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) + Google ML Kit Barcode Scanning
- **Local Persistence**: Room Database (`androidx.room`) with KSP
- **Target SDK**: Android 16 (API 36), Min SDK: 24 (Android 7.0+)

## How to Build

1. Clone or download this repository.
2. Open the project in **Android Studio** (Ladybug or newer recommended).
3. Let Gradle sync and resolve dependencies.
4. Run on an Android device or emulator with camera enabled:
   ```bash
   gradle :app:assembleDebug
   ```
5. The generated APK will be in `app/build/outputs/apk/debug/app-debug.apk`.
