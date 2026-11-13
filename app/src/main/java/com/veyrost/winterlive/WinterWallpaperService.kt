package com.veyrost.winterlive

import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.view.Surface
import kotlin.math.cos
import kotlin.math.sin
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.random.Random

class WinterWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WinterEngine()

    inner class WinterEngine : Engine() {
        private var renderer: WinterRenderer? = null
        private var thread: Thread? = null
        private var visible = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            renderer = WinterRenderer()
        }
        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (visible) startRender() else stopRender()
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.resize(width, height)
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stopRender()
            super.onSurfaceDestroyed(holder)
        }
        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP) {
                renderer?.touch(event.x, event.y, event.action == MotionEvent.ACTION_UP)
            }
            super.onTouchEvent(event)
        }
        override fun onDestroy() {
            stopRender()
            renderer?.release()
            renderer = null
            super.onDestroy()
        }
        private fun startRender() {
            if (thread?.isAlive == true) return
            thread = Thread {
                val r = renderer ?: return@Thread
                if (!r.open(surfaceHolder.surface)) return@Thread
                while (visible && !Thread.currentThread().isInterrupted && surfaceHolder.surface.isValid) {
                    val start = System.nanoTime()
                    r.draw()
                    val elapsed = (System.nanoTime() - start) / 1_000_000
                    try { Thread.sleep((33L - elapsed).coerceAtLeast(1L)) } catch (_: InterruptedException) { break }
                }
                r.close()
            }.apply { name = "VEYROST-Winter-Render"; start() }
        }
        private fun stopRender() {
            thread?.interrupt()
            try { thread?.join(400) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            thread = null
        }
    }
}

private class WinterRenderer {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var width = 1
    private var height = 1
    private var program = 0
    private var position = 0
    private var color = 0
    private var time = 0f
    private val flakes = Array(100) { Flake(Random.nextFloat(), Random.nextFloat(), 0.12f + Random.nextFloat() * 0.28f, 0.002f + Random.nextFloat() * 0.004f) }
    private var snowballs = 0
    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var snowRadius = 0.035f

    fun resize(w: Int, h: Int) { width = w.coerceAtLeast(1); height = h.coerceAtLeast(1) }
    fun touch(x: Float, y: Float, up: Boolean) {
        val nx = x / width
        val ny = 1f - y / height
        if (!dragging && !up) dragging = true
        if (dragging) { dragX = nx; dragY = ny; snowRadius = (snowRadius + 0.0015f).coerceAtMost(0.12f) }
        if (up && dragging) {
            if (dragY < 0.62f && snowballs < 3) snowballs++
            dragging = false
            snowRadius = 0.035f
        }
    }

    fun open(nativeSurface: Surface): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val versions = IntArray(2)
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return false
        val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE)
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1); val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0) return false
        val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
        surface = EGL14.eglCreateWindowSurface(display, configs[0], nativeSurface, intArrayOf(EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false
        program = createProgram(VS, FS)
        position = GLES20.glGetAttribLocation(program, "aPosition")
        color = GLES20.glGetUniformLocation(program, "uColor")
        return program != 0
    }
    fun draw() {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.035f, 0.09f, 0.16f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        time += 0.016f
        quad(-1f,-1f,1f,1f,0.035f,0.09f,0.16f)
        triangle(-1f,-0.25f,-0.38f,0.58f,0.15f,-0.25f,0.42f,0.58f,0.75f,0.24f,1f,-0.22f,0.54f,0.69f,0.79f)
        quad(-1f,-1f,1f,-0.25f,0.82f,0.91f,0.97f)
        // Distant fir trees
        for (i in 0..8) {
            val x = -0.95f + i * 0.24f
            triangle(x-0.09f,-0.28f,x,0.12f,x+0.09f,-0.28f,0.08f,0.24f,0.34f)
            triangle(x-0.07f,-0.12f,x,0.29f,x+0.07f,-0.12f,0.12f,0.38f,0.49f)
        }
        // Snowman appears only after three completed rolls
        if (snowballs >= 1) circle(0.0f,-0.47f,0.16f,0.93f,0.97f,1f)
        if (snowballs >= 2) circle(0.0f,-0.18f,0.12f,0.97f,0.99f,1f)
        if (snowballs >= 3) {
            circle(0.0f,0.06f,0.085f,0.99f,1f,1f)
            circle(-0.03f,0.09f,0.009f,0.04f,0.08f,0.12f); circle(0.03f,0.09f,0.009f,0.04f,0.08f,0.12f)
            triangle(0.0f,0.055f,0.0f,0.02f,0.09f,0.04f,1f,0.48f,0.12f)
        }
        for (f in flakes) {
            f.y -= f.speed
            f.x += sin((time * 1.7f + f.y * 8f).toDouble()).toFloat() * 0.0008f
            if (f.y < -1f) { f.y = 1f; f.x = Random.nextFloat() * 2f - 1f }
            circle(f.x, f.y, f.size, 0.92f,0.97f,1f)
        }
        if (dragging) circle(dragX * 2f - 1f, dragY * 2f - 1f, snowRadius, 0.98f,1f,1f)
        EGL14.eglSwapBuffers(display, surface)
    }
    private fun quad(l:Float,b:Float,r:Float,t:Float,cr:Float,cg:Float,cb:Float) {
        drawVertices(floatArrayOf(l,b,r,b,l,t,r,t),cr,cg,cb,GLES20.GL_TRIANGLE_STRIP)
    }
    private fun triangle(x1:Float,y1:Float,x2:Float,y2:Float,x3:Float,y3:Float,r:Float,g:Float,b:Float) {
        drawVertices(floatArrayOf(x1,y1,x2,y2,x3,y3),r,g,b,GLES20.GL_TRIANGLES)
    }
    private fun circle(cx:Float,cy:Float,rad:Float,r:Float,g:Float,b:Float) {
        val a = FloatArray(42); a[0]=cx; a[1]=cy
        for (i in 0..19) { val ang = i * Math.PI / 10.0; a[(i+1)*2]=cx+cos(ang).toFloat()*rad; a[(i+1)*2+1]=cy+sin(ang).toFloat()*rad }
        drawVertices(a,r,g,b,GLES20.GL_TRIANGLE_FAN)
    }
    private fun drawVertices(data:FloatArray,r:Float,g:Float,b:Float,mode:Int) {
        val fb = ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data).position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,fb)
        GLES20.glUniform4f(color,r,g,b,1f)
        GLES20.glDrawArrays(mode,0,data.size/2)
    }
    fun close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context)
            EGL14.eglTerminate(display)
        }
        display=EGL14.EGL_NO_DISPLAY; context=EGL14.EGL_NO_CONTEXT; surface=EGL14.EGL_NO_SURFACE
    }
    fun release() { close() }
    private fun createProgram(v:String,f:String):Int {
        val vs=shader(GLES20.GL_VERTEX_SHADER,v); val fs=shader(GLES20.GL_FRAGMENT_SHADER,f)
        return GLES20.glCreateProgram().also { GLES20.glAttachShader(it,vs); GLES20.glAttachShader(it,fs); GLES20.glLinkProgram(it) }
    }
    private fun shader(type:Int,source:String):Int = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it,source); GLES20.glCompileShader(it) }
    private data class Flake(var x:Float,var y:Float,val speed:Float,val size:Float)
    companion object {
        private const val VS = "attribute vec2 aPosition; void main(){ gl_Position=vec4(aPosition,0.0,1.0); gl_PointSize=2.0; }"
        private const val FS = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }"
    }
}
