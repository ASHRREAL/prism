# Prism

An Android music player for Navidrome / Subsonic, built with Jetpack Compose designed to look like windows phone UI.

<p align="center">
  <img src="screenshots/04_home.jpg" width="22%" alt="Home">
  <img src="screenshots/01_now_playing.jpg" width="22%" alt="Now Playing">
  <img src="screenshots/02_queue.jpg" width="22%" alt="Queue">
  <img src="screenshots/03_lyrics.jpg" width="22%" alt="Lyrics">
</p>


## Stack

- Kotlin + Jetpack Compose (BOM 2025.02)
- Media3 / ExoPlayer for playback
- Coil for image loading
- Retrofit + Subsonic API for library browsing and streaming
- `SharedPreferences`-backed `StateFlow` for settings

## Setup

1. Clone the repo and open in Android Studio.
2. Build and install on a device or emulator running Android 8.0+.
3. On first launch, enter your Navidrome server URL, username, and password.

## License

[MIT](LICENSE.md)
