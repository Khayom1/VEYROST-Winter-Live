# VEYROST Winter Live

A native Android live wallpaper experiment built with Kotlin, Android `WallpaperService`, EGL14 and OpenGL ES 2.0. This is an Android APK, not a browser page.

## Current implementation

- Native Android wallpaper service and launcher activity.
- Perspective camera and depth-buffered 3D rendering.
- Procedural sphere, cone, cylinder, ground and mountain meshes.
- Procedural conifer forest with layered foliage and snow caps.
- Animated 3D snowfall with wind drift.
- Three-touch-gesture snowman assembly prototype.
- GitHub Actions APK build and Termux download installer.

## Known limitations

This is an early 3D prototype, not a finished photorealistic product. The current snowman interaction counts a sufficiently long drag as one rolled snowball; advanced snow deformation, physically simulated rolling, high-quality shadows, audio, weather, and graphics settings remain future work.

A standard Android live wallpaper is rendered behind launcher icons and widgets. Drawing snow on top of actual launcher icons requires a separate overlay implementation with Android special permission or a custom launcher; it is not part of the wallpaper service itself.

## Build

Push to `main` or run the workflow manually from GitHub Actions. The workflow builds a debug APK and uploads it as an artifact. The nightly release is updated after a successful build.

## Install from Termux

After a successful workflow run:

```sh
pkg install -y curl termux-tools
curl -fsSL https://raw.githubusercontent.com/Khayom1/VEYROST-Winter-Live/main/install.sh -o install.sh
bash install.sh
```

Android will ask you to confirm APK installation. Open the app and choose **Set Live Wallpaper** to apply it.

## Device target

Designed to support Android 6.0+ (API 23+) and OpenGL ES 2.0 devices. Performance and visual quality must be validated on physical hardware.
