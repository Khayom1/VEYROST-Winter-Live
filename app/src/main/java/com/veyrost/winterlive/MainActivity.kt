package com.veyrost.winterlive

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
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
            text = "A native winter world for your home screen.\n\nDrag across the lower part of the scene to roll snow. Release three snowballs to build a snowman."
            textSize = 16f
            setTextColor(0xFFD5E6F3.toInt())
            setPadding(0, 24, 0, 32)
        }
        val button = Button(this).apply {
            text = "SET LIVE WALLPAPER"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, WinterWallpaperService::class.java))
                }
                startActivity(intent)
            }
        }
        root.addView(title); root.addView(subtitle); root.addView(button)
        setContentView(root)
    }
}
