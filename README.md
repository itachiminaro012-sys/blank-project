Local Voice-Controlled Music Assistant (Android)

Overview
- Offline voice commands using Vosk (with wake word: “Hey Music”)
- Local music playback using Media3 ExoPlayer
- Simple UI built with Jetpack Compose
- Push-to-talk microphone with smooth press animation
- Play/pause/next/previous, shuffle/repeat
- Voice feedback via TextToSpeech (female voice preferred when available)
- First-run model installation helper dialog

Requirements
- Android Studio Hedgehog or newer
- Android device with Android 8.0 (API 26) or higher
- Local music files on the device (e.g., /Music)
- Vosk offline speech model placed on device (see below)

Quick Start
1) Open the project in Android Studio
2) Sync Gradle
3) Build > Build Bundle(s) / APK(s) > Build APK(s)
4) Install the APK on your device
5) On first run, grant:
   - Microphone (RECORD_AUDIO)
   - Music library access:
     - Android 13+ (API 33+): READ_MEDIA_AUDIO
     - Android 12- (API ≤ 32): READ_EXTERNAL_STORAGE

Add Local Music
- Copy MP3/FLAC (or other supported formats) to your device:
  - Recommended: /Music folder
- The app indexes your device’s audio via MediaStore and builds a playlist.

Install the Vosk Offline Speech Model
The app expects a Vosk model directory at:
Android/data/<your.package>/files/model

Option A (recommended): Download on your desktop and push to device:
- Download a small English model (approx. 50–60 MB), for example:
  https://alphacephei.com/vosk/models
  Look for something like vosk-model-small-en-us-0.15
- Unzip it locally and rename the folder to model
  It should contain subfolders like conf, am, etc.
- Push the folder to the app’s files directory:
  - After installing and launching the app once, find its files path on device:
    /sdcard/Android/data/com.example.localmusicassistant/files
  - Copy the model folder there so you end up with:
    /sdcard/Android/data/com.example.localmusicassistant/files/model
- Return to the app. The first-run dialog includes a button to open the model page.

Option B: Via ADB (if available)
- adb shell run-as com.example.localmusicassistant mkdir -p files
- adb push model files/model

Permissions
- RECORD_AUDIO to capture voice commands
- READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE (API ≤ 32) to read local songs
- INTERNET is included only to open the model download page in your browser

Voice Commands
- Wake word: “Hey Music” (activates active listening mode)
- "Play music" or "Play" — starts playing your library
- "Pause"
- "Resume" or "Continue"
- "Next song" or "Next"
- "Previous song" or "Back"
- "Shuffle on" / "Shuffle off"
- "Repeat on" / "Repeat off"
- "Play [artist|song|album]" — basic contains text match against metadata

Notes
- After “Hey Music”, the app listens for ~10 seconds for a command, then returns to wake-word mode.
- If you say "Play Daft Punk", the app will search title, artist, and album fields for "Daft Punk" and build a filtered playlist.
- Recognition runs entirely offline once the model is present.

Build Details
- Language: Kotlin
- UI: Jetpack Compose + Material3
- Playback: androidx.media3:media3-exoplayer:1.4.1
- STT: com.alphacephei:vosk-android:0.3.70
- Min SDK: 26, Target SDK: 34

Continuous Integration (GitHub Actions)
A workflow is included at .github/workflows/android.yml that builds a debug APK and uploads it as an artifact.
- On GitHub, after you push the repo:
  - Actions tab > Android CI > latest run > Artifacts > app-debug-apk
- The APK file name: app-debug.apk

How to publish this to GitHub and get the APK link
1) Create a new GitHub repository (public or private).
2) Initialize a local git repo in this project folder if needed:
   git init
   git add .
   git commit -m "Initial commit: Local Music Assistant"
3) Add the GitHub remote and push:
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git branch -M main
   git push -u origin main
4) Go to https://github.com/<your-username>/<your-repo>/actions
   - Open the latest workflow run
   - Download the app-debug-apk artifact (contains the APK)
5) If you want a permanent release link, create a GitHub Release and attach the APK.

Troubleshooting
- Microphone button says "Speech model not found":
  Ensure the model directory exists at:
  /sdcard/Android/data/com.example.localmusicassistant/files/model
- No music plays:
  - Confirm permissions are granted.
  - Ensure your device has audio files indexed by MediaStore (try opening the default Music app).
- App cannot see FLAC files:
  - Confirm your device’s software decoders support the format.
- Crash on startup after model copy:
  - Ensure you copied the correct Vosk model (folder with conf, am, etc. inside).