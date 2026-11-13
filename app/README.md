# Pocket Player: the Android app

The watch-side software of the [pocket music player](../README.md): a small Wear OS app written in Java. Package `org.op2r.pocketplayer`, version 0.7.

For the watch setup around it (wireless ADB, removing apps, settings, restrict mode) see the [software guide](../docs/watch-software.md). This file covers the app itself.

## What it contains

| Entry | What it does |
| --- | --- |
| **Pocket Player** (`MainActivity`) | Spotify controls over the dimmed album cover: previous, play/pause, next, volume, song time, and a visualizer along the screen edge. The cover and text slide in the direction you skipped |
| **Pocket Face** (`PlayerFaceService`) | The same screen as a watch face. Faces only receive plain taps, so the buttons are tap zones. It drops the buttons when the watch is locked |
| **Lock Watch** (`LockActivity`) | No screen. Turns the display off and locks the watch at once. Meant for a button shortcut |
| **Menu** (`ToolsActivity`) | A see-through layer opened from the player: sensor switch, lock now, visualizer style, colour, open Spotify, Bluetooth devices |

The visualizer has four styles: layered waves (default), a full ring, a lower arc and a small equalizer. They move at random while music plays and do not follow the audio. The colour is white or taken from the cover.

Spotify's media session on Wear OS carries only title, artist, album and duration, with no picture. Covers are looked up by artist and album on the Deezer and iTunes search APIs (no key needed), stored on the watch as small WebP files and limited to 10 MB and 90 days.

## Source layout

Everything is in `app/src/main/java/org/op2r/pocketplayer/`, counted from this folder (`app/app/src/...` from the repository root).

| File | Role |
| --- | --- |
| `MainActivity` | The player screen, media session handling, cover and text transitions |
| `PlayerFaceService` | The watch face (AndroidX watch face library, drawn on a canvas) |
| `ToolsActivity` | The menu |
| `LockActivity`, `AdminReceiver` | Instant lock through the device-admin force-lock policy |
| `MediaListener` | An empty notification listener: being one is what allows reading Spotify's session |
| `SensorRestrict` | Switches the sensor service's restrict mode and reads the frozen wrist state |
| `SpectrumView`, `SpectrumModel` | The edge visualizer and its pure-Java maths |
| `IconView`, `RoundFade` | Button icons with shadow, and the fade of text toward the round edge |
| `Accent`, `ArtFetcher`, `PlayerPrefs`, `TimeFormat` | Cover colour, cover lookup and cache, settings, song time |

Resources: `res/font/` holds the Figtree font (SIL Open Font License 1.1, text in `res/raw/figtree_ofl.txt`) and `res/drawable/` the app icons.

## Install

Build it (below), then, with wireless debugging on, from the repository folder:

```
adb -s <WATCH> install -t -r app/app/build/outputs/apk/debug/app-debug.apk

# read Spotify's media session (the app is a notification listener for this)
adb -s <WATCH> shell cmd notification allow_listener org.op2r.pocketplayer/org.op2r.pocketplayer.MediaListener

# Lock Watch uses the device-admin lock (force-lock policy only)
adb -s <WATCH> shell dpm set-active-admin --user 0 org.op2r.pocketplayer/.AdminReceiver

# the menu's Sensors switch needs this; the grant survives reboots
adb -s <WATCH> shell pm grant org.op2r.pocketplayer android.permission.DUMP
```

The app is marked test-only (`-t` above), so that ADB can remove the admin rights again, which Android requires before an uninstall:

```
adb -s <WATCH> shell dpm remove-active-admin --user 0 org.op2r.pocketplayer/.AdminReceiver
adb -s <WATCH> uninstall org.op2r.pocketplayer
```

To use the watch face, pick **Pocket Face** in the watch's face list (software guide, section 5.6).

## Build

Needs JDK 17 and the Android SDK (platform 34, build tools 34.0.0). Command-line tools are enough, no Android Studio. Create `app/local.properties` with `sdk.dir=<path to the SDK>`, then:

```
cd app
./gradlew testDebugUnitTest assembleDebug     # Windows: gradlew.bat
```

The APK is `app/app/build/outputs/apk/debug/app-debug.apk`. The pure logic (song time, cover colour, visualizer maths, settings) has JVM unit tests in `app/app/src/test/`. Check a change on the real watch with a screenshot: `adb -s <WATCH> exec-out screencap -p > shot.png`.

Libraries: AndroidX Palette (cover colours), the AndroidX watch face libraries (Pocket Face) and JUnit for tests.

## Licence

[GNU General Public License, version 2](../LICENSE). The Figtree font keeps its own licence (SIL Open Font License 1.1).
