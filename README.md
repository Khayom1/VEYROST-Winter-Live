# VEYROST Winter Live

Native Android live wallpaper with motion-reactive snowfall and an optional transparent home-screen snow overlay.

## Architecture

- Native Android `WallpaperService` rendered with EGL14 / OpenGL ES 2.0.
- Accelerometer-driven gravity response; touch does not control snow.
- Snow particles, a lightweight accumulation height field, and shake-triggered snow shedding.
- Optional `TYPE_APPLICATION_OVERLAY` layer. It is non-focusable and non-touchable so launcher icons remain tappable.
- Overlay visibility is gated by Usage Access and a recognized launcher foreground package.
- GitHub Actions builds and publishes a debug APK.

## Permissions and setup

1. Set the live wallpaper from the app.
2. To show snow over home-screen icons, grant **Display over other apps**.
3. Grant **Usage access** so the overlay can be hidden when another app is foreground.
4. Return to VEYROST and start Home Screen Snow.

The overlay currently uses a generic 5-column Samsung-style grid approximation. It does not inspect the screen or identify icon bounds, so launcher layouts with widgets, folders, custom grids, or changed icon spacing may need calibration. The snow overlay is a visual approximation of snow caps, not a true depth-aware simulation on each icon.

## Important limitations

This repository is an evolving prototype. The renderer still uses procedural OpenGL meshes; it is not yet the photorealistic Filament/glTF asset pipeline proposed for the final visual target. The current snowman is staged from accumulated snowfall and does not yet simulate three physically rolled snowballs. Device motion and overlay behavior require validation on the target phone. Do not treat a successful APK build as proof of visual quality or runtime stability.

A standard live wallpaper cannot draw above launcher icons. The optional overlay is a separate Android window and requires the user to grant special access. It may not appear above secure/system UI surfaces.

## Build

Push to `main` or run the Android APK workflow manually in GitHub Actions. It assembles a debug APK, verifies it is non-empty, uploads the artifact, and updates the `nightly` prerelease.

## Install using Termux

```sh
pkg install -y curl termux-tools
termux-setup-storage
curl -fsSL https://raw.githubusercontent.com/Khayom1/VEYROST-Winter-Live/main/install.sh -o install.sh
bash install.sh
```

The installer saves `VEYROST-Winter-Live.apk` into `Internal storage/Download` and opens Android's package installer.

## Device target

Minimum Android API 23, OpenGL ES 2.0. High refresh rates are not guaranteed; frame pacing, thermals, battery use, and GPU load must be measured on physical devices.
