# Immich TV — Photo & Video Viewer for Amazon Fire Stick 4K

A native Android TV app that connects to your self-hosted [Immich](https://immich.app) server and lets you browse your photo library on the big screen using the Fire Stick remote.

## Features

- **Gallery Browser** — Navigate albums, people, and random photos with D-pad
- **On This Day / Memories** — Relive photos from past years
- **People** — Browse photos organized by detected faces
- **Albums** — View all your Immich albums
- **Video Playback** — Play videos with ExoPlayer (Media3)
- **Full-screen Photo Viewer** — Navigate through photos with ← → arrows
- **D-pad Optimized** — Every UI element is navigable with the Fire Stick remote

## Requirements

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**
- **Immich Server** v1.90+ (API v1)
- **Fire Stick 4K** (or any Fire TV / Android TV device)
- **ADB** for sideloading

## Build Instructions

### 1. Clone & Open in Android Studio

```bash
# Copy the project to your workspace
cp -r immich-tv ~/AndroidStudioProjects/

# Open in Android Studio
# File → Open → select the immich-tv folder
```

### 2. Build the APK

**Option A: Android Studio**
- Build → Build Bundle(s) / APK(s) → Build APK(s)
- APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

**Option B: Command Line**
```bash
cd immich-tv
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

**For release (smaller, optimized):**
```bash
./gradlew assembleRelease
```

### 3. Get Your Immich API Key

1. Open Immich web UI in your browser
2. Go to **Account Settings** (click your avatar → Account Settings)
3. Scroll to **API Keys**
4. Click **New API Key** → give it a name like "Fire TV"
5. Copy the key — you'll enter it in the app

## Sideload to Fire Stick 4K

### Enable Developer Options on Fire Stick

1. **Settings** → **My Fire TV** → **About**
2. Click **Fire TV Stick 4K** (the device name) **7 times**
3. Go back to **My Fire TV** → **Developer Options**
4. Enable **ADB debugging**
5. Enable **Apps from Unknown Sources**

### Find Fire Stick IP Address

Settings → My Fire TV → About → Network → note the **IP address**

### Install via ADB

```bash
# Connect to Fire Stick (replace with your IP)
adb connect 192.168.1.XXX:5555

# Accept the prompt on your TV screen

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or for release:
adb install app/build/outputs/apk/release/app-release.apk
```

### Alternative: Install via USB Drive

1. Copy the APK to a USB drive
2. Plug into Fire Stick (needs USB-C adapter for 4K Stick)
3. Use a file manager app (e.g., "File Commander") to install

## Usage

### First Launch

1. Find **Immich TV** in your apps (Settings → Applications → Manage Installed Applications if not on home screen)
2. The Settings screen opens automatically on first run
3. Enter your **Immich server URL** (e.g., `http://192.168.1.100:2283`)
4. Enter your **API key**
5. Click **Test Connection** to verify
6. Click **Save & Go Back**

### Navigation (Fire Stick Remote)

| Button | Action |
|--------|--------|
| **D-pad ↑↓←→** | Navigate cards and menus |
| **Select (center)** | Open album / photo / video |
| **Back** | Go back to previous screen |
| **←→ in Photo Viewer** | Previous / Next photo |
| **Select in Photo Viewer** | Toggle info overlay |
| **Play/Pause in Video** | Play / Pause video |
| **←→ in Video** | Seek backward / forward |

### Home Screen Rows

- **💭 On This Day** — Memories from past years on today's date
- **📷 Albums** — All your Immich albums
- **👤 People** — Face-recognized people
- **🎲 Random Photos** — Random selection from your library
- **⚙️ Settings** — Reconfigure server connection

## Troubleshooting

### "Cannot reach server"
- Make sure Fire Stick and Immich server are on the same network
- Check that the URL includes the port (default `:2283`)
- Try `http://` not `https://` for local servers

### "Invalid API key"
- Regenerate the key in Immich → Account Settings → API Keys
- Make sure no extra spaces when entering the key

### App not showing on home screen
- Go to Settings → Applications → Manage Installed Applications
- Find "Immich TV" and launch from there
- Or install a sideload launcher like "Sideload Launcher" from Amazon Appstore

### Photos load slowly
- The app loads preview-size thumbnails first, then full resolution
- Ensure your network connection between Fire Stick and server is good
- Consider using 5GHz WiFi for better throughput

### Video won't play
- Immich transcodes videos on-the-fly; make sure your server has enough CPU
- Some very large 4K videos may need a moment to buffer

## Project Structure

```
immich-tv/
├── app/src/main/
│   ├── AndroidManifest.xml          # TV app manifest
│   ├── java/com/immichtv/
│   │   ├── ImmichTVApp.kt           # Application class
│   │   ├── api/
│   │   │   ├── Models.kt            # Data models (Album, Asset, Person, etc.)
│   │   │   ├── ImmichApi.kt         # Retrofit API interface
│   │   │   └── ImmichClient.kt      # API client singleton
│   │   ├── ui/
│   │   │   ├── MainActivity.kt      # Entry point
│   │   │   ├── HomeFragment.kt      # Leanback BrowseSupportFragment
│   │   │   ├── BrowseGridActivity.kt # Grid view for albums/people
│   │   │   ├── PhotoViewerActivity.kt # Full-screen photo viewer
│   │   │   ├── VideoPlayerActivity.kt # ExoPlayer video playback
│   │   │   ├── SettingsActivity.kt   # Server config
│   │   │   └── presenters/
│   │   │       └── CardPresenter.kt  # Leanback card rendering
│   │   └── util/
│   │       ├── PrefsManager.kt       # SharedPreferences helper
│   │       └── ImmichGlideModule.kt  # Glide auth headers
│   └── res/
│       ├── layout/                   # XML layouts
│       ├── values/                   # Strings, colors, themes
│       ├── drawable/                 # App banner, icons
│       └── xml/                      # Network security config
├── build.gradle.kts                  # Project build config
├── settings.gradle.kts
└── gradle/                           # Gradle wrapper
```

## Tech Stack

- **Kotlin** — Primary language
- **Leanback** — Android TV UI framework (BrowseSupportFragment, VerticalGridSupportFragment)
- **Retrofit 2** — HTTP client for Immich API
- **OkHttp** — Networking with auth interceptors
- **Glide** — Image loading with API key headers
- **Media3 / ExoPlayer** — Video playback with auth headers
- **Coroutines** — Async operations

## License

MIT — Free to use, modify, and distribute.
