# VEYROST Winter Live

Native Android live wallpaper built with Kotlin and OpenGL ES 2.0. This project is designed to be built by GitHub Actions and installed on Android devices.

> Status: initial implementation in progress. The first build targets a native `WallpaperService`; it is not a browser wallpaper.

## Features planned
- Procedural winter scene and animated snowfall
- Touch-driven snowball interaction
- Three-stage snowman assembly
- Adaptive rendering and lifecycle-aware engine

## Build
Push to `main` to trigger the Android CI workflow. Download the APK from the workflow artifacts or the `nightly` release when published.

## Install
Use the Termux installer after the first successful CI build:

```sh
curl -fsSL https://raw.githubusercontent.com/Khayom1/VEYROST-Winter-Live/main/install.sh -o install.sh
bash install.sh
```

The Android system will still require the user to confirm installation and select the live wallpaper.
