Local Voice-Controlled Music Assistant (Android)

Overview
- Offline voice commands using Vosk
- Local music playback using Media3 ExoPlayer
- Simple UI built with Jetpack Compose
- Push-to-talk microphone, play/pause/next/previous, shuffle/repeat
- Optional voice feedback via TextToSpeech

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
- Relaunch the app. Tapping the microphone should now start listening offline.

Option B: Via ADB (if available)
- adb shell run-as com.example.localmusicassistant mkdir -p files
- adb push model files/model

Permissions
- RECORD_AUDIO to capture voice commands
- READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE (API ≤ 32) to read local songs
- INTERNET is included to optionally support model download helpers in the future (app remains offline for recognition)

Voice Commands
- "Play music" or "Play" — starts playing your library
- "Pause"
- "Resume" or "Continue"
- "Next song" or "Next"
- "Previous song" or "Back"
- "Shuffle on" / "Shuffle off"
- "Repeat on" / "Repeat off"
- "Play [artist|song|album]" — basic contains text match against metadata

Notes
- If you say "Play Daft Punk", the app will search title, artist, and album fields for "Daft Punk" and build a filtered playlist.
- Recognition runs entirely offline once the model is present.
- The microphone button toggles listening.
- The app uses Media3 ExoPlayer under the hood.

Build Details
- Language: Kotlin
- UI: Jetpack Compose + Material3
- Playback: androidx.media3:media3-exoplayer:1.4.1
- STT: com.alphacephei:vosk-android:0.3.70
- Min SDK: 26, Target SDK: 34

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

Planned/Bonus
- Wake-word detection ("Hey Music")
- Playlists and favorites
- Bluetooth speaker auto-detection

License
- This project uses Vosk (Apache 2.0) and Media3 (Apache 2.0). See their respective licenses for details.