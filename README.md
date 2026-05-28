# ⚜️ ZenPaper

ZenPaper is a premium, battery-efficient native Android wallpaper changer app built using **Kotlin**, **Jetpack Compose**, and **WorkManager**. It features a modern gold-and-dark themed UI designed for minimalist aesthetic appeal.

---

## ✨ Features

* **Custom Interval Updates**: Set auto-updates from **1 minute up to 300 minutes (5 hours)**. Bypasses standard Android WorkManager 15-minute minimum limits natively using chained queues.
* **Aspect-Ratio Screen Fitting**: Automatically crops and scales downloaded wallpapers using `BitmapFactory` dimensions to perfectly center-fit your device's screen resolution (optimized for high-resolution screens like the Samsung Galaxy S25 Ultra).
* **Storage Integration**: Saves all downloaded and applied high-res wallpapers in public external storage under `Pictures/ZenPaper` (using modern `MediaStore` API for compatibility with Android 10+).
* **Dual Library Sources**:
  * **ZenPaper Curated**: Sleek minimalist, aesthetic original wallpapers fetched from high-resolution CDN storage (including dedicated spiritual and artistic selections).
  * **Wallhaven 4K HD API**: Integrates dynamic searching of ultra-high-definition wallpapers with custom tags and color tones.
* **Samsung Battery Optimizations Whitelist**: Direct runtime UI button hooks to easily prompt and bypass aggressive OEM Device Care limits.
* **Sync Logging**: Beautiful local sync card displaying live success/error statuses, wallpaper filename, and exact update timestamps.
* **Instant Force Update**: Immediate local coroutine execution for manual updating without background scheduler throttling.

---

## 🛠️ Tech Stack & Architecture

* **UI Layer**: Jetpack Compose, Material 3, Dark Mode/Gold Aesthetics.
* **Background Tasks**: AndroidX WorkManager using custom recursive `OneTimeWorkRequest` chains.
* **Image Processing**: Custom center-cropping and resolution-scaling matrix under `WallpaperController.kt`.
* **Min SDK**: API 26 (Android 8.0)
* **Target SDK**: API 34 (Android 14)

---

## 🚀 How to Build & Install

### Prerequisites
* **Android SDK**: API level 34 or higher.
* **JDK**: JDK 17.

### Compilation
To compile the debug APK under Windows PowerShell, navigate to the project directory and run the compilation override command:
```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
.\gradlew.bat assembleDebug
```

The successfully built APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Project Structure

```
zenpaper-app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/zenpaper/
│           ├── MainActivity.kt               # Entrypoint UI and permission setup
│           ├── ZenPaperApplication.kt        # Custom application start triggers
│           ├── WallpaperController.kt        # Image downloader, cropper, and storage manager
│           ├── ui/
│           │   └── HomeScreen.kt             # Jetpack Compose dark/gold UI layout
│           └── worker/
│               └── WallpaperUpdateWorker.kt  # WorkManager recursive timer engine
└── settings.gradle.kts
```

---

## 📜 License
Personal use / Custom portfolio wallpaper tool.
