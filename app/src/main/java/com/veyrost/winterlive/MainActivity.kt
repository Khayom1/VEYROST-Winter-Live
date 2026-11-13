package com.veyrost.winterlive

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(0xFF10243A.toInt())
        }
        val title = TextView(this).apply {
            text = "VEYROST\nWINTER LIVE"
            textSize = 30f
            setTextColor(0xFFF4FAFF.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "A motion-reactive winter world.\n\nSnow responds to device tilt and shaking. Touch does not control the snow. Enable the optional Home Screen Snow layer to display snow above launcher icons."
            textSize = 16f
            setTextColor(0xFFD5E6F3.toInt())
            setPadding(0, 24, 0, 32)
        }
        val wallpaperButton = Button(this).apply {
            text = "SET LIVE WALLPAPER"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, WinterWallpaperService::class.java))
                }
                startActivity(intent)
            }
        }
        val overlayPermissionButton = Button(this).apply {
            text = "ALLOW HOME SCREEN SNOW"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }
        val startOverlayButton = Button(this).apply {
            text = "START HOME SCREEN SNOW"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, HomeSnowOverlayService::class.java))
                } else {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(wallpaperButton)
        root.addView(overlayPermissionButton)
        root.addView(startOverlayButton)
        setContentView(root)
    }
}
